package net.rationalstargazer.events

import net.rationalstargazer.*
import java.util.*

interface EventSource<out T> : HasLifecycle {

    fun listen(listener: Listener<T>)

    fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit) {
        listen(StdListener(lifecycle, listener))
    }
}

/**
 * "Value" is essentially an [EventSource] that holding (or keeping) a value that can be changed over time.
 */
interface Value<out T> : EventSource<T> {

    val value: T

    //fun listenInvalidate(lifecycle: Lifecycle, listener: () -> Unit)
}

interface ValueCustomConsumer<T> {
    val oldValue: T
    val nextValue: T
    fun set(nextValue: T)
    fun dispatch()
    fun setAndDispatch(nextValue: T)
}

/**
 * "Variable" is writable [Value]
 */
interface Variable<T> : Value<T> {

    override var value: T
}

interface HasLifecycle {
    val lifecycle: Lifecycle
}

// interface LifecycleBased {
//
//     val finished: Boolean
//
//     fun listen(listener: Listener<Unit>)
//
//     fun listen(listenerLifecycle: Lifecycle, listener: () -> Unit) {
//         listen(StdListener(listenerLifecycle) { listener() })
//     }
// }

interface Lifecycle {

    val coordinator: EventsQueueDispatcher

    val finished: Boolean

    fun listen(listener: Listener<Unit>)

    fun listen(listenerLifecycle: Lifecycle, listener: () -> Unit) {
        listen(StdListener(listenerLifecycle) { listener() })
    }
}

interface SuspendableLifecycle {

    //val continuousLifecycle??

    val active: Boolean

    // fun listenStateChange(listener: Listener<Boolean>)
    //
    // fun listenStateChange(listenerLifecycle: Lifecycle, listener: (value: Boolean) -> Unit) {
    //     listenStateChange(StdListener(listenerLifecycle, listener))
    // }
}

interface ViewLifecycle : SuspendableLifecycle

interface ControlledLifecycle : LifecycleBased {

    // not final api
    //fun closeImmediately()
}

interface Listener<in T> {

    val lifecycle: Lifecycle

    fun notify(value: T)
}

private interface EventsCoordinator {

    fun <T> enqueue(dispatchList: List<Listener<T>>, nextValue: T)

    fun enqueueDirectCall(block: () -> Unit)
}

/**
 * Events will always be delivered to main (UI) thread. Multithreading is not supported.
 */
private fun events(updateLogic: EventsCoordinator.() -> Unit) {
    EventsGlobalCoordinator.dispatch(updateLogic)
}

/**
 * Currently not part of public API (but can be in the future)
 *
 * dispatchImmediately is designed for use from main (UI) thread. Multithreading is not allowed. Details: events can be dispatched at the wrong thread when using in different threads simultaneously. [LifecycleCommon] implementation depends on using dispatch() on UI thread, so you can't use it from a background thread without introducing critical bugs. Note: if you need events at a background thread you can improve implementation of dispatch() to use different EventsGlobalCoordinator for different threads, this way you will not need events synchronization and overly-complicated switching threads logic during dispatch (but events in different threads will be treated as completely unrelated, so there will be no ordering (sorting) for events from different threads).
 */
//private fun dispatchImmediately(updateLogic: EventsCoordinator.() -> Unit) {
//    ImmediateEventsGlobalCoordinator.dispatch(updateLogic)
//}

class ListenersRegistry<T>(
    lifecycle: Lifecycle
) {

    private val registryLifecycle: Lifecycle = lifecycle

    fun add(listener: Listener<T>) {
        add(listener.lifecycle, listener::notify)
    }

    fun add(listenerLifecycle: Lifecycle, listenerFunction: (T) -> Unit) {
        if (listenerLifecycle.finished) {
            return
        }

        if (listenerLifecycle == registryLifecycle) {
            commonItems.add(listenerFunction)
            return
        }

        val registry = otherLifecyclesItems[listenerLifecycle]
            ?: ListenersRegistry<T>(listenerLifecycle).also { otherLifecyclesItems[listenerLifecycle] = it }

        registry.add(listenerLifecycle, listenerFunction)
    }

    fun enqueueEvent(eventValue: T) {
        if (registryLifecycle.finished) {
            return
        }

        registryLifecycle.coordinator.enqueue(registryLifecycle, listeners(), eventValue)
    }

    private fun listeners(): ImmutableList<(T) -> Unit> {
        val list = commonItems.toMutableList()
        otherLifecyclesItems.values.forEach {
            list.addAll(it.listeners())
        }

        return list.considerImmutable()
    }

    private val commonItems: Deque<(T) -> Unit> = LinkedList()
    private val otherLifecyclesItems: MutableMap<Lifecycle, ListenersRegistry<T>> = mutableMapOf()

    private fun close() {
        commonItems.clear()
        otherLifecyclesItems.clear()
    }

    init {
        if (!registryLifecycle.finished) {
            registryLifecycle.listen(registryLifecycle, ::close)
        }
    }
}

