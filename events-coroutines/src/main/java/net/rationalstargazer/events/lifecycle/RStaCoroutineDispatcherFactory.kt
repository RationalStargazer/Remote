package net.rationalstargazer.events.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

object RStaCoroutineDispatcherFactory {
    fun create(coroutineLifecycle: RStaCoroutineLifecycle): RStaLifecycleBasedCoroutineDispatcher {
        return LifecycleBasedCoroutineDispatcherImpl(coroutineLifecycle.lifecycle, coroutineLifecycle.coroutineContext)
    }
}

private class LifecycleBasedCoroutineDispatcherImpl(
    override val lifecycle: RStaLifecycle,
    context: CoroutineContext
) : RStaLifecycleBasedCoroutineDispatcher {

    private val scope = CoroutineScope(context + SupervisorJob())

    override fun autoCancellableScope(): CoroutineScope? {
        if (lifecycle.finished) {
            return null
        }

        return CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext.job))
    }

    override fun manuallyCancellableScope(): CoroutineScope? {
        if (lifecycle.finished) {
            return null
        }

        return CoroutineScope(scope.coroutineContext + Job())
    }

    init {
        lifecycle.listenFinished(true, lifecycle) {
            scope.cancel("lifecycle has finished")
        }
    }
}