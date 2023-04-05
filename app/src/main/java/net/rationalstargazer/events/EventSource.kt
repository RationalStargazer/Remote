package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

/**
 * Represents a source of `event` you can listen to.
 *
 * In net.rationalstargazer.events package `events` are always sequential.
 * When you're firing an event the listeners are not called immediately (thus interrupting your control flow).
 * Instead the framework will schedule the invocation of the listeners using [RStaEventsQueueDispatcher] associated with the thread.
 * See [RStaEventsQueueDispatcher] for details.
 *
 * It is important to note that current implementation doesn't have any thread-switching logic.
 * Current implementations of event sources and observable values are supposed to work on a single thread.
 * It means that the listeners will be called on the event source's thread, not the thread where you make [listen] call.
 *
 * In net.rationalstargazer.events package event `listeners` normally are not removed manually
 * (with methods like "removeListener").
 * Instead every listener has a `lifecycle` ([RStaLifecycle]).
 * Listener is active as long as its correspondent lifecycle is not finished.
 * In the moment
 */
interface RStaEventSource<out T> {
    
    /**
     *
     */
    val lifecycle: RStaLifecycle
    
    fun listen(listener: RStaListener<T>) {
        listen(listener.lifecycleScope, listener::notify)
    }
    
    /**
     * Adds listener to the event source. For more details about events see [RStaEventSource].
     *
     * @param lifecycle listener's lifecycle.
     *
     * Listener's lifecycle determines
     */
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