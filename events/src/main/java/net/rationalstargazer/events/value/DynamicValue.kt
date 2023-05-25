package net.rationalstargazer.events.value

import net.rationalstargazer.events.Container
import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListenersRegistry
import net.rationalstargazer.events.lifecycle.RStaLifecycle

class RStaDynamicValue<out Value : Event, Event>(
    override val lifecycle: RStaLifecycle,
    private val skipSameEvent: Boolean,
    valueVersion: () -> Long,
    valueFunction: () -> Value
) : RStaGenericValue<Value, Event> {

    override fun checkValueVersion(): Long {
        return versionFunction?.invoke()
            ?: cachedVersion!!  // cachedVersion created before versionFunction reference is cleared
    }

    override val value: Value
        get() {
            val ver = checkValueVersion()
            if (ver == cachedVersion) {
                return cache!!.value  // ver != null => cachedVersion != null => cache != null
            }

            cachedVersion = ver

            val r = function!!.invoke()

            val c = cache
            if (c != null) {
                c.value = r
            } else {
                cache = Container(r)
            }

            return r
        }

    fun notifyChanged(eventData: Event) {
        if (skipSameEvent) {
            val last = lastEvent
            if (last != null) {
                if (last.value == eventData) {
                    return
                }

                last.value = eventData
            } else {
                lastEvent = Container(eventData)
            }
        }

        listeners.enqueueEvent(eventData)
    }

    override fun listen(
        invoke: RStaListenerInvoke,
        lifecycle: RStaLifecycle,
        listener: (eventData: Event) -> Unit
    ) {
        listeners.add(invoke, this::value, lifecycle, listener)
    }

    override fun asEventSource(): RStaEventSource<Event> {
        return listeners.asEventSource()
    }

    private var function: (() -> Value)? = valueFunction
    private var versionFunction: (() -> Long)? = valueVersion

    private val listeners = RStaListenersRegistry<Event>(lifecycle)
    private var lastEvent: Container<Event>? = null
    private var cache: Container<Value>? = null
    private var cachedVersion: Long? = null

    init {
        lifecycle.listenBeforeFinish(true, lifecycle) {
            if (cachedVersion == null) {
                @Suppress("UNUSED_VARIABLE")  // reading to create cache
                val v = value
            }
        }

        lifecycle.listenFinished(true, lifecycle) {
            this.versionFunction = null
            this.function = null
        }
    }
}