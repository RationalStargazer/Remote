package net.rationalstargazer.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

interface LifecycleBasedCoroutineDispatcher : LifecycleBasedSimpleCoroutineDispatcher {
    override val lifecycle: Lifecycle
}

class LifecycleBasedCoroutineDispatcherImpl(
    override val lifecycle: Lifecycle,
    context: CoroutineContext
) : LifecycleBasedCoroutineDispatcher {

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