package net.rationalstargazer.remote.sync

sealed class RemoteSyncTarget {
    
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