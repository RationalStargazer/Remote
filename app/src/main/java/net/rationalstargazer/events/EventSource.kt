package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaHasLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycle

interface RStaEventSource<out T> : RStaHasLifecycle {

    fun listen(listener: RStaListener<T>) {
        listen(listener.lifecycleScope, listener::notify)
    }

    fun listen(lifecycle: RStaLifecycle, listener: (eventData: T) -> Unit)
}

class RStaEventDispatcher<T>(override val lifecycle: RStaLifecycle) : RStaEventSource<T> {

    override fun listen(lifecycle: RStaLifecycle, listener: (eventData: T) -> Unit) {
        listeners.addWithoutInvoke(lifecycle, listener)
    }

    fun enqueueEvent(eventValue: T) {
        listeners.enqueueEvent(eventValue)
    }

    private val listeners = RStaListenersRegistry<T>(lifecycle)
}