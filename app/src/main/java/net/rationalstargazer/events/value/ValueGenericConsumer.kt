package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListenersRegistry
import net.rationalstargazer.events.lifecycle.RStaLifecycle

class ValueGenericConsumer<Value : Event, Event>(
    override val lifecycle: RStaLifecycle,
    defaultValue: Value,
    private val skipSameValue: Boolean,
    private val assignValueImmediately: Boolean,
    private val assignValueWhenFinished: Boolean,
    private val handler: (Dispatcher<Value>) -> Unit  //TODO: remove handler reference when lifecycle is finished
): RStaGenericValue<Value, Event> {

    interface Dispatcher<T> {
        val prevValue: T
        val valueAtTimeOfChange: T
        fun dispatch()
    }

    private class DispatcherImpl<Value : Event, Event>(
        private val listeners: RStaListenersRegistry<Event>,
        override val prevValue: Value,
        override val valueAtTimeOfChange: Value
    ) : Dispatcher<Value> {

        var dispatched: Boolean = false
            private set

        override fun dispatch() {
            if (dispatched) {
                return
            }

            dispatched = true

            listeners.enqueueEvent(valueAtTimeOfChange)
        }
    }

    override fun checkGeneration(): Long {
        return valueGeneration
    }

    override var value: Value = defaultValue
        private set

    fun set(value: Value) {
        if (lifecycle.finished && !assignValueWhenFinished) {
            return
        }

        if (skipSameValue && value == this.value) {
            return
        }

        val dispatcher = DispatcherImpl(listeners, this.value, value)

        if (assignValueImmediately) {
            valueGeneration++
            this.value = value
        }

        if (consumeInProgress) {
            consumeQueue.add(dispatcher)
        } else {
            consumeInProgress = true
            handleItem(dispatcher)

            while (consumeQueue.isNotEmpty()) {
                handleItem(consumeQueue.removeFirst())
            }

            consumeInProgress = false
        }
    }

    override fun listen(
        invoke: RStaValueEventSource.Invoke,
        lifecycle: RStaLifecycle,
        listener: (eventData: Event) -> Unit
    ) {
        listeners.add(invoke, value, lifecycle, listener)
    }

    override fun asEventSource(): RStaEventSource<Event> {
        return listeners.asEventSource()
    }

    private val listeners = RStaListenersRegistry<Event>(lifecycle)
    private var valueGeneration: Long = 0
    private var consumeInProgress: Boolean = false
    private val consumeQueue: MutableList<Dispatcher<Value>> = mutableListOf()

    private fun handleItem(dispatcher: Dispatcher<Value>) {
        if (!lifecycle.finished) {
            if (!assignValueImmediately) {
                valueGeneration++
                value = dispatcher.valueAtTimeOfChange
            }

            handler(dispatcher)
            dispatcher.dispatch()
        } else {
            if (!assignValueImmediately && assignValueWhenFinished) {
                valueGeneration++
                value = dispatcher.valueAtTimeOfChange
            }
        }
    }
}