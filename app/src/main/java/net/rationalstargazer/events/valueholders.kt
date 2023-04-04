package net.rationalstargazer.events

interface RStaValueEventSource<out T> : RStaHasLifecycle {

    enum class Invoke { YesNow, YesEnqueue, No }

    fun listen(invoke: Invoke, listener: RStaListener<T>) {
        listen(invoke, listener.lifecycleScope, listener::notify)
    }

    fun listen(invoke: Invoke, lifecycle: RStaLifecycle, listener: (eventData: T) -> Unit)

    fun asEventSource(): RStaEventSource<T>
}

typealias RStaValue<T> = RStaGenericValue<T, T>

typealias RStaValueSource<T> = RStaGenericValue<T, Any?>

/**
 * It is essentially an [RStaEventSource] that holding (or keeping) a value that can be changed over time.
 */
interface RStaGenericValue<out Value : Event, out Event> : RStaValueEventSource<Event> {
    fun checkGeneration(): Long
    val value: Value
}

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

class ChainGenericItem<out Value : Event, Event, SourceEvent>(
    private val base: FunctionalValue<Value, Event>
) : RStaGenericValue<Value, Event> by base {

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<SourceEvent>,
        changeHandler: (SourceEvent, emitter: (Event) -> Unit) -> Unit,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        lifecycle,
        upstreamChangeSource.asEventSource(),
        changeHandler,
        valueGeneration,
        function
    )

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaEventSource<SourceEvent>,
        changeHandler: (SourceEvent, emitter: (Event) -> Unit) -> Unit,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        FunctionalValue(
            RStaIntersectionLifecycle.get(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) { sourceEvent ->
            changeHandler(sourceEvent) { handledEvent ->
                base.notifyChanged(handledEvent)
            }
        }
    }

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<SourceEvent>,
        changeHandler: (SourceEvent) -> Event,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        lifecycle,
        upstreamChangeSource.asEventSource(),
        changeHandler,
        valueGeneration,
        function
    )

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaEventSource<SourceEvent>,
        changeHandler: (SourceEvent) -> Event,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        FunctionalValue(
            RStaIntersectionLifecycle.get(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) { sourceEvent ->
            val handledEvent = changeHandler(sourceEvent)
            base.notifyChanged(handledEvent)
        }
    }
}

class ChainLazyItem<out Value>(
    private val base: FunctionalValue<Value, Any?>
) : RStaGenericValue<Value, Any?> by base {

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<Any?>,
        changeHandler: () -> Boolean,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        lifecycle,
        upstreamChangeSource.asEventSource(),
        changeHandler,
        valueGeneration,
        function
    )

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaEventSource<Any?>,
        changeHandler: () -> Boolean,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        FunctionalValue(
            RStaIntersectionLifecycle.get(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) {
            val notifyDownstream = changeHandler()
            if (notifyDownstream) {
                base.notifyChanged(Unit)
            }
        }
    }

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaValueEventSource<Any?>,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        lifecycle,
        upstreamChangeSource.asEventSource(),
        valueGeneration,
        function
    )

    constructor(
        lifecycle: RStaLifecycle,
        upstreamChangeSource: RStaEventSource<Any?>,
        valueGeneration: () -> Long,
        function: () -> Value
    ) : this(
        FunctionalValue(
            RStaIntersectionLifecycle.get(lifecycle.coordinator, listOf(lifecycle, upstreamChangeSource.lifecycle)),
            valueGeneration,
            function
        )
    ) {
        upstreamChangeSource.listen(lifecycle) {
            base.notifyChanged(Unit)
        }
    }
}

class ValueMapper<out Value, in SourceValue> private constructor(
    private val base: ChainGenericItem<Value, Value, SourceValue>
) : RStaGenericValue<Value, Value> by base {

    constructor(
        lifecycle: RStaLifecycle,
        source: RStaGenericValue<SourceValue, SourceValue>,
        mapper: (SourceValue) -> Value
    ) : this(
        ChainGenericItem(
            lifecycle,
            source,
            mapper,
            source::checkGeneration,
        ) {
            mapper(source.value)
        }
    )
}

class ValueDispatcher<T> private constructor(
    private val handler: ValueGenericConsumer<T, T>
) : RStaGenericValue<T, T> by handler {

    constructor(
        lifecycle: RStaLifecycle,
        defaultValue: T,
        handler: (ValueGenericConsumer.Dispatcher<T>) -> Unit = {}
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