package net.rationalstargazer.events

import kotlinx.coroutines.CoroutineScope

interface LifecycleBasedSimpleCoroutineDispatcher {
    val lifecycle: LifecycleMarker
    fun autoCancellableScope(): CoroutineScope?
    fun manuallyCancellableScope(): CoroutineScope?
}