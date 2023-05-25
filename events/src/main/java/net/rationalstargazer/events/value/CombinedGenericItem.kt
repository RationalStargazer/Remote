package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaWhileAllLifecycle

@Suppress("FunctionName")
fun <Value : Event, Event, SourceAValue : SourceAEvent, SourceAEvent, SourceBValue : SourceBEvent, SourceBEvent> RStaCombinedGenericItem(
    lifecycle: RStaLifecycle,
    skipSameEvent: Boolean,
    upstreamA: RStaGenericValue<SourceAValue, SourceAEvent>,
    upstreamAChangeHandler: (currentEvent: SourceAEvent, lastAEvent: SourceAEvent?, lastBEvent: SourceBEvent?) -> Event,
    upstreamB: RStaGenericValue<SourceBValue, SourceBEvent>,
    upstreamBChangeHandler: (currentEvent: SourceBEvent, lastAEvent: SourceAEvent?, lastBEvent: SourceBEvent?) -> Event,
    valueFunction: (SourceAValue, SourceBValue) -> Value
): RStaGenericValue<Value, Event> {
    val versionHelper = RStaValueVersionHelper.DefaultCombinedValue.create(
        upstreamA::checkValueVersion,
        upstreamB::checkValueVersion
    )

    return RStaCombinedGenericItem(
        lifecycle,
        skipSameEvent,
        upstreamA.asEventSource(),
        upstreamAChangeHandler,
        upstreamB.asEventSource(),
        upstreamBChangeHandler,
        versionHelper
    ) {
        valueFunction(upstreamA.value, upstreamB.value)
    }
}

@Suppress("FunctionName")
fun <Value : Event, Event, SourceAEvent, SourceBEvent> RStaCombinedGenericItem(
    lifecycle: RStaLifecycle,
    skipSameEvent: Boolean,
    upstreamA: RStaEventSource<SourceAEvent>,
    upstreamAChangeHandler: (currentEvent: SourceAEvent, lastAEvent: SourceAEvent?, lastBEvent: SourceBEvent?) -> Event,
    upstreamB: RStaEventSource<SourceBEvent>,
    upstreamBChangeHandler: (currentEvent: SourceBEvent, lastAEvent: SourceAEvent?, lastBEvent: SourceBEvent?) -> Event,
    valueVersion: () -> Long,
    valueFunction: () -> Value
): RStaGenericValue<Value, Event> {
    val r = RStaDynamicValue<Value, Event>(
        RStaWhileAllLifecycle(lifecycle.coordinator, lifecycle, upstreamA.lifecycle, upstreamB.lifecycle),
        skipSameEvent,
        valueVersion,
        valueFunction
    )

    var lastA: SourceAEvent? = null
    var lastB: SourceBEvent? = null

    upstreamA.listen(lifecycle) { sourceEvent ->
        val event = upstreamAChangeHandler(sourceEvent, lastA, lastB)
        lastA = sourceEvent
        r.notifyChanged(event)
    }

    upstreamB.listen(lifecycle) { sourceEvent ->
        val event = upstreamBChangeHandler(sourceEvent, lastA, lastB)
        lastB = sourceEvent
        r.notifyChanged(event)
    }

    return r
}