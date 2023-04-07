package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

/**
 * Represents a source of `event` you can listen to.
 *
 * In `net.rationalstargazer.events` package `events` are always sequential.
 * When an event is fired the listeners are not called immediately (thus interrupting your control flow),
 * instead the framework will schedule the invocation of the listeners using [RStaEventsQueueDispatcher] associated with the thread
 * (see [RStaEventsQueueDispatcher] for details).
 *
 * It is important to note that current implementation doesn't have any thread-switching logic.
 * Current implementations of event sources and observable values are supposed to work on a single thread.
 * It means that the listeners will be called on the event source's thread, not the thread where you do [listen] call.
 *
 * In `net.rationalstargazer.events` package event `listeners` are not removed manually
 * (with methods like "removeListener").
 * Instead every listener has a `lifecycle` ([RStaLifecycle]) which can be the same or a different from the event source's [lifecycle].
 * Listener is active as long as its correspondent lifecycle is not finished.
 * The `finished` property of the lifecycle is checked immediately before the call of the listener
 * (if it is `false` the listener will not be called).
 *
 * After event source's [lifecycle] will become `finished` all the listeners will be automatically removed.
 * When event source's `lifecycle.finished == true` no events will be scheduled from now on,
 * but it is possible that some events were already scheduled (and now are in the event's queue).
 * It means that the listener can be called when the `lifecycle.finished` is actually `true`.
 *
 * It is a part of the nature of the event system.
 * All events are always represent something that has happened in the past.
 * After that other events can happen, things can be different since.
 * When you handle an event you may don't want to rely on the `eventData`,
 * instead you may prefer to get actual data and handle the situation according to it.
 *
 * When `lifecycle.consumed == true` it is guaranteed that all scheduled events were delivered and
 * no listeners will be called.
 */
interface RStaEventSource<out T> {
    
    /**
     * Lifecycle ([RStaLifecycle]) of events.
     *
     * When `lifecycle.finished == true` no further events will be scheduled
     * (some events can still be in the event queue and will be delivered to their listeners).
     * When `lifecycle.consumed == true` all events are delivered, all references to the listeners are removed.
     */
    val lifecycle: RStaLifecycle
    
    /**
     * See overloaded version for the details.
     */
    fun listen(listener: RStaListener<T>) {
        listen(listener.lifecycleScope, listener::notify)
    }
    
    /**
     * Adds listener to the event source. See [RStaEventSource] for the information about events and lifecycles.
     *
     * If event source's [lifecycle] or listener's lifecycle is `finished`, the method does nothing.
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