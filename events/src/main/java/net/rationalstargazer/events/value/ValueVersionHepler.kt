package net.rationalstargazer.events.value

class RStaValueVersionHelper<in CheckHolder : Any>(
    lastCheckHolder: CheckHolder,
    currentCheckHolder: CheckHolder,
    private val check: (CheckHolder) -> Unit
) {
    object DefaultCombinedValue {
        fun create(sourceValueVersionA: () -> Long, sourceValueVersionB: () -> Long): () -> Long {
            val helper =  RStaValueVersionHelper(LongArray(2), LongArray(2)) {
                it[0] = sourceValueVersionA()
                it[1] = sourceValueVersionB()
            }

            return helper::checkValueVersion
        }

        fun create(sourceValueVersions: List<() -> Long>): () -> Long {
            val size = sourceValueVersions.size
            val helper = RStaValueVersionHelper(LongArray(size), LongArray(size)) {
                for (i in 0 until size) {
                    it[i] = sourceValueVersions[i]()
                }
            }

            return helper::checkValueVersion
        }

        fun createFromSources(sources: List<RStaGenericValue<*, *>>): () -> Long {
            return create(sources.map { it::checkValueVersion })
        }
    }

    fun checkValueVersion(): Long {
        check(nextCheck)
        if (nextCheck == lastCheck) {
            return version
        }

        val t = lastCheck
        lastCheck = nextCheck
        nextCheck = t
        version++
        return version
    }

    private var lastCheck: CheckHolder = lastCheckHolder
    private var nextCheck: CheckHolder = currentCheckHolder
    private var version: Long = 0L
}