package net.rationalstargazer.events.lifecycle

/**
 * Returns the lifecycle which life span is limited by given `lifecycle`.
 * When the given lifecycle will become `finished` the result lifecycle will be finished.
 *
 * The result lifecycle is [RStaControlledLifecycle] so it can be finished at any time.
 */
@Suppress("FunctionName")
fun RStaInnerLifecycle(lifecycle: RStaLifecycleScope): RStaControlledLifecycle {
    if (lifecycle.finished) {
        return RStaLifecycleDispatcher.Finished(lifecycle.coordinator)
    }
    
    val inner = LifecycleDispatcher(lifecycle.coordinator)
    lifecycle.listenBeforeFinish(true, inner) {
        inner.close()
    }
    
    return inner
}