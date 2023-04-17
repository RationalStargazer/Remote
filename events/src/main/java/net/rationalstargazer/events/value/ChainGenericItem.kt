package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.Lifecycles
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.whileAll

class RStaChainGenericItem<out Value : Event, Event, SourceEvent>(
    private val base: RStaDynamicValue<Value, Event>
) : RStaGenericValue<Value, Event> by base {

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<SourceEvent>,
        changeHandler: (SourceEvent, emitter: (Event) -> Unit) -> Unit,
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
        upstreamChangeSource: RStaEventSource<SourceEvent>,
        changeHandler: (SourceEvent, emitter: (Event) -> Unit) -> Unit,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        RStaDynamicValue(
            Lifecycles.whileAll(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) { sourceEvent ->
            changeHandler(sourceEvent) { handledEvent ->
                base.notifyChanged(handledEvent)
            }
        }
    }

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<SourceEvent>,
        changeHandler: (SourceEvent) -> Event,
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
        upstreamChangeSource: RStaEventSource<SourceEvent>,
        changeHandler: (SourceEvent) -> Event,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        RStaDynamicValue(
            Lifecycles.whileAll(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) { sourceEvent ->
            val handledEvent = changeHandler(sourceEvent)
            base.notifyChanged(handledEvent)
        }
    }
}