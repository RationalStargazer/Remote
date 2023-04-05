package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycleScope
import net.rationalstargazer.events.value.RStaValueEventSource

class RStaListenersRegistry<T>(
    lifecycle: RStaLifecycle
) {

    private val registryLifecycle: RStaLifecycle = lifecycle

    fun addWithoutInvoke(listenerLifecycle: RStaLifecycle, listenerFunction: (T) -> Unit) {
        if (listenerLifecycle.finished) {
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
        invoke: RStaValueEventSource.Invoke,
        invokeValue: () -> T,
        listenerLifecycle: RStaLifecycle,
        listenerFunction: (T) -> Unit
    ) {
        addWithoutInvoke(listenerLifecycle, listenerFunction)

        when (invoke) {
            RStaValueEventSource.Invoke.YesNow -> listenerFunction(invokeValue())

            RStaValueEventSource.Invoke.YesEnqueue -> enqueueEvent(invokeValue())

            RStaValueEventSource.Invoke.No -> {
                // do nothing
            }
        }
    }

    fun add(
        invoke: RStaValueEventSource.Invoke,
        invokeValue: T,
        listenerLifecycle: RStaLifecycle,
        listenerFunction: (T) -> Unit
    ) {
        addWithoutInvoke(listenerLifecycle, listenerFunction)

        when (invoke) {
            RStaValueEventSource.Invoke.YesNow -> listenerFunction(invokeValue)

            RStaValueEventSource.Invoke.YesEnqueue -> enqueueEvent(invokeValue)

            RStaValueEventSource.Invoke.No -> {
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