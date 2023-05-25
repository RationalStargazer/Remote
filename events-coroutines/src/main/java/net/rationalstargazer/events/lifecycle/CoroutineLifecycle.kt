package net.rationalstargazer.events.lifecycle

import kotlin.coroutines.CoroutineContext

data class RStaCoroutineLifecycle(
    val lifecycle: RStaLifecycle,
    val coroutineContext: CoroutineContext
)