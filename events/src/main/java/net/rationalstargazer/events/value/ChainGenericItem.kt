package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaWhileAllLifecycle

@Suppress("FunctionName")
fun <Value : Event, Event, SourceEvent> RStaChainGenericItem(
    lifecycle: RStaLifecycle,
    skipSameEvent: Boolean,
    upstreamChangeSource: RStaValueEventSource<SourceEvent>,
    changeHandler: (SourceEvent, emitter: (Event) -> Unit) -> Unit,
    valueVersion: () -> Long,
    valueFunction: () -> Value
): RStaGenericValue<Value, Event> {
    return RStaChainGenericItem(
        lifecycle,
        skipSameEvent,
        upstreamChangeSource.asEventSource(),
        changeHandler,
        valueVersion,
        valueFunction
    )
}

@Suppress("FunctionName")
fun <Value : Event, Event, SourceEvent> RStaChainGenericItem(
    lifecycle: RStaLifecycle,
    skipSameEvent: Boolean,
    upstreamChangeSource: RStaEventSource<SourceEvent>,
    changeHandler: (SourceEvent, emitter: (Event) -> Unit) -> Unit,
    valueVersion: () -> Long,
    valueFunction: () -> Value
): RStaGenericValue<Value, Event> {
    val r = RStaDynamicValue<Value, Event>(
        RStaWhileAllLifecycle(lifecycle, upstreamChangeSource.lifecycle),
        skipSameEvent,
        valueVersion,
        valueFunction
    )

    upstreamChangeSource.listen(lifecycle) { sourceEvent ->
        changeHandler(sourceEvent) { handledEvent ->
            r.notifyChanged(handledEvent)
        }
    }

    return r
}

@Suppress("FunctionName")
fun <Value : Event, Event, SourceEvent> RStaChainGenericItem(
    lifecycle: RStaLifecycle,
    skipSameEvent: Boolean,
    upstreamChangeSource: RStaValueEventSource<SourceEvent>,
    changeHandler: (SourceEvent) -> Event,
    valueVersion: () -> Long,
    valueFunction: () -> Value
) : RStaGenericValue<Value, Event> {
    return RStaChainGenericItem(
        lifecycle,
        skipSameEvent,
        upstreamChangeSource.asEventSource(),
        changeHandler,
        valueVersion,
        valueFunction
    )
}

@Suppress("FunctionName")
fun <Value : Event, Event, SourceEvent> RStaChainGenericItem(
    lifecycle: RStaLifecycle,
    skipSameEvent: Boolean,
    upstreamChangeSource: RStaEventSource<SourceEvent>,
    changeHandler: (SourceEvent) -> Event,
    valueVersion: () -> Long,
    valueFunction: () -> Value
) : RStaGenericValue<Value, Event> {
    val r = RStaDynamicValue<Value, Event>(
        RStaWhileAllLifecycle(lifecycle, upstreamChangeSource.lifecycle),
        skipSameEvent,
        valueVersion,
        valueFunction
    )

    upstreamChangeSource.listen(lifecycle) { sourceEvent ->
        val handledEvent = changeHandler(sourceEvent)
        r.notifyChanged(handledEvent)
    }

    return r
}