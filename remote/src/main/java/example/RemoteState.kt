package example

import net.rationalstargazer.remote.RemoteData

sealed class RemoteState<out T : Any> {
    
    abstract val value: T?
    abstract val inProgress: Boolean
    
    data class Synced<out T : Any>(override val value: T, override val inProgress: Boolean) : RemoteState<T>()
    
    data class NotSynced<out T : Any>(
        override val value: T?,
        override val inProgress: Boolean,
        val lastError: RemoteData.Fail?
    ) : RemoteState<T>()
}