private class StdListener<T>(
    override val lifecycle: Lifecycle,
    private val listenerFunction: (T) -> Unit) : Listener<T> {

    override fun notify(value: T) {
        if (!lifecycle.finished) {
            listenerFunction(value)
        }
    }
}

class EventDispatcher<T>(override val lifecycle: Lifecycle) : EventSource<T> {

    override fun listen(listener: Listener<T>) {
        listeners.add(listener)
    }

    override fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit) {
        listeners.add(lifecycle, listener)
    }

    fun enqueueEvent(eventValue: T) {
        listeners.enqueueEvent(eventValue)
    }

    private val listeners = ListenersRegistry<T>(lifecycle)
}

class ValueDispatcher<T> (override val lifecycle: Lifecycle, defaultValue: T) : Value<T> {

    override var value: T = defaultValue
        private set

    override fun listen(listener: Listener<T>) {
        changeDispatcher.listen(listener)
    }

    override fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit) {
        changeDispatcher.listen(lifecycle, listener)
    }

    fun setValueWithEvent(nextValue: T) {
        if (nextValue == value) {
            return
        }

        value = nextValue
        changeDispatcher.enqueueEvent(nextValue)
    }

    private val changeDispatcher =  EventDispatcher<T>(lifecycle)
}

class ValueCustomHandler<T>(
    override val lifecycle: Lifecycle,
    defaultValue: T,
    private val handler: Handler<T>
): Value<T> {

    interface Handler<T> {
        val dispatchSameValue: Boolean
        fun valueMapper(value: T): T
        fun beforeDispatch(oldValue: T, nextValue: T)
        fun afterDispatch(oldValue: T, nextValue: T)
    }

    open class CustomHandler<T>(override val dispatchSameValue: Boolean) : Handler<T> {
        override fun valueMapper(value: T): T {
            return value
        }

        override fun beforeDispatch(oldValue: T, nextValue: T) {
            // empty
        }

        override fun afterDispatch(oldValue: T, nextValue: T) {
            // empty
        }
    }

    override var value: T = defaultValue
        private set

    fun handle(value: T) {
        if (lifecycle.finished) {
            return
        }

        val nextValue = handler.valueMapper(value)

        if (!handler.dispatchSameValue && nextValue == this.value) {
            return
        }

        val oldValue = this.value
        this.value = nextValue
        handler.beforeDispatch(oldValue, nextValue)
        listeners.enqueueEvent(nextValue)
        handler.afterDispatch(oldValue, nextValue)
    }

    override fun listen(listener: Listener<T>) {
        listeners.add(listener)
    }

    override fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit) {
        listeners.add(lifecycle, listener)
    }

    private val listeners = ListenersRegistry<T>(lifecycle)
}

class VariableDispatcher<T> (
    override val lifecycle: Lifecycle,
    defaultValue: T,
    changeHandler: (ValueChangeDispatcher, oldValue: T, nextValue: T) -> Unit
): Variable<T> {

    class ValueChangeDispatcher(private val dispatchLogic: () -> Unit) {

        var enqueued: Boolean = false
            private set

        fun enqueueDispatch() {
            if (enqueued) {
                return
            }

            enqueued = true
            dispatchLogic()
        }
    }

    override var value: T get() = mValue
        set(value) {
            setValueWithEvent(value)
        }

    private var mValue: T = defaultValue

    private val changeDispatcher = EventDispatcher<T>(lifecycle)

    override fun listen(listener: Listener<T>) {
        changeDispatcher.listen(listener)
    }

    fun setValueWithEvent(nextValue: T) {
        if (nextValue == value) {
            return
        }

        val oldValue = mValue
        mValue = nextValue

        val vd = ValueChangeDispatcher {
            changeDispatcher.enqueueEvent(nextValue)
        }

        changeHandler?.invoke(vd, oldValue, nextValue)

        if (!vd.enqueued) {
            vd.enqueueDispatch()
        }
    }

    private var changeHandler: ((ValueChangeDispatcher, oldValue: T, nextValue: T) -> Unit)? = changeHandler

    init {
        lifecycle.listenCloseImmediately(Lifecycle.Forever, true) {
            this.changeHandler = null
        }
    }
}

class ValueTransform<T, R>(parentLifecycle: Lifecycle, source: Value<T>, transformHandler: (T) -> R) : Value<R> {

