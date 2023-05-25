package example

import net.rationalstargazer.events.RStaEventDispatcher
import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.RStaCoroutineDispatcherFactory
import net.rationalstargazer.events.lifecycle.RStaCoroutineLifecycle
import net.rationalstargazer.events.value.RStaListenerInvoke
import net.rationalstargazer.logic.RStaBaseMessageQueueHandlerImpl
import net.rationalstargazer.remote.sync.RemoteQueueHandler
import net.rationalstargazer.remote.sync.RemoteSyncId
import net.rationalstargazer.remote.sync.RemoteSyncIdValue
import net.rationalstargazer.remote.sync.RemoteSyncTarget
import net.rationalstargazer.remote.sync.Repository
import net.rationalstargazer.remote.sync.WritableRemoteComplexDataSource
import net.rationalstargazer.remote.sync.WritableRemoteComplexDataSourceImpl

/**
 * The [WritableRemoteComplexDataSource] where value for each key is completely independent from values for other keys.
 * It is a requirement, the implementation depend on it.
 */
interface WritableSimpleRemoteDataSource<StateData, Key, Value : Any> :
    WritableRemoteComplexDataSource<StateData, Key, Value, Value> {
    
    sealed class HandlerResult<out Value> {
        data class Success<out Value>(val nextValue: Value) : HandlerResult<Value>()
        object Fail : HandlerResult<Nothing>()
    }
    
    /**
     * An event source of all known changes.
     * [sync], [write], [cancelLocallyAhead] calls emit events.
     * After `sync`, or `write` commands will be handled (some time later) event with the correspondent key will be emitted again.
     */
    val changeSource: RStaEventSource<Key>
    
    /**
     * Cancels locally overwritten value, the local value will be equal to the remote one.
     * @param afterCancelledDataMapper Should be pure function.
     * Will be called if cancellation is possible to get the state's data correspondent to the state's data after successful cancellation.
     * If cancellation is impossible
     * (the value for the specified key is not in "locally ahead" state or already in the middle of the sending to the server)
     * no changes to the state will be made and afterCancelledDataMapper will not be called.
     *
     * Important note: current implementation allows the possibility that the value will still be in "locally ahead" state,
     * if cancelLocallyAhead is called at the time when the value is sending to the server and the send will be unsuccessful.
     */
    fun cancelLocallyAhead(key: Key, afterCancelledDataMapper: (beforeCancelData: StateData) -> StateData)
}

/**
 * Creates [WritableSimpleRemoteDataSource] (see for details).
 * @param dataLayerCoroutineLifecycle see [WritableRemoteComplexDataSourceImpl.coroutineDispatcher]
 * @param elapsedRealTimeSource the source of current elapsed real time
 * @param localRepository will be used to read and write "local remote" values
 * (see [net.rationalstargazer.remote.sync.BaseRemoteComplexDataSource.LocalRemote]).
 * @param localCommandsRepository at first it will be used a single time to read "locally ahead" values
 * (see [net.rationalstargazer.remote.sync.BaseRemoteComplexDataSource.LocalRemote]),
 * after that it will be used to write "locally ahead" values soon (but not immediately)
 * after the [WritableSimpleRemoteDataSource.write] is called.
 * It is guaranteed that all `write` calls will be saved soon (except for special cases like exceptions or program's termination).
 *
 * @param initialData first value of state's data
 * @param alreadySyncedHandler callback which is called during the handling of [RemoteQueueHandler.SyncCommand.Sync]
 * if it was determined that the value meets specified requirements and is considered to be actual (already synced).
 *
 * @param remoteHandler callback which is called during the handling of [RemoteQueueHandler.SyncCommand]
 * to perform the networking and return the result (pair of values: the result, and next state).
 */
