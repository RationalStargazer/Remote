package example

import net.rationalstargazer.remote.sync.BaseRemoteComplexDataSource
import net.rationalstargazer.remote.sync.RemoteQueueHandler
import net.rationalstargazer.types.handle

/*
sealed class RemoteChangeableState<out T: Any, out AheadAdditionalData, out ReceiveError : Any, out SendError : Any> {
    
    abstract val local: T?
    abstract val localAheadState: AheadState<T, AheadAdditionalData, SendError>?
    abstract val remoteState: RemoteState<T?, ReceiveError>
    abstract val inProgress: Boolean
    abstract val allSynced: Boolean
    
    data class NoData<out T : Any, out AheadAdditionalData, out ReceiveError : Any>(
        override val remoteState: RemoteState.NotSynced<Nothing?, ReceiveError>,
    ) : RemoteChangeableState<T, AheadAdditionalData, ReceiveError, Nothing>() {
        
        override val local: Nothing? = null
        
        override val localAheadState: Nothing? = null
    
        override val inProgress: Boolean = remoteState.inProgress
        
        override val allSynced: Boolean = false
    }
    
    sealed class Data<out T: Any, AheadAdditionalData, out ReceiveError : Any, out SendError : Any> :
        RemoteChangeableState<T, AheadAdditionalData, ReceiveError, SendError>() {
    
        abstract override val local: T
        abstract override val remoteState: RemoteState<T?, ReceiveError>
        
        data class Ahead<out T: Any, AdditionalAheadData, out ReceiveError : Any, out SendError : Any>(
            override val remoteState: RemoteState<T?, ReceiveError>,
            override val localAheadState: AheadState<T, AdditionalAheadData, SendError>,
        ) : Data<T, AdditionalAheadData, ReceiveError, SendError>() {
        
            override val local: T = localAheadState.value
        
            override val inProgress: Boolean = localAheadState.inProgress || remoteState.inProgress
            
            override val allSynced: Boolean = false
        }
    
        data class NotAhead<out T: Any, AdditionalAheadData, out ReceiveError : Any, out SendError : Any>(
            override val remoteState: RemoteState<T, ReceiveError>,
        ) : Data<T, AdditionalAheadData, ReceiveError, SendError>() {
        
            override val local: T = remoteState.value
    
            override val localAheadState: Nothing? = null
            
            override val inProgress: Boolean = remoteState.inProgress
        
            override val allSynced: Boolean = remoteState.isSynced
        }
    }
    
    sealed interface ByRemoteState<out T: Any, out AheadAdditionalData, out ReceiveError : Any, out SendError : Any> {
    
        val remoteState: RemoteState<T?, ReceiveError>
        val asState: RemoteChangeableState<T, AheadAdditionalData, ReceiveError, SendError>
        
        data class Behind<out T: Any, out AheadAdditionalData, out ReceiveError : Any, out SendError : Any>(
            override val remoteState: RemoteState.NotSynced<T?, ReceiveError>,
            val localAheadState: AheadState<T, AheadAdditionalData, SendError>?
        ) : ByRemoteState<T, AheadAdditionalData, ReceiveError, SendError> {
    
            override val asState: RemoteChangeableState<T, AheadAdditionalData, ReceiveError, SendError> =
                RemoteChangeableState(remoteState, localAheadState)
        }
    
        data class NotBehind<out T: Any, out AheadAdditionalData, out ReceiveError : Any, out SendError : Any>(
            override val remoteState: RemoteState.Synced<T>,
            val localAheadState: AheadState<T, AheadAdditionalData, SendError>?
        ) : ByRemoteState<T, AheadAdditionalData, ReceiveError, SendError> {
        
            override val asState: RemoteChangeableState<T, AheadAdditionalData, ReceiveError, SendError> =
                RemoteChangeableState(remoteState, localAheadState)
        }
    }
    
    data class AheadState<out T : Any, out AdditionalData, out Error : Any>(
        val value: T,
        val inProgress: Boolean,
        val additional: AdditionalData,
        val lastError: Error?,
    )
    
    companion object {
        fun <StateData, Key, Value : Any, Command> fromLocalRemoteWithState(
            repositoryState: RemoteQueueHandler.State<StateData, Key, Command>,
            localRemote: BaseRemoteComplexDataSource.LocalRemote<Value>?,
            key: Key,
        ): RemoteChangeableState<Value, Unit, Unit, Unit> {
            val keyInQueue = repositoryState.queue.any { it.value.key == key }
            
            if (localRemote == null) {
                return NoData(
                    RemoteState.NotSynced(
                        null,
                        keyInQueue,
                        Unit
                    )
                )
            }
            
            val hasSendsInQueue = repositoryState.queue.any {
                when (it.value) {
                    is RemoteQueueHandler.SyncCommand.Sync -> false
                    is RemoteQueueHandler.SyncCommand.Send -> true
                }
            }
            
            val hasFailedSends = repositoryState.failedSends.any { it.key == key }
            
            val lastFailed = repositoryState.failedAttempts[key] != null
            val remoteState = if (lastFailed) {
                RemoteState.NotSynced(localRemote.remote, keyInQueue, Unit)
            } else {
                RemoteState.Synced(localRemote.remote, keyInQueue)
            }
            
            return if (hasSendsInQueue || hasFailedSends) {
                Data.Ahead(remoteState, AheadState(localRemote.local, hasSendsInQueue, Unit, Unit))
            } else {
                Data.NotAhead(remoteState)
            }
        }
    }
    
    // fun <R : Any> mapData(mapper: (T) -> R): RemoteChangeableState<R, AheadAdditionalData, ReceiveError, SendError> {
    //     toEitherByData()
    //         .mapLeft { data ->
    //             when (data) {
    //                 is Data.Ahead -> Data.Ahead(remoteState.mapValue(mapper), localAheadState)
    //                 is Data.NotAhead -> TODO()
    //             }
    //         }
    // }
    
    // fun <R : Any> mapData(mapper: (T) -> R): RemoteChangeableState<R, AheadAdditionalData, ReceiveError, SendError> {
    //     return when (this) {
    //         is Data -> {
    //             Data(
    //                 when (remoteState) {
    //                     is RemoteState.NotSynced -> {
    //                         RemoteState.Synced(mapper(remoteState.value), remoteState.inProgress)
    //                     }
    //
    //                     is RemoteState.Synced -> {
    //                         RemoteState.Synced(mapper(remoteState.value), remoteState.inProgress)
    //                     }
    //                 },
    //
    //                 localAheadState?.let { ahead ->
    //                     AheadState(
    //                         mapper(ahead.value),
    //                         ahead.inProgress,
    //                         ahead.additional,
    //                         ahead.lastError
    //                     )
    //                 }
    //             )
    //         }
    //
    //         is NoData -> {
    //             NoData(remoteState)
    //         }
    //     }
    // }
}

fun <T: Any, AheadAdditionalData, ReceiveError : Any, SendError : Any> RemoteChangeableState(
    remoteState: RemoteState<T?, ReceiveError>,
    localAheadState: RemoteChangeableState.AheadState<T, AheadAdditionalData, SendError>?
): RemoteChangeableState<T, AheadAdditionalData, ReceiveError, SendError> {
    return if (localAheadState != null) {
        RemoteChangeableState.Data.Ahead(remoteState, localAheadState)
    } else {
        remoteState.eitherEmptyOrNot().handle(
            {
                RemoteChangeableState.NoData<T, AheadAdditionalData, ReceiveError>(it)
            },
            {
                RemoteChangeableState.Data.NotAhead(it)
            }
        )
    }
}*/
