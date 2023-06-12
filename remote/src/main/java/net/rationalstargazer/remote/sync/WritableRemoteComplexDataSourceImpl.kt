package net.rationalstargazer.remote.sync

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycleBasedSimpleCoroutineDispatcher
import net.rationalstargazer.events.value.RStaDerivedValue
import net.rationalstargazer.events.value.RStaValue
import net.rationalstargazer.events.value.RStaValueDispatcher
import net.rationalstargazer.events.value.considerStrictValue
import net.rationalstargazer.logic.RStaBaseMessageQueueHandlerImpl

/**
 * [WritableRemoteComplexDataSource] which is based on the concept
 * "remote values are sources of truth,
 * local values are either equal to remotes or (if they are currently in "locally ahead" state)
 * described as consecutive commands that describes changes that are applied over remote values".
 * Remote values are stored in [localRemote] repository.
 * When local value is in "locally ahead" state
 * it is kept as [RemoteQueueHandler.SyncCommand.Send] command in [RemoteQueueHandler.State.queue],
 * and "locally ahead" values that were failed to send to the server are kept in [RemoteQueueHandler.State.failedSends].
 *
 * [readLocalRemote] returns [BaseRemoteComplexDataSource.LocalRemote.remote] value directly from localRemoteRepository,
 * and [BaseRemoteComplexDataSource.LocalRemote.local] value is calculated every time
 * by applying information about failed in the past (stored in [RemoteQueueHandler.State.failedSends])
 * and scheduled for the future (stored in [RemoteQueueHandler.State.queue]) relevant SyncCommand.Send commands.
 *
 * localRemote values are usually kept in on-device persistent storage (local cache).
 * If you also want to keep "locally aheads" values (to not lose them between restarts of the application),
 * you can observe [state] value and save all Send commands, at first from the State.failedSends,
 * followed by the Send commands from State.queue.
 * (SyncCommand.Sync commands in the queue are not important, you don't need to save them).
 * To restore "locally aheads" next time use `initialCommandsOneTimeSource` and provide saved Send commands there.
 */
