package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource

typealias RStaValue<T> = RStaGenericValue<T, T>

typealias RStaValueSource<T> = RStaGenericValue<T, Any?>

/**
 * It is essentially an [RStaEventSource] that holding (or keeping) a value that can be changed over time.
 */
interface RStaGenericValue<out Value : Event, out Event> : RStaValueEventSource<Event> {
    fun checkGeneration(): Long
    val value: Value
}