fun <StateData, Key, Value : Any> WritableSimpleRemoteDataSource(
    dataLayerCoroutineLifecycle: RStaCoroutineLifecycle,
    elapsedRealTimeSource: () -> Long,
    localRepository: Repository.Writable<Key, Value>,
    localCommandsRepository: Repository.Writable<Unit, List<Pair< Key, Value>>>,
    initialData: StateData,
    alreadySyncedHandler: suspend (
        state: StateData,
        command: RemoteSyncIdValue<RemoteQueueHandler.SyncCommand<Key, Value>>,
    ) -> StateData,
    remoteHandler: suspend (
        state: StateData,
        command: RemoteSyncIdValue<RemoteQueueHandler.SyncCommand<Key, Value>>,
    ) -> Pair<WritableSimpleRemoteDataSource.HandlerResult<Value>, StateData>
): WritableSimpleRemoteDataSource<StateData, Key, Value> {
    val lifecycle = dataLayerCoroutineLifecycle.lifecycle
    
    val changedSource = RStaEventDispatcher<Key>(lifecycle)
    
    val r = WritableRemoteComplexDataSourceImpl<StateData, Key, Value, Value>(
        lifecycle,
        RStaCoroutineDispatcherFactory.create(dataLayerCoroutineLifecycle),
        localRepository,
        initialData,
        null,
        {
            localCommandsRepository.read(Unit)
                ?.map {
                    RemoteQueueHandler.SyncCommand.Send(it.first, it.second)
                }
                ?: listOf()
        },
        
        { state, command ->
            when (command) {
                is RemoteQueueHandler.QueueCommand.Remote<Key, Value> -> {
                    state.copy(queue = state.queue + command.remote)
                }
                
                is RemoteQueueHandler.QueueCommand.StateChange -> {
                    command.reducer(state)
                }
            }
        },
        
        { key, initialValue, state ->
            state.queue
                .map { it.value }
                .filterIsInstance<RemoteQueueHandler.SyncCommand.Send<Key, Value>>()
                .findLast { it.key == key }
                ?.command
                ?: initialValue
        },
        
        { state, local, write ->
            val queueItem = state.queue.firstOrNull()
            
            if (queueItem != null) {
                val command = queueItem.value
                
                val send: RemoteQueueHandler.SyncCommand.Send<Key, Value>?
                val sync: RemoteQueueHandler.SyncCommand.Sync<Key>?
                val alreadySynced: Boolean
                
                when (command) {
                    is RemoteQueueHandler.SyncCommand.Send -> {
                        send = command
                        sync = null
                        alreadySynced = false
                    }
                    
                    is RemoteQueueHandler.SyncCommand.Sync -> {
                        send = state.failedSends.firstOrNull { it.key == command.key }
                        
                        if (send != null) {
                            sync = null
                            alreadySynced = false
                        } else {
                            sync = command
                            
                            alreadySynced = when (command.target) {
                                RemoteSyncTarget.ExistsLocally -> {
                                    local.read(command.key) != null
                                }
    
                                RemoteSyncTarget.Once -> {
                                    state.lastSuccessfulElapsedRealTimes[command.key] != null
                                }
    
                                is RemoteSyncTarget.InLast -> {
                                    state.lastSuccessfulElapsedRealTimes[command.key]
                                        ?.let { t ->
                                            t + command.target.millisecs > elapsedRealTimeSource()
                                        }
                                        ?: false
                                }
                            }
                        }
                    }
                }
                
                if (send != null) {
                    val time = elapsedRealTimeSource()
                    val (result, nextState) = remoteHandler(state.data, RemoteSyncIdValue(queueItem.id, send))
                    
                    val nextQueue = state.queue.drop(1)
                    val nextFailedAttempts = state.failedAttempts - send.key
                    val nextFailedSends = state.failedSends.filter { it.key != send.key }
                    
                    when (result) {
                        is WritableSimpleRemoteDataSource.HandlerResult.Success -> {
                            write { writer ->
                                writer.write(send.key, result.nextValue)
        
                                RemoteQueueHandler.State(
                                    nextState,
                                    nextQueue,
                                    state.lastSuccessfulElapsedRealTimes + (send.key to time),
                                    nextFailedAttempts,
                                    nextFailedSends
                                )
                            }
                        }
    
                        is WritableSimpleRemoteDataSource.HandlerResult.Fail -> {
                            write {
                                RemoteQueueHandler.State(
                                    nextState,
                                    nextQueue,
                                    state.lastSuccessfulElapsedRealTimes,
                                    nextFailedAttempts + (send.key to RemoteQueueHandler.State.TimeData.Precise(time)),
                                    nextFailedSends + send
                                )
                            }
                        }
                    }
    
                    changedSource.enqueueEvent(command.key)
                }
                
                if (sync != null) {
                    if (alreadySynced) {
                        val nextState = alreadySyncedHandler(state.data, RemoteSyncIdValue(queueItem.id, sync))
                        
                        write {
                            RemoteQueueHandler.State(
                                nextState,
                                state.queue.drop(1),
                                state.lastSuccessfulElapsedRealTimes,
                                state.failedAttempts,
                                state.failedSends
                            )
                        }
                    } else {
                        val time = elapsedRealTimeSource()
                        val nextFailedAttempts = state.failedAttempts - command.key
                        val (result, nextState) = remoteHandler(state.data, RemoteSyncIdValue(queueItem.id, sync))
    
                        when (result) {
                            is WritableSimpleRemoteDataSource.HandlerResult.Success -> {
                                write { writer ->
                                    writer.write(command.key, result.nextValue)
                
                                    RemoteQueueHandler.State(
                                        nextState,
                    
                                        state.queue
                                            .filter {
                                                if (it.value.key != command.key) {
                                                    true
                                                } else {
                                                    when (it.value) {
                                                        is RemoteQueueHandler.SyncCommand.Send -> true
                                                        is RemoteQueueHandler.SyncCommand.Sync -> false
                                                    }
                                                }
                                            },
                    
                                        state.lastSuccessfulElapsedRealTimes + (command.key to time),
                                        nextFailedAttempts,
                                        state.failedSends
                                    )
                                }
                            }
        
                            is WritableSimpleRemoteDataSource.HandlerResult.Fail -> {
                                write {
                                    RemoteQueueHandler.State(
                                        nextState,
                                        state.queue.drop(1),
                                        state.lastSuccessfulElapsedRealTimes,
                                        nextFailedAttempts + (command.key to RemoteQueueHandler.State.TimeData.Precise(time)),
                                        state.failedSends
                                    )
                                }
                            }
                        }
    
                        changedSource.enqueueEvent(command.key)
                    }
                }
            }
        }
    )
    
    val commandsSaveQueue = RStaBaseMessageQueueHandlerImpl<List<Pair<Key, Value>>>(
        RStaCoroutineDispatcherFactory.create(dataLayerCoroutineLifecycle)
    ) {
        localCommandsRepository.write(Unit, it)
    }
    
    r.state.listen(RStaListenerInvoke.No, lifecycle) {
        val state = r.state.value
        val failedSends = state.failedSends.map { it.key to it.command }
        val activeSends = state.queue
            .mapNotNull { item ->
                when (item.value) {
                    is RemoteQueueHandler.SyncCommand.Send -> item.value.key to item.value.command
                    is RemoteQueueHandler.SyncCommand.Sync -> null
                }
            }
        
        commandsSaveQueue.add(failedSends + activeSends)
    }
    
    return object :
        WritableSimpleRemoteDataSource<StateData, Key, Value>,
        WritableRemoteComplexDataSource<StateData, Key, Value, Value> by r {
        
        override val changeSource: RStaEventSource<Key> = changedSource
    
        override fun sync(key: Key, target: RemoteSyncTarget): RemoteSyncId {
            changedSource.enqueueEvent(key)
            return r.sync(key, target)
        }
    
        override fun write(key: Key, command: Value): RemoteSyncId {
            changedSource.enqueueEvent(key)
            return r.write(key, command)
        }
    
        override fun cancelLocallyAhead(key: Key, afterCancelledDataMapper: (beforeCancelData: StateData) -> StateData) {
            r.change { state ->
                if (state.queue.firstOrNull()?.value?.key == key) {
                    // can't cancel the item which is probably will be sent right now
                    state
                } else {
                    val nextData = afterCancelledDataMapper(state.data)
                    val nextFailedSends = state.failedSends.filter { it.key != key }
                    val nextQueue = state.queue.filter { it.value.key != key }
                    RemoteQueueHandler.State(
                        nextData,
                        nextQueue,
                        state.lastSuccessfulElapsedRealTimes,
                        state.failedAttempts,
                        nextFailedSends
                    )
                }
            }
            
            changedSource.enqueueEvent(key)
        }
    }
}