package net.rationalstargazer.events.lifecycle

import kotlinx.coroutines.CoroutineScope
import net.rationalstargazer.events.lifecycle.RStaLifecycleMarker

interface LifecycleBasedSimpleCoroutineDispatcher {
    val lifecycle: RStaLifecycleMarker
    fun autoCancellableScope(): CoroutineScope?
    fun manuallyCancellableScope(): CoroutineScope?
}