    override val value: R get() {
        return sourceData?.let { it.transformer(it.source.value) } ?: lastDispatchedValue
    }

    override fun listen(listener: Listener<R>) {
        changeDispatcher.listen(listener)
    }

    override val lifecycle: Lifecycle = LifecyclesIntersection(parentLifecycle, source.lifecycle).also {
        it.listenCloseImmediately(Lifecycle.Forever, true) {
            lastDispatchedValue = value
            sourceData = null
        }
    }

    private val changeDispatcher = EventDispatcher<R>(lifecycle)

    private var sourceData: Source<T, R>? = Source(source, transformHandler)

    private var lastDispatchedValue: R = value

    init {
        source.listen(lifecycle) {
            sourceData?.let { // when listener is calling -> lifecycle.closed == false -> sourceData != null
                val next = it.transformer(it.source.value)
                if (next != lastDispatchedValue) {
                    lastDispatchedValue = next
                    changeDispatcher.enqueueEvent(next)
                }
            }
        }
    }

    private class Source<T, R>(val source: Value<T>, val transformer: (T) -> R)
}

abstract class ConditionalLifecycle : Lifecycle {

    final override val closed: Boolean get() {
        val v = closeCondition?.invoke()

        return when (v) {
            null -> true

            true -> {
                closeCondition = null
                true
            }

            else -> false
        }
    }

    final override var finished: Boolean = false
        private set

    /**
     * closeCondition Contracts:
     * - Returns appropriate closed state at the time of calling
     * - No state changes, no losing of control
     * - Implementation doesn't use: this.closed
     */
    protected abstract var closeCondition: (() -> Boolean)?

    override fun listenCloseImmediately(callIfAlreadyClosed: Boolean, listener: Listener<Boolean>) {
        if (closed) {
            if (callIfAlreadyClosed) {
                listener.notify(true)
            }
        } else {
            immediateListeners.add(listener)
        }
    }

    final override fun listenCloseStreamlined(callIfAlreadyClosed: Boolean, listener: Listener<Boolean>) {
        if (closed) {
            if (callIfAlreadyClosed && !listener.finished) {
                events {
                    enqueueDirectCall {
                        listener.notify(true)
                    }
                }
            }
        } else {
            streamlinedListeners.add(listener)
        }
    }

    protected fun initiateClose() {
        if (closeHasCalled) return
        closeHasCalled = true
        closeCondition = null

        streamlinedListeners.getNotFinishedListeners().let { list ->
            if (list.isNotEmpty()) {
                events {
                    enqueueDirectCall {
                        list.forEach { it.notify(false) }
                        finished = true
                        untilFinishedLifecycle?.closeImmediately()
                    }
                }
            }
        }

        immediateListeners.getNotFinishedListeners().let { sourceList ->
            val copy = sourceList.toList()
            copy.forEach { it.notify(false) }
        }
    }

    protected val untilFinished: Lifecycle get() {
        return untilFinishedLifecycle ?: run {
            val d = LifecycleDispatcher()
            if (finished) d.closeImmediately()

            untilFinishedLifecycle = d
            d
        }
    }

    private var untilFinishedLifecycle: LifecycleDispatcher? = null

    private var closeHasCalled = false

    @Suppress("LeakingThis")
    private val immediateListeners = ListenersRegistry<Boolean>(this, true)

    @Suppress("LeakingThis")
    private val streamlinedListeners = ListenersRegistry<Boolean>(this, true)
}

class LifecycleDispatcher : ConditionalLifecycle(), ControlledLifecycle {

    override var closeCondition: (() -> Boolean)? = { false }

    override fun closeImmediately() {
        initiateClose()
    }
}

class SuspendableLifecycleDispatcher : ConditionalLifecycle(), SuspendableLifecycle {

    override var active: Boolean = false
        private set

    fun setActiveWithEvent(value: Boolean) {
        if (closed) return
        if (value == active) return

        active = value
        stateChangeDispatcher.enqueueEvent(
            if (value) SuspendableLifecycle.State.Active else SuspendableLifecycle.State.Inactive
        )
    }

    fun closeImmediately() {
        if (closed) {
            return
        }

        if (active) {
            setActiveWithEvent(false)
        }

        stateChangeDispatcher.enqueueEvent(SuspendableLifecycle.State.Closed)
        initiateClose()
    }

    override fun listenStateChange(listener: Listener<SuspendableLifecycle.State>) {
        stateChangeDispatcher.listen(listener)
    }

    override var closeCondition: (() -> Boolean)? = { false }

    private val stateChangeDispatcher = EventDispatcher<SuspendableLifecycle.State>(this)
}

