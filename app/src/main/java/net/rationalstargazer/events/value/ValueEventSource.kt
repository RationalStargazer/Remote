package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListener
import net.rationalstargazer.events.lifecycle.RStaLifecycle

/**
 * Represents a source of `value changed` event you can listen to.
 * See [RStaEventSource] for the information about events and lifecycles.
 *
 * It is important to note that current implementation doesn't have any thread-switching logic.
 * Current implementation of event sources and observable values are supposed to work on a single thread.
 * It means that the listeners will be called on the event source's thread, not the thread where you do [listen] call.
 */
interface RStaValueEventSource<out T> {

    enum class Invoke { YesNow, YesEnqueue, No }
    
    /**
     * See [RStaEventSource.lifecycle] for details.
     */
    val lifecycle: RStaLifecycle
    
    /**
     * See overloaded version for the details.
     */
    fun listen(invoke: Invoke, listener: RStaListener<T>) {
        listen(invoke, listener.lifecycleScope, listener::notify)
    }
    
    /**
     * Adds listener to the event source. See [RStaEventSource] for the information about events and lifecycles.
     *
     * If event source's [lifecycle] or listener's lifecycle is `finished`, the method does nothing regardless of `invoke` parameter.
     *
     * @param invoke When `YesNow` the `listener` will be directly called now (during the call of `listen` method) with the current value.
     * when `YesEnqueue` the call of the `listener` will be enqueued with the value at the time of 'listen' method call;
     * when `No` no call will be made until value will be changed.
     */
    fun listen(invoke: Invoke, lifecycle: RStaLifecycle, listener: (eventData: T) -> Unit)
    
    /**
     * Represents `this` instance as [RStaEventSource].
     */
    fun asEventSource(): RStaEventSource<T>
}