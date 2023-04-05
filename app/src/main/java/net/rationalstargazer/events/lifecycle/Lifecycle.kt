package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.RStaListener
import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

interface RStaLifecycleMarker {
    val finished: Boolean
}

interface RStaLifecycleScope : RStaLifecycleMarker {
    val coordinator: RStaEventsQueueDispatcher

    override val finished: Boolean

    val consumed: Boolean

    fun listenBeforeFinish(callIfAlreadyFinished: Boolean, listener: RStaListener<Unit>) {
        listenBeforeFinish(callIfAlreadyFinished, listener.lifecycleScope, listener::notify)
    }

    fun listenBeforeFinish(callIfAlreadyFinished: Boolean, listenerLifecycle: RStaLifecycle, listenerFunction: (Unit) -> Unit)

    fun listenFinished(callIfAlreadyFinished: Boolean, listener: RStaListener<Unit>) {
        listenFinished(callIfAlreadyFinished, listener.lifecycleScope, listener::notify)
    }

    fun listenFinished(callIfAlreadyFinished: Boolean, listenerLifecycle: RStaLifecycle, listenerFunction: (Unit) -> Unit)

    fun watch(lifecycle: RStaLifecycleScope)
}

// interface RStaHasLifecycle {
//     val lifecycle: RStaLifecycle
// }

interface RStaLifecycle : RStaLifecycleScope

interface RStaSuspendableLifecycle : RStaLifecycleScope {

    val scope: RStaLifecycle

    val active: Boolean

    // fun listenStateChange(listener: Listener<Boolean>)
    //
    // fun listenStateChange(listenerLifecycle: Lifecycle, listener: (value: Boolean) -> Unit) {
    //     listenStateChange(StdListener(listenerLifecycle, listener))
    // }
}

interface RStaControlledLifecycle : RStaLifecycle {

    fun close()

    // TODO: not implemented yet
    //fun close(onConsumed: () -> Unit)
}