package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaWhileAllLifecycle

interface RStaDerivedValue<T> : RStaGenericValue<T, T> {
    fun notifyNow()
}

fun <T> RStaDerivedValue(
    lifecycle: RStaLifecycle,
    sources: List<RStaValueSource<*>>,
    valueFunction: () -> T
): RStaDerivedValue<T> {
    var customValueVersion = 0L

    val base = RStaDynamicValue<T, T>(
        RStaWhileAllLifecycle(lifecycle.coordinator, sources.map { it.lifecycle } + lifecycle),
        true,
        RStaValueVersionHelper.DefaultCombinedValue.create(sources.map { it::checkValueVersion } + { customValueVersion }),
        valueFunction
    )

    return object : RStaDerivedValue<T>, RStaGenericValue<T, T> by base {
        override fun notifyNow() {
            customValueVersion++
            base.notifyChanged(base.value)
        }
    }
}