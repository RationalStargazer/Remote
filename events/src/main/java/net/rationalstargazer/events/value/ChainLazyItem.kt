package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.Lifecycles
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.whileAll

class RStaChainLazyItem<out Value>(
    private val base: RStaDynamicValue<Value, Any?>
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
        RStaDynamicValue(
            Lifecycles.whileAll(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
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
        RStaDynamicValue(
            Lifecycles.whileAll(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) {
            base.notifyChanged(Unit)
        }
    }
}