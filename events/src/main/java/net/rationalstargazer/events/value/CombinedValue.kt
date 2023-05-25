package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaWhileAllLifecycle

@Suppress("FunctionName")
fun <Value, SourceA, SourceB> RStaCombinedValue(
    lifecycle: RStaLifecycle,
    upstreamA: RStaValue<SourceA>,
    upstreamB: RStaValue<SourceB>,
    valueFunction: (SourceA, SourceB) -> Value
): RStaValue<Value> {
    var lastEventA: SourceA = upstreamA.value
    var lastEventB: SourceB = upstreamB.value

    val versionHelper = RStaValueVersionHelper.DefaultCombinedValue.create(
        upstreamA::checkValueVersion,
        upstreamB::checkValueVersion
    )

    val r = RStaDynamicValue<Value, Value>(
        RStaWhileAllLifecycle(lifecycle.coordinator, lifecycle, upstreamA.lifecycle, upstreamB.lifecycle),
        true,
        versionHelper
    ) {
        valueFunction(upstreamA.value, upstreamB.value)
    }

    upstreamA.listen(RStaListenerInvoke.No, lifecycle) { sourceEvent ->
        lastEventA = sourceEvent
        val event = valueFunction(lastEventA, lastEventB)
        r.notifyChanged(event)
    }

    upstreamB.listen(RStaListenerInvoke.No, lifecycle) { sourceEvent ->
        lastEventB = sourceEvent
        val event = valueFunction(lastEventA, lastEventB)
        r.notifyChanged(event)
    }

    return r.considerStrictValue()
}