package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListener
import net.rationalstargazer.events.RStaListenersRegistry
import net.rationalstargazer.events.lifecycle.RStaHasLifecycle
import net.rationalstargazer.events.lifecycle.RStaIntersectionLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycle

class FunctionalValue<out Value : Event, Event>(
    override val lifecycle: RStaLifecycle,
    valueGeneration: () -> Long,
    function: () -> Value
) : RStaGenericValue<Value, Event> {

    // object EventBased {
    //     fun <T> create(
    //         lifecycle: Lifecycle,
    //         changeEventSource: EventSource<Any>,
    //         valueGeneration: () -> Long,
    //         function: () -> T
    //     ): FunctionalValue<T> {
    //         val r = FunctionalValue<T>(lifecycle, valueGeneration, function)
    //         changeEventSource.listen(lifecycle) { r.notifyChanged() }
    //         if (changeEventSource.lifecycle != lifecycle) {
    //             changeEventSource.lifecycle.listenBeforeFinish(true, lifecycle) {
    //                 //TODO: actually source functions should be released here
    //                 r.value  // read value to create cache
    //             }
    //         }
    //
    //         return r
    //     }
    //
    //     fun <T> create(
    //         lifecycle: Lifecycle,  //TODO: maybe also add default union lifecycle?
    //         changeSources: List<EventSource<Any>>,
    //         valueGeneration: () -> Long,
    //         function: () -> T
    //     ): FunctionalValue<T> {
    //         val item = FunctionalValue<T>(lifecycle, valueGeneration, function)
    //         changeSources.forEach {
    //             it.listen(lifecycle) { item.notifyChanged() }
    //         }
    //
    //         return item
    //     }
    // }

    override fun checkGeneration(): Long {
        return generation?.invoke()
            ?: cachedGeneration!!  // cachedGeneration created before generation reference is cleared
    }

    override val value: Value
        get() {
            val g = checkGeneration()
            if (g == cachedGeneration) {
                return cache!!.value  // g != null => cachedGeneration != null => cache exists
            }

            val r = function!!.invoke()

            val c = cache
            if (c != null) {
                c.value = r
            } else {
                cache = Cache(r)
            }

            cachedGeneration = g

            return r
        }

    fun notifyChanged(eventData: Event) {
        listeners.enqueueEvent(eventData)
    }

    override fun listen(
        invoke: RStaValueEventSource.Invoke,
        lifecycle: RStaLifecycle,
        listener: (eventData: Event) -> Unit
    ) {
        listeners.add(invoke, this::value, lifecycle, listener)
    }

    override fun asEventSource(): RStaEventSource<Event> {
        return listeners.asEventSource()
    }

    private var function: (() -> Value)? = function
    private var generation: (() -> Long)? = valueGeneration

    private val listeners = RStaListenersRegistry<Event>(lifecycle)
    private var cache: Cache<Value>? = null
    private var cachedGeneration: Long? = null

    class Cache<T>(var value: T)

    init {
        lifecycle.listenBeforeFinish(true, lifecycle) {
            if (cachedGeneration == null) {
                val v = value  // reading to create cache
            }
        }

        lifecycle.listenFinished(true, lifecycle) {
            generation = null
            this.function = null
        }
    }
}