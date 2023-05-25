package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.lifecycle.RStaLifecycle

@Suppress("FunctionName")
fun <Value, SourceAEvent, SourceBEvent> RStaCombinedStrictItem(
    lifecycle: RStaLifecycle,
    upstreamA: RStaEventSource<SourceAEvent>,
    upstreamAChangeHandler: (currentEvent: SourceAEvent, lastAEvent: SourceAEvent?, lastBEvent: SourceBEvent?) -> Value,
    upstreamB: RStaEventSource<SourceBEvent>,
    upstreamBChangeHandler: (currentEvent: SourceBEvent, lastAEvent: SourceAEvent?, lastBEvent: SourceBEvent?) -> Value,
    valueVersion: () -> Long,
    valueFunction: () -> Value
): RStaValue<Value> {
    return RStaCombinedGenericItem(
        lifecycle,
        true,
        upstreamA,
        upstreamAChangeHandler,
        upstreamB,
        upstreamBChangeHandler,
        valueVersion,
        valueFunction
    ).considerStrictValue()
}