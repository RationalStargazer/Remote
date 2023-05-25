package net.rationalstargazer.events.lifecycle

import kotlinx.coroutines.CoroutineScope

interface RStaLifecycleBasedSimpleCoroutineDispatcher {
    val lifecycle: RStaLifecycleMarker
    fun manuallyCancellableScope(): CoroutineScope?
}