class NestedLifecycle(outerLifecycle: Lifecycle) : ConditionalLifecycle(), ControlledLifecycle {

    override var closeCondition: (() -> Boolean)? = {
        outerLifecycle.closed
    }

    override fun closeImmediately() {
        initiateClose()
    }

    init {
        outerLifecycle.listenCloseImmediately(untilFinished, true) {
            closeImmediately()
        }
    }
}

class LifecyclesIntersection(lifecycleA: Lifecycle, lifecycleB: Lifecycle) : ConditionalLifecycle() {

    override var closeCondition: (() -> Boolean)? = {
        lifecycleA.closed || lifecycleB.closed
    }

    init {
        lifecycleA.listenCloseImmediately(untilFinished, true) {
            initiateClose()
        }

        lifecycleB.listenCloseImmediately(untilFinished, true) {
            initiateClose()
        }
    }
}

object ThreadQueueControl {

    interface Handler {
        fun post(block: () -> Unit)
    }

    val handler: Handler get() = mHandler ?: throw IllegalStateException("handler was not initialized")

    private var mHandler: Handler? = null

    fun setUp(handler: Handler) {
        if (mHandler != null) return
        mHandler = handler
    }
}

interface EventsQueueDispatcher {
    fun <T> enqueue(lifecycle: Lifecycle, dispatchList: ImmutableList<(T) -> Unit>, valueToDispatch: T)
}

private object EventsGlobalCoordinator {

    class Coordinator : EventsCoordinator {

        override fun <T> enqueue(dispatchList: List<Listener<T>>, nextValue: T) {
            if (dispatchList.isNotEmpty()) {
                doEnqueue(this) {
                    dispatchList.forEach { it.notify(nextValue) }
                }
            }
        }

        override fun enqueueDirectCall(block: () -> Unit) {
            doEnqueue(this, block)
        }
    }

    fun dispatch(updateLogic: EventsCoordinator.() -> Unit) {
        val currentCoordinator = coordinator
        if (currentCoordinator != null) {
            updateLogic(currentCoordinator)
        } else {
            val c = Coordinator()
            coordinator = c

            updateLogic(c)

            val dispatchQueue = eventsQueue
            coordinator = null
            eventsQueue = newEventsQueue()

            ThreadQueueControl.handler.post { dispatchQueue.forEach { it() } }
        }
    }

    private var coordinator: EventsCoordinator? = null

    private var eventsQueue = newEventsQueue()

    private fun newEventsQueue(): Queue<() -> Unit> {
        return ArrayDeque<() -> Unit>()
    }

    private fun doEnqueue(coordinator: EventsCoordinator, dispatchCall: () -> Unit) {
        if (this.coordinator != coordinator) {
            logger.throwable(AppLogger.ThrowOption.DebugThrow, IllegalStateException("EventsCoordinator.customCall() was used from invalid instance, event queue can be broken now (do not keep EventsCoordinator for future uses, instead call dispatch() again)"))
        }

        eventsQueue.add(dispatchCall)
    }
}

//private object ImmediateEventsGlobalCoordinator {
//
//    class Coordinator : EventsCoordinator {
//
//        override fun event(source: EventDispatcher<Unit>) {
//            source.dispatch(this, Unit)
//        }
//
//        override fun <T> next(source: EventDispatcher<T>, value: T) {
//            source.dispatch(this, value)
//        }
//
//        override fun customCall(dispatchCall: () -> Unit) {
//            doEnqueue(this, dispatchCall)
//        }
//    }
//
//    val inProgress: Boolean get() = coordinator != null
//
//    fun dispatch(updateLogic: EventsCoordinator.() -> Unit) {
//        val currentCoordinator = coordinator
//        if (currentCoordinator != null) {
//            updateLogic(currentCoordinator)
//        } else {
//            val c = Coordinator()
//            coordinator = c
//
//            updateLogic(c)
//
//            while (!eventsQueue.isEmpty()) {
//                val event = eventsQueue.remove()
//                event()
//            }
//
//            coordinator = null
//        }
//    }
//
//    private var coordinator: EventsCoordinator? = null
//
//    private val eventsQueue = ConcurrentLinkedQueue<() -> Unit>()
//
//    private fun doEnqueue(coordinator: EventsCoordinator, dispatchCall: () -> Unit) {
//        if (this.coordinator != coordinator) {
//            logger.throwable(AppLogger.ThrowOption.DebugThrow, IllegalStateException("EventsCoordinator.customCall() was used from invalid instance, event queue can be broken now (do not keep EventsCoordinator for future uses, instead call dispatch() again)"))
//        }
//
//        eventsQueue.add(dispatchCall)
//    }
//}