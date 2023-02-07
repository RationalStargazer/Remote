package net.rationalstargazer.events

import net.rationalstargazer.ImmutableList
import net.rationalstargazer.considerImmutable
import net.rationalstargazer.immutableListOf

interface EventSource<out T> : HasLifecycle {

    fun listen(listener: Listener<T>) {
        listen(listener.lifecycle, listener::notify)
    }

    fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit)
}

interface Value<out T> : EventSource<Unit> {
    fun checkGeneration(): Long
    val value: T
}

/**
 * "Value" is essentially an [EventSource] that holding (or keeping) a value that can be changed over time.
 */
interface SignalValue<out T> : EventSource<T> {

    val value: T

    //fun listenInvalidate(lifecycle: Lifecycle, listener: () -> Unit)
}

/**
 * "Variable" is writable [SignalValue]
 */
interface Variable<T> : SignalValue<T> {

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

    val consumed: Boolean

    fun listenBeforeFinish(callIfAlreadyFinished: Boolean, listener: Listener<Unit>) {
        listenBeforeFinish(callIfAlreadyFinished, listener.lifecycle, listener::notify)
    }

    fun listenBeforeFinish(callIfAlreadyFinished: Boolean, listenerLifecycle: Lifecycle, listenerFunction: (Unit) -> Unit)

    fun listenFinished(callIfAlreadyFinished: Boolean, listener: Listener<Unit>) {
        listenFinished(callIfAlreadyFinished, listener.lifecycle, listener::notify)
    }

    fun listenFinished(callIfAlreadyFinished: Boolean, listenerLifecycle: Lifecycle, listenerFunction: (Unit) -> Unit)

    fun watch(lifecycle: Lifecycle)
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

interface ControlledLifecycle : Lifecycle {

    fun close()

    fun close(onConsumed: () -> Unit)
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

        registryLifecycle.coordinator.enqueue(listeners(), eventValue)
    }

    private fun listeners(): ImmutableList<(T) -> Unit> {
        val list = commonItems.toMutableList()
        otherLifecyclesItems.values.forEach {
            list.addAll(it.listeners())
        }

        return list.considerImmutable()
    }

    private val commonItems: ArrayDeque<(T) -> Unit> = ArrayDeque()
    private val otherLifecyclesItems: MutableMap<Lifecycle, ListenersRegistry<T>> = mutableMapOf()

    init {
        registryLifecycle.listenFinished(true, registryLifecycle) {
            commonItems.clear()
            otherLifecyclesItems.clear()
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

    override fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit) {
        listeners.add(lifecycle, listener)
    }

    fun enqueueEvent(eventValue: T) {
        listeners.enqueueEvent(eventValue)
    }

    private val listeners = ListenersRegistry<T>(lifecycle)
}

class FunctionalValue<T>(
    override val lifecycle: Lifecycle,
    valueGeneration: () -> Long,
    function: () -> T
) : Value<T> {

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

    override val value: T
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

    fun notifyChanged() {
        listeners.enqueueEvent(Unit)
    }

    override fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: Unit) -> Unit) {
        listeners.add(lifecycle, listener)
    }

    private var function: (() -> T)? = function
    private var generation: (() -> Long)? = valueGeneration

    private val listeners = ListenersRegistry<Unit>(lifecycle)
    private var cache: Cache<T>? = null
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

class ChainGenericItem<T>(private val base: FunctionalValue<T>) : Value<T> by base {

    constructor(
        lifecycle: Lifecycle,
        upstreamChangeSource: EventSource<Any>,
        valueGeneration: () -> Long,
        function: () -> T
    ) : this(
        FunctionalValue(combineLifecycles(lifecycle, upstreamChangeSource.lifecycle), valueGeneration, function)
    ) {
        upstreamChangeSource.listen(lifecycle, this::notifyChanged)
    }

    constructor(
        lifecycle: Lifecycle,
        upstreamSources: List<EventSource<Any>>,
        valueGeneration: () -> Long,
        function: () -> T
    ) : this(
        FunctionalValue(combineLifecycles(lifecycle, combineSources(upstreamSources)), valueGeneration, function)
    ) {
        upstreamSources.forEach {
            it.listen(lifecycle, this::notifyChanged)
        }
    }

    companion object {
        private fun combineLifecycles(vararg lifecycles: Lifecycle): Lifecycle {

        }

        private fun combineSources(sources: List<EventSource<Any>>): Lifecycle {

        }
    }

    private fun notifyChanged(any: Any) {
        base.notifyChanged()
    }
}

class ValueMapper<V, V0> private constructor(
    private val base: ChainGenericItem<V>
) : Value<V> by base {

    constructor(
        lifecycle: Lifecycle,
        source: Value<V0>,
        mapper: (V0) -> V
    ) : this(
        ChainGenericItem(
            lifecycle,
            source,
            source::checkGeneration,
        ) {
            mapper(source.value)
        }
    )
}

