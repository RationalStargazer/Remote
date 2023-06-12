package net.rationalstargazer.events.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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

    override fun launchAutoCancellable(block: suspend CoroutineScope.() -> Unit) {
        if (lifecycle.finished) {
            return
        }

        CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext.job)).launch {
            block()
        }
    }

    override fun launchNonCancellable(block: suspend CoroutineScope.() -> Unit) {
        if (lifecycle.finished) {
            return
        }

        CoroutineScope(scope.coroutineContext + Job()).launch {
            block()
        }
    }

    init {
        lifecycle.listenFinished(true, lifecycle) {
            scope.cancel("lifecycle has finished")
        }
    }
}