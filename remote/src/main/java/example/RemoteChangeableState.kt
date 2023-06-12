package example

import net.rationalstargazer.remote.RemoteData
import net.rationalstargazer.remote.sync.BaseRemoteComplexDataSource
import net.rationalstargazer.remote.sync.RemoteQueueHandler
import net.rationalstargazer.remote.sync.RemoteSyncIdValue
import net.rationalstargazer.remote.sync.RemoteSyncTarget

sealed class RemoteChangeableState<out T: Any> {
    
    abstract val local: T?
    abstract val remote: RemoteState<T>
    abstract val aheadInfo: Data.AheadInfo<T>?
    abstract val state: Data.State<T>
    
    data class NoData<out T : Any>(
        override val remote: RemoteState.NotSynced<T>,
    ) : RemoteChangeableState<T>() {
        
        override val local: Nothing? = null
        override val aheadInfo: Nothing? = null
        
        override val state: Data.State.NotSynced<T> = Data.State.NotSynced(
            local = null,
            remote = null,
            inProgress = remote.inProgress,
            isAhead = null,
            lastError = remote.lastError
        )
    }
    
    data class Data<out T : Any>(
        override val local: T,
        override val remote: RemoteState<T>,
        override val aheadInfo: AheadInfo<T>?,
    ) : RemoteChangeableState<T>() {
        
        override val state: State<T>
        
        init {
            state = when (remote) {
                is RemoteState.Synced -> {
                    if (aheadInfo != null) {
                        State.Ahead(
                            local,
                            aheadInfo.inProgress || remote.inProgress,
                            aheadInfo.lastError,
                            aheadInfo.problematic
                        )
                    } else {
                        State.Synced(remote.value, remote.inProgress)
                    }
                }
                
                is RemoteState.NotSynced -> {
                    State.NotSynced(
                        local,
                        remote.value,
                        aheadInfo?.inProgress == true || remote.inProgress,
                        aheadInfo != null,
                        aheadInfo?.lastError ?: remote.lastError
                    )
                }
            }
        }
        
        data class AheadInfo<out T : Any>(
            val inProgress: Boolean,
            val lastError: RemoteData.Fail?,
            val problematic: Boolean
        )
        
        sealed class State<out T : Any> {
            
            abstract val value: T?
            abstract val inProgress: Boolean
            abstract val isAhead: Boolean?
            abstract val lastError: RemoteData.Fail?
            
            data class NotSynced<out T : Any>(
                val local: T?,
                val remote: T?,
                override val inProgress: Boolean,
                override val isAhead: Boolean?,
                override val lastError: RemoteData.Fail?
            ) : State<T>() {
                
                override val value: T? = local
            }
            
            data class Synced<out T : Any>(
                override val value: T,
                override val inProgress: Boolean,
            ) : State<T>() {
                
                override val isAhead: Boolean = false
                override val lastError: RemoteData.Fail? = null
            }
            
            data class Ahead<out T : Any>(
                override val value: T,
                override val inProgress: Boolean,
                override val lastError: RemoteData.Fail?,
                val problematic: Boolean
            ) : State<T>() {
                
                override val isAhead: Boolean = true
            }
        }
    }
    
    companion object {
        fun <StateData, Key, Value : Any, Command> fromLocalRemoteWithState(
            source: Pair<RemoteQueueHandler.State<StateData, Key, Command>, BaseRemoteComplexDataSource.LocalRemote<Value>?>,
            key: Key,
            syncCriteria: RemoteSyncTarget,
            elapsedRealTimeNow: Long,
            lastRemoteDataProvider: (Key) -> RemoteData.Fail?,
            problematicMapper: (Key) -> Boolean,
        ): RemoteChangeableState<Value> {
            val (state, localRemote) = source
    
            val keyInQueue = state.queue.any { it.value.key == key }
            
            if (localRemote == null) {
                return NoData(
                    RemoteState.NotSynced(
                        value = null,
                        keyInQueue,
                        lastRemoteDataProvider(key)
                    )
                )
            }
            
            val hasSendsInQueue = state.queue.any {
                when (it.value) {
                    is RemoteQueueHandler.SyncCommand.Sync -> false
                    is RemoteQueueHandler.SyncCommand.Send -> true
                }
            }
            
            val hasFailedSends = state.failedSends.any { it.key == key }
            
            val fresh = syncCriteria.isFresh(
                true,
                state.lastSuccessfulElapsedRealTimes[key]?.let { RemoteQueueHandler.State.TimeData.Precise(it) },
                elapsedRealTimeNow
            )
            
            if (hasSendsInQueue || hasFailedSends) {
                return Data(
                    local = localRemote.local,
                    remote = if (fresh) {
                        RemoteState.Synced(localRemote.remote, keyInQueue)
                    } else {
                        RemoteState.NotSynced(localRemote.remote, keyInQueue, lastRemoteDataProvider(key))
                    },
                    aheadInfo = Data.AheadInfo(hasSendsInQueue, lastRemoteDataProvider(key), problematicMapper(key))
                )
            }
    
            return Data(
                local = localRemote.local,
                remote = if (fresh) {
                    RemoteState.Synced(localRemote.remote, keyInQueue)
                } else {
                    RemoteState.NotSynced(localRemote.remote, keyInQueue, lastRemoteDataProvider(key))
                },
                aheadInfo = null
            )
        }
    }
}