class WritableRemoteComplexDataSourceImpl<StateData, Key, Value : Any, Command>(
    
    /**
     * [net.rationalstargazer.events.lifecycle.RStaLifecycle] of [state].
     * When lifecycle will be finished all listeners will be automatically removed, and no events will be delivered since.
     */
    private val lifecycle: RStaLifecycle,

    /**
     * [net.rationalstargazer.events.lifecycle.RStaLifecycleBasedSimpleCoroutineDispatcher]
     * coroutine context and lifecycle for internal queue of commands.
     * When its lifecycle will be finished commands queue will be closed,
     * calls to [sync], [write], or [change] will throw IllegalStateException,
     * and [start] and [pause] will do nothing.
     */
    private val coroutineDispatcher: RStaLifecycleBasedSimpleCoroutineDispatcher,

    /**
     * Access to local cache of remote data.
     * Values that are read with it are the return values of `readLocalRemote](key)?.remote`.
     * Values that are write with [commandsHandler] `write` argument (see [commandsHandler]) will be written using this localRemote.
     */
    private val localRemote: Repository.Writable<Key, Value>,

    /**
     * First value of [state]
     */
    initialState: StateData,

    /**
     * One-time provider of the next-after-first state you'd like to have but was unable to provide it to the time of instantiation.
     * The implementation will wait for the state to be fetched
     * (all commands will be postponed (including those provided with [asyncStateOneTimeSource]),
     * then will be added to the loaded state normally using [stateReducer])
     */
    asyncStateOneTimeSource: (suspend () -> RemoteQueueHandler.State<StateData, Key, Command>)? = null,

    /**
     * One-time provider of the commands you want to be the first in the queue
     * but was unable to provide them to the time of instantiation.
     * At first [asyncStateOneTimeSource] will be executed (if it is not null), then asyncStateOneTimeSource,
     * (all common commands that are results of [sync] and [write] calls will be postponed,
     * then will be combined with the the result of asyncCommandsOneTimeSource,
     * asyncCommandsOneTimeSource will be the first, the common commands after them)
     */
    asyncCommandsOneTimeSource: (suspend () -> List<RemoteQueueHandler.SyncCommand<Key, Command>>)?,

    /**
     * Should be pure function.
     *
     * Folds new commands into the state.
     *
     * Implementation details:
     * stateReducer is used every time when it is needed to assemble actual state with new commands are taken into account.
     * It is called at every read of [state],
     * at every [read] and [readLocalRemote] calls before the call of [localValueReducer],
     * and before the handling of each [RemoteQueueHandler.SyncCommand] (see [commandsHandler])
     */
    private val stateReducer: (
        state: RemoteQueueHandler.State<StateData, Key, Command>,
        command: RemoteQueueHandler.QueueCommand<StateData, Key, Command>
    ) -> RemoteQueueHandler.State<StateData, Key, Command>,

    /**
     * Should be pure function.
     *
     * Used in [read] and [readLocalRemote] to calculate [BaseRemoteComplexDataSource.LocalRemote.local].
     * `remoteValue` parameter is the value that was get from [localRemote] repository.
     * The value represents local cache of last known remote value.
     * Null value means "no cache".
     * Usually to calculate [BaseRemoteComplexDataSource.LocalRemote.local]
     * localValueReducer should apply in sequence all [RemoteQueueHandler.SyncCommand.Send] commands over the remoteValue,
     * at first from [RemoteQueueHandler.State.failedSends], then from [RemoteQueueHandler.State.queue],
     * but it is entirely up to implementation.
     */
    private val localValueReducer: (
        key: Key,
        remoteValue: Value?,
        state: RemoteQueueHandler.State<StateData, Key, Command>
    ) -> Value?,

    /**
     * A handler that is used to process one [RemoteQueueHandler.SyncCommand] from the `state` argument according to the command's contracts
     * (see [RemoteQueueHandler.SyncCommand.Send] and [RemoteQueueHandler.SyncCommand.Sync] for details).
     *
     * `state` is the state value at the moment just before the call of the handler.
     *
     * Precondition: [RemoteQueueHandler.State.queue] is not empty.
     *
     * From commandsHandler's point of view the commandHandler is the only one who can change it
     * (using `write` calls, see below) for the time of one separate handling.
     * So you don't need to consider a possibility that the state can be changed from the outside at the middle of the handling.
     * All external (not in the result of `write` call) changes of the state during the handling
     * don't actually change internal value of the state, instead they are saved into separate waitingCommands queue
     * (including changes that are the result of [change] call)
     * and will be applied to the result state automatically using [stateReducer] after the commandsHandler call will be finished.
     * commandsHandler should change the state by using `write` argument (see below).
     *
     * For the one call of commandHandler it is supposed it will pick an appropriate SyncCommand from the State.queue,
     * handle it appropriately, and write the result using `write` argument (see [WritableRemoteComplexDataSourceImpl_Handler_Write]).
     *
     * The handler can use `write` to update data in repository as many times as it wants and at any time it sees appropriate
     * (for example one `write` call can be used before the remote call
     * to write the data that indicates that communication process was started
     * and another call can be made after the remote call to update the data according to the result of the call),
     * but it should be noted that every `write` call probably holds mutex lock during its call
     * blocking the access for other users of the repository,
     * so it is advised to not make remote calls inside a `write` call.
     * Data and the state that was written with `write` argument will be effective immediately after the end of the `write` call.
     *
     * The handler is required to make at least one `write` call during the handling.
     *
     * `read` can be used to read additional data from the repository if it is necessary.
     * But do not use `read` to read the data inside a `write` call's lambda.
     * If you want to read the data inside a `write` call use the Writer that was provided by the `write`,
     * it is capable of reading too and designed to be used alongside with its write calls.
     * Usage of `read` argument inside `write` call's lambda can lead to dead lock for some implementations
     * if `write` uses mutex lock to block the access completely until the writing will be finished
     * and `read` is based on the same mutex.
     */
    private val commandsHandler: suspend (
        state: RemoteQueueHandler.State<StateData, Key, Command>,
        read: Repository<Key, Value>,
        write: WritableRemoteComplexDataSourceImpl_Handler_Write<StateData, Key, Value, Command>
    ) -> Unit,
): WritableRemoteComplexDataSource<StateData, Key, Value, Command> {

    private val ids = RemoteSyncId.Factory.create()
    
    /**
     * The state that is only updated just before and during the call of commandsHandler
     */
    private val innerState = RStaValueDispatcher<RemoteQueueHandler.State<StateData, Key, Command>>(
        lifecycle,
        RemoteQueueHandler.State(
            initialState,
            emptyList(),
            emptyMap(),
            emptyMap(),
            emptyList()
        )
    )
    
    /**
     * Commands that are waiting to be added to innerState at appropriate time.
     */
    private val waitingCommands = RStaValueDispatcher<List<RemoteQueueHandler.QueueCommand<StateData, Key, Command>>>(
        lifecycle,
        emptyList()
    )
    
    /**
     * Observable state (see [RStaValue] for details)
     */
    private val _state = RStaDerivedValue(
        lifecycle,
        listOf(innerState,  waitingCommands),
    ) {
        var reduced = innerState.value
        val commands = waitingCommands.value
        for (command in commands) {
            reduced = stateReducer(reduced, command)
        }
    
        reduced
    }
    
    override val state: RStaValue<RemoteQueueHandler.State<StateData, Key, Command>> = _state.considerStrictValue()
    
    private val _loaded = RStaValueDispatcher<Boolean>(lifecycle, false)
    
    /**
     * It is true when asynchronous first-time preparation of [state] is completed.
     *
     * If (asyncStateOneTimeSource != null || asyncCommandsOneTimeSource != null) then it is initially false,
     * and will become true after the state from asyncStateOneTimeSource and asyncCommandsOneTimeSource will be returned
     * and applied as the `state` value.
     */
    val loaded: RStaValue<Boolean> = _loaded
    
    private val loadJob = Job()

    override fun sync(key: Key, target: RemoteSyncTarget): RemoteSyncId {
        val id = ids.newId()
    
        if (lifecycle.finished || coroutineDispatcher.lifecycle.finished) {
            // TODO: improve logging
            throw IllegalStateException("call to write() after lifecycle was finished")
        }
    
        val command = RemoteSyncIdValue(id, RemoteQueueHandler.SyncCommand.Sync(key, target))
        val item = RemoteQueueHandler.QueueCommand.Remote(command)
        waitingCommands.value = waitingCommands.value + item
        _state.notifyNow()
        commandsQueue.add(Unit)
        return command.id
    }
    
    override suspend fun read(key: Key): Value? {
        return readLocalRemoteWithState(key).second?.local
    }
    
    override suspend fun readLocalRemote(key: Key): BaseRemoteComplexDataSource.LocalRemote<Value>? {
        return readLocalRemoteWithState(key).second
    }
    
    override suspend fun readWithState(key: Key): Pair<RemoteQueueHandler.State<StateData, Key, Command>, Value?> {
        val (state, localRemote) = readLocalRemoteWithState(key)
        return state to localRemote?.local
    }
    
    override suspend fun readLocalRemoteWithState(
        key: Key
    ): Pair<RemoteQueueHandler.State<StateData, Key, Command>, BaseRemoteComplexDataSource.LocalRemote<Value>?> {
        loadJob.join()
        
        lateinit var s: RemoteQueueHandler.State<StateData, Key, Command>
        var r: Value? = null
        
        localRemote.access {
            s = state.value
            r = localRemote.read(key)
        }
        
        val remote = r
        val local = localValueReducer(key, remote, state.value)
    
        if (local == null || remote == null) {
            return s to null
        }
    
        return s to BaseRemoteComplexDataSource.LocalRemote(local, remote)
    }
    
    override fun write(key: Key, command: Command): RemoteSyncId {
        val id = ids.newId()

        if (lifecycle.finished || coroutineDispatcher.lifecycle.finished) {
            // TODO: improve logging
            throw IllegalStateException("call to write() after lifecycle was finished")
        }

        val syncCommand = RemoteSyncIdValue(id, RemoteQueueHandler.SyncCommand.Send(key, command))
        val item = RemoteQueueHandler.QueueCommand.Remote(syncCommand)
        waitingCommands.value = waitingCommands.value + item
        _state.notifyNow()
        commandsQueue.add(Unit)
        return syncCommand.id
    }
    
    override suspend fun waitForSync(key: Key, target: RemoteSyncTarget) {
        TODO("Not yet implemented")
    }
    
    fun change(
        reducer: (state: RemoteQueueHandler.State<StateData, Key, Command>) -> RemoteQueueHandler.State<StateData, Key, Command>
    ) {
        if (lifecycle.finished || coroutineDispatcher.lifecycle.finished) {
            return
        }
    
        waitingCommands.value = waitingCommands.value + RemoteQueueHandler.QueueCommand.StateChange(reducer)
        _state.notifyNow()
        commandsQueue.add(Unit)
    }
    
    /**
     * Resume commands queue (see [pause])
     */
    fun start() {
        startTarget = true
        
        if (_loaded.value) {
            commandsQueue.start()
        }
    }
    
    /**
     * Pause commands queue.
     * After the current command will be handled (if it is handling now) queue will be paused
     * ([commandsHandler] will not be called for the next command),
     * new commands will be accumulated in inner list, and [state] will remain to be unchanged
     * until the queue will be resumed with [start].
     */
    fun pause() {
        startTarget = false
        commandsQueue.pause()
    }

    private var startTarget: Boolean = true
    private var writeCalled: Boolean = false

    private val commandsQueue = RStaBaseMessageQueueHandlerImpl<Unit>(coroutineDispatcher, this::handleCommands)
        .also {
            it.pause()
        }
    
    private suspend fun handleCommands(@Suppress("UNUSED_PARAMETER") any: Unit) {
        while (startTarget && waitingCommands.value.isNotEmpty() || innerState.value.queue.isNotEmpty()) {
            val reducedState = if (waitingCommands.value.isEmpty()) {
                innerState.value
            } else {
                val s = state.value
                innerState.value = s
                waitingCommands.value = emptyList()
                _state.notifyNow()
                s
            }

            if (reducedState.queue.isEmpty()) {
                break
            }
            
            writeCalled = false
            commandsHandler(reducedState, localRemote, this::writer)
            
            if (!writeCalled) {
                // TODO: improve logging
                break
            }
        }
    }

    private suspend fun writer(
        block: suspend (Repository.Writer<Key, Value>) -> RemoteQueueHandler.State<StateData, Key, Command>
    ) {
        writeCalled = true
        localRemote.writeAccess {
            val state = block(it)
            innerState.value = state
            _state.notifyNow()
        }
    }

    init {
        val stateSource = asyncStateOneTimeSource
        val commandsSource = asyncCommandsOneTimeSource
        
        if (stateSource != null || commandsSource != null) {
            coroutineDispatcher.launchNonCancellable {
                if (stateSource != null) {
                    val state = stateSource()
                    innerState.value = state
                }
                
                if (commandsSource != null) {
                    val commands = commandsSource()
                        .map {
                            RemoteQueueHandler.QueueCommand.Remote(RemoteSyncIdValue(ids.newId(), it))
                        }
                    
                    waitingCommands.value = commands + waitingCommands.value
                }
                
                _state.notifyNow()
                
                _loaded.value = true
                loadJob.complete()
                
                if (startTarget) {
                    commandsQueue.start()
                    commandsQueue.add(Unit)
                }
            }
        } else {
            _loaded.value = true
            loadJob.complete()
            commandsQueue.start()
            commandsQueue.add(Unit)
        }
    }
}

/**
 * Provides Writer that is used to write data to repository.
 * Example:
 * ```
 * handlerWrite { writer: Repository.Writer<DataKey, DataValue> ->
 *     writer.write(key1, value1)
 *
 *     // in a straightforward implementation you usually need a single write call,
 *     // but in complicated interdependent data structures you may need multiple ones.
 *     writer.write(key2, value2)
 *
 *     // as the result of the lambda you should provide next RemoteQueueHandler.State
 *     // that corresponds to the new local values you just wrote with the `writer`
 *     // (the value will be written as the next state immediately after the lambda,
 *     // the inner logic of WritableRemoteComplexDataSourceImpl guarantees proper consistency of the state and underlying repository
 *     // as long as the repository adheres to its contracts)
 *     DataState(nextDataState)
 * }
 * ```
 */
typealias WritableRemoteComplexDataSourceImpl_Handler_Write<StateData, Key, Value, Command> = suspend (
    suspend (Repository.Writer<Key, Value>) -> RemoteQueueHandler.State<StateData, Key, Command>
) -> Unit