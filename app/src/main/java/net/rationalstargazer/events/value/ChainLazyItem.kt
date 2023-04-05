package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.RStaIntersectionLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycle

class ChainLazyItem<out Value>(
    private val base: FunctionalValue<Value, Any?>
) : RStaGenericValue<Value, Any?> by base {

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<Any?>,
        changeHandler: () -> Boolean,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        lifecycle,
        upstreamChangeSource.asEventSource(),
        changeHandler,
        valueGeneration,
        function
    )

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaEventSource<Any?>,
        changeHandler: () -> Boolean,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        FunctionalValue(
            RStaIntersectionLifecycle.get(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) {
            val notifyDownstream = changeHandler()
            if (notifyDownstream) {
                base.notifyChanged(Unit)
            }
        }
    }

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<Any?>,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        lifecycle,
        upstreamChangeSource.asEventSource(),
        valueGeneration,
        function
    )

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaEventSource<Any?>,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        FunctionalValue(
            RStaIntersectionLifecycle.get(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) {
            base.notifyChanged(Unit)
        }
    }
}