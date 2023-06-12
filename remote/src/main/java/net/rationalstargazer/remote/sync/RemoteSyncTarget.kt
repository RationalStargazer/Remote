package net.rationalstargazer.remote.sync

sealed class RemoteSyncTarget {
    
    fun isFresh(
        existsLocally: Boolean,
        lastUpdatedAt: RemoteQueueHandler.State.TimeData.Precise?,
        elapsedRealTimeNow: Long
    ): Boolean {
        return when (this) {
            ExistsLocally -> existsLocally
    
            RemoteSyncTarget.Once -> lastUpdatedAt != null
    
            is RemoteSyncTarget.InLast -> {
                if (lastUpdatedAt != null) {
                    lastUpdatedAt.elapsedRealTime + this.millisecs > elapsedRealTimeNow
                } else {
                    false
                }
            }
        }
    }
    
    /**
     * Value is considered actual if it exists in local cache, no matter how long ago (months ago?) it was loaded.
     */
    object ExistsLocally : RemoteSyncTarget()
    
    /**
     * Value is considered actual if it was updated during current application run.
     */
    object Once : RemoteSyncTarget()
    
    /**
     * Value is considered actual if it was updated not earlier than `millisecs` (milliseconds) ago.
     */
    data class InLast(val millisecs: Long) : RemoteSyncTarget()
}