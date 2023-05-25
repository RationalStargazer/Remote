package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycleScope
import net.rationalstargazer.events.value.RStaListenerInvoke

/**
 * Can hold listeners as long as its lifecycle and lifecycles of the listeners are not finished.
 * @param lifecycle Lifecycle of the listener itself.
 */
class RStaListenersRegistry<T>(
    lifecycle: RStaLifecycle
) {

    private val registryLifecycle: RStaLifecycle = lifecycle

    fun addWithoutInvoke(listenerLifecycle: RStaLifecycle, listenerFunction: (T) -> Unit) {
        if (registryLifecycle.finished || listenerLifecycle.finished) {
            return
        }

        if (listenerLifecycle == registryLifecycle) {
            commonItems.add(listenerFunction)
            return
        }

        val registry = otherLifecyclesItems[listenerLifecycle]
            ?: RStaListenersRegistry<T>(listenerLifecycle).also { otherLifecyclesItems[listenerLifecycle] = it }

        registry.addWithoutInvoke(listenerLifecycle, listenerFunction)
    }

    fun add(
        invoke: RStaListenerInvoke,
        invokeValue: () -> T,
        listenerLifecycle: RStaLifecycle,
        listenerFunction: (T) -> Unit
    ) {
        if (listenerLifecycle.finished) {
            return
        }
        
        addWithoutInvoke(listenerLifecycle, listenerFunction)

        when (invoke) {
            RStaListenerInvoke.YesNow -> listenerFunction(invokeValue())

            RStaListenerInvoke.YesEnqueue -> enqueueEvent(invokeValue())

            RStaListenerInvoke.No -> {
                // do nothing
            }
        }
    }

    fun add(
        invoke: RStaListenerInvoke,
        invokeValue: T,
        listenerLifecycle: RStaLifecycle,
        listenerFunction: (T) -> Unit
    ) {
        if (registryLifecycle.finished || listenerLifecycle.finished) {
            return
        }
        
        addWithoutInvoke(listenerLifecycle, listenerFunction)

        when (invoke) {
            RStaListenerInvoke.YesNow -> listenerFunction(invokeValue)

            RStaListenerInvoke.YesEnqueue -> enqueueEvent(invokeValue)

            RStaListenerInvoke.No -> {
                // do nothing
            }
        }
    }

    fun enqueueEvent(eventValue: T) {
        if (registryLifecycle.finished) {
            return
        }

        val listeners = activeListeners()

        registryLifecycle.coordinator.enqueue {
            listeners.forEach {
                it(eventValue)
            }
        }
    }

    fun asEventSource(): RStaEventSource<T> {
        //TODO: subject for refactoring
        return object : RStaEventSource<T> {
            override fun listen(lifecycle: RStaLifecycle, listener: (eventData: T) -> Unit) {
                this@RStaListenersRegistry.addWithoutInvoke(lifecycle, listener)
            }

            override val lifecycle: RStaLifecycle
                get() {
                    return this@RStaListenersRegistry.registryLifecycle
                }
        }
    }

    private fun activeListeners(): List<(T) -> Unit> {
        if (registryLifecycle.finished) {
            return emptyList()
        }

        val list = commonItems.toMutableList()
        otherLifecyclesItems.values.forEach {
            if (!it.registryLifecycle.finished) {
                list.addAll(it.activeListeners())
            }
        }

        return list
    }

    private val commonItems: MutableList<(T) -> Unit> = mutableListOf()
    private val otherLifecyclesItems: MutableMap<RStaLifecycleScope, RStaListenersRegistry<T>> = mutableMapOf()

    init {
        registryLifecycle.listenFinished(true, registryLifecycle) {
            commonItems.clear()
            otherLifecyclesItems.clear()
        }
    }
}