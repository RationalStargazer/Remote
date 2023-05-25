package net.rationalstargazer.remote.sync

data class RemoteSyncIdValue<out T>(val id: RemoteSyncId, val value: T)

@JvmInline
value class RemoteSyncId private constructor(val value: Int) {

    class Factory private constructor(firstIdValue: Int) {
        companion object {
            fun create(): Factory {
                return Factory(0)
            }
        }

        private var count: Int = firstIdValue

        fun newId(): RemoteSyncId {
            if (count == Int.MAX_VALUE) {
                //TODO: think about throw modules
                //TODO: log "operation id has exceeded Int.MAX_VALUE"
            }

            return RemoteSyncId(count++)
        }
    }
}