package net.rationalstargazer.events

import kotlinx.coroutines.CoroutineScope

interface LifecycleBasedSimpleCoroutineDispatcher {
    val lifecycle: RStaLifecycleMarker
    fun autoCancellableScope(): CoroutineScope?
    fun manuallyCancellableScope(): CoroutineScope?
}