class VariableDispatcher<T> private constructor(
    private val handler: ValueGenericConsumer<T>
) : SignalValue<T> by handler, Variable<T> {

    constructor(
        lifecycle: Lifecycle,
        defaultValue: T,
        handler: (ValueGenericConsumer.Dispatcher<T>) -> Unit
    ) : this(
        ValueGenericConsumer(
            lifecycle,
            defaultValue,
            skipSameValue = true,
            assignValueImmediately = true,
            assignValueWhenFinished = true,
            handler = handler
        )
    )

    override var value: T
        get() {
            return handler.value
        }

        set(value) {
            handler.set(value)
        }
}

class ValueGenericConsumer<T>(
    override val lifecycle: Lifecycle,
    defaultValue: T,
    private val skipSameValue: Boolean,
    private val assignValueImmediately: Boolean,
    private val assignValueWhenFinished: Boolean,
    private val handler: (Dispatcher<T>) -> Unit  //TODO: remove handler reference when lifecycle is finished
): SignalValue<T> {

    interface Dispatcher<T> {
        val prevValue: T
        val valueAtTimeOfChange: T
        fun dispatch()
    }

    private class DispatcherImpl<T>(
        private val listeners: ListenersRegistry<T>,
        override val prevValue: T,
        override val valueAtTimeOfChange: T
    ) : Dispatcher<T> {

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

    override var value: T = defaultValue
        private set

    fun set(value: T) {
        if (lifecycle.finished && !assignValueWhenFinished) {
            return
        }

        if (skipSameValue && value == this.value) {
            return
        }

        val dispatcher = DispatcherImpl(listeners, this.value, value)

        if (assignValueImmediately) {
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

    override fun listen(lifecycle: Lifecycle, listener: (dataAtTimeOfEvent: T) -> Unit) {
        listeners.add(lifecycle, listener)
    }

    private val listeners = ListenersRegistry<T>(lifecycle)
    private var consumeInProgress: Boolean = false
    private val consumeQueue: MutableList<Dispatcher<T>> = mutableListOf()

    private fun handleItem(dispatcher: Dispatcher<T>) {
        if (!lifecycle.finished) {
            if (!assignValueImmediately) {
                value = dispatcher.valueAtTimeOfChange
            }

            handler(dispatcher)
            dispatcher.dispatch()
        } else {
            if (!assignValueImmediately && assignValueWhenFinished) {
                value = dispatcher.valueAtTimeOfChange
            }
        }
    }
}

class LifecycleDispatcher(override val coordinator: EventsQueueDispatcher) : ControlledLifecycle {

    var closeCalled: Boolean = false
        private set

    override var finished: Boolean = false
        private set

    override var consumed: Boolean = false
        private set

    override fun listenBeforeFinish(
        callIfAlreadyFinished: Boolean,
        listenerLifecycle: Lifecycle,
        listenerFunction: (Unit) -> Unit
    ) {
        if (finished) {
            if (callIfAlreadyFinished) {
                beforeFinishRegistry.add(this, listenerFunction)  // lifecycle = this because lifecycle doesn't matter now
                coordinator.enqueue(this::handleLateListeners, Unit)
            }

            return
        }

        val new = connectedLifecycles.add(listenerLifecycle)
        if (new) {
            listenerLifecycle.watch(this)
        }

        beforeFinishRegistry.add(listenerLifecycle, listenerFunction)
    }

    override fun listenFinished(
        callIfAlreadyFinished: Boolean,
        listenerLifecycle: Lifecycle,
        listenerFunction: (Unit) -> Unit
    ) {
        if (finished) {
            if (callIfAlreadyFinished) {
                finishedRegistry.add(this, listenerFunction)  // lifecycle = this because lifecycle doesn't matter now
                coordinator.enqueue(this::handleLateListeners, Unit)
            }

            return
        }

        val new = connectedLifecycles.add(listenerLifecycle)
        if (new) {
            listenerLifecycle.watch(this)
        }

        finishedRegistry.add(listenerLifecycle, listenerFunction)
    }

    override fun close() {
        startClose(null)
    }

    override fun close(onConsumed: () -> Unit) {
        startClose(onConsumed)
    }

    override fun watch(lifecycle: Lifecycle) {
        if (consumed) {
            return
        }

        if (lifecycle == this) {
            return
        }

        if (!lifecycle.consumed) {
            connectedLifecycles.add(lifecycle)
        } else {
            if (closeCalled) {
                // all references will be cleared anyway after closing procedure will be finished
                return
            }

            connectedLifecycles.remove(lifecycle)
            beforeFinishRegistry.clear(lifecycle)
            finishedRegistry.clear(lifecycle)
        }
    }

    private fun startClose(onConsumed: (() -> Unit)?) {
        if (closeCalled) {
            return
        }

        closeCalled = true

        beforeFinishRegistry.otherLifecycles().forEach {
            if (it.finished) {
                beforeFinishRegistry.clear(it)
            }
        }

        finishedRegistry.otherLifecycles().forEach {
            if (it.finished) {
                finishedRegistry.clear(it)
            }
        }

        handleTail(onConsumed)
    }

    private fun handleTail(onConsumed: (() -> Unit)?) {
        TODO("incorrect, should be recursively executed in single command")

        val beforeFinishedListeners = beforeFinishRegistry.allListeners()
        if (beforeFinishedListeners.isNotEmpty()) {
            beforeFinishRegistry.clearMainAndOthers()
            coordinator.enqueue(beforeFinishedListeners, Unit)
            coordinator.enqueue(immutableListOf({ handleTail(onConsumed) }), Unit)
        } else {
            finished = true

            val finalListeners = finishedRegistry.allListeners()
            if (finalListeners.isNotEmpty()) {
                finishedRegistry.clearMainAndOthers()
                coordinator.enqueue(finalListeners, Unit)
                coordinator.enqueue(immutableListOf({ handleTail(onConsumed) }), Unit)
            } else {
                if (!consumed) {
                    consumed = true
                    beforeFinishRegistry.clearMainAndOthers()
                    finishedRegistry.clearMainAndOthers()
                    val lifecycles = connectedLifecycles.toSet()
                    connectedLifecycles.clear()
                    lifecycles.forEach {
                        it.watch(this)
                    }

                    onConsumed?.invoke()
                }
            }
        }
    }

    private fun handleLateListeners(any: Unit) {
        handleTail(null)
    }

    private val beforeFinishRegistry = ManualRegistry<Unit>(this)
    private val finishedRegistry = ManualRegistry<Unit>(this)
    private val connectedLifecycles = mutableSetOf<Lifecycle>()

    private class ManualRegistry<T>(private val mainLifecycle: Lifecycle) {

        fun add(listenerLifecycle: Lifecycle, listenerFunction: (T) -> Unit) {
            if (listenerLifecycle == mainLifecycle) {
                mainListeners.add(listenerFunction)
                return
            }

            val list = otherListeners[listenerLifecycle]
                ?: mutableListOf<(T) -> Unit>()
                    .also {
                        otherListeners[listenerLifecycle] = it
                    }

            list.add(listenerFunction)
        }

        fun clearMainAndOthers() {
            mainListeners.clear()
            val others = otherListeners.keys.toList()
            others.forEach {
                clear(it)
            }
        }

        fun clear(lifecycle: Lifecycle) {
            if (lifecycle == mainLifecycle) {
                mainListeners.clear()
                return
            }

            val list = otherListeners.remove(lifecycle)
            list?.clear()
        }

        fun allListeners(): ImmutableList<(T) -> Unit> {
            val list = mainListeners.toMutableList()
            otherListeners.values.forEach {
                list.addAll(it)
            }

            return list.considerImmutable()
        }

        fun otherLifecycles(): ImmutableList<Lifecycle> {
            return otherListeners.keys.toList().considerImmutable()
        }

        fun mainPlusOtherNotFinished(): ImmutableList<(T) -> Unit> {
            val list = mainListeners.toMutableList()
            otherListeners.forEach {
                if (!it.key.finished) {
                    list.addAll(it.value)
                }
            }

            return list.considerImmutable()
        }

        private val mainListeners: ArrayDeque<(T) -> Unit> = ArrayDeque()
        private val otherListeners: MutableMap<Lifecycle, MutableList<(T) -> Unit>> = mutableMapOf()
    }
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

// class LifecyclesIntersection(vararg lifecycles: Lifecycle) : Lifecycle {
//     override val coordinator: EventsQueueDispatcher
//         get() = TODO("Not yet implemented")
//
//     override val finished: Boolean
//         get() = TODO("Not yet implemented")
//
//     override val consumed: Boolean
//         get() = TODO("Not yet implemented")
//
//     override fun listenBeforeFinish(
//         callIfAlreadyFinished: Boolean,
//         listenerLifecycle: Lifecycle,
//         listener: (Unit) -> Unit
//     ) {
//         TODO("Not yet implemented")
//     }
//
//     override fun listenFinished(
//         callIfAlreadyConsumed: Boolean,
//         listenerLifecycle: Lifecycle,
//         listener: (Unit) -> Unit
//     ) {
//         TODO("Not yet implemented")
//     }
// }

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
    fun <T> enqueue(block: (T) -> Unit, valueToDispatch: T)
    fun <T> enqueue(dispatchList: ImmutableList<(T) -> Unit>, valueToDispatch: T)
    fun <T> enqueue(dispatchList: ImmutableList<(T) -> Unit>, valueToDispatch: T, onDispatched: () -> Unit)
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