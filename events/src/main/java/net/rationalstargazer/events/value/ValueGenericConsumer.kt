package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListenersRegistry
import net.rationalstargazer.events.lifecycle.RStaLifecycle

class RStaValueGenericConsumer<Value>(
    override val lifecycle: RStaLifecycle,
    defaultValue: Value,
    private val skipSameValue: Boolean,
    private val assignValueImmediately: Boolean,
    private val assignValueIfFinished: Boolean,
    private var handler: ((ChangeData<Value>) -> Unit)? = null
) : RStaValue<Value> {

    data class ChangeData<T>(
        val value: T,
        val prevValue: T
    )

    override fun checkValueVersion(): Long {
        return valueVersion
    }

    override var value: Value = defaultValue
        private set

    fun set(value: Value) {
        if (lifecycle.finished && !assignValueIfFinished) {
            return
        }

        if (skipSameValue && value == this.value) {
            return
        }

        val data = ChangeData(this.value, value)

        if (assignValueImmediately) {
            valueVersion++
            this.value = value
        }

        if (consumeInProgress) {
            consumeQueue.add(data)
        } else {
            consumeInProgress = true
            handleItem(data)

            while (consumeQueue.isNotEmpty()) {
                handleItem(consumeQueue.removeFirst())
            }

            consumeInProgress = false
        }
    }

    override fun listen(
        invoke: RStaListenerInvoke,
        lifecycle: RStaLifecycle,
        listener: (eventData: Value) -> Unit
    ) {
        listeners.add(invoke, value, lifecycle, listener)
    }

    override fun asEventSource(): RStaEventSource<Value> {
        return listeners.asEventSource()
    }

    private val listeners = RStaListenersRegistry<Value>(lifecycle)
    private var valueVersion: Long = 0
    private var consumeInProgress: Boolean = false
    private val consumeQueue: MutableList<ChangeData<Value>> = mutableListOf()

    private fun handleItem(data: ChangeData<Value>) {
        if (!lifecycle.finished) {
            if (!assignValueImmediately) {
                valueVersion++
                value = data.value
            }

            handler?.invoke(data)
            listeners.enqueueEvent(data.value)
        } else {
            if (!assignValueImmediately && assignValueIfFinished) {
                valueVersion++
                value = data.value
            }
        }
    }

    init {
        if (handler != null) {
            lifecycle.listenFinished(true, lifecycle) {
                handler = null
            }
        }
    }
}