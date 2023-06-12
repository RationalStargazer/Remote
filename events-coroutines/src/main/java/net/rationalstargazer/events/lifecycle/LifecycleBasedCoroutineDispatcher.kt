package net.rationalstargazer.events.lifecycle

import kotlinx.coroutines.CoroutineScope

interface RStaLifecycleBasedCoroutineDispatcher : RStaLifecycleBasedSimpleCoroutineDispatcher {
    override val lifecycle: RStaLifecycle
    fun launchAutoCancellable(block: suspend CoroutineScope.() -> Unit)
}