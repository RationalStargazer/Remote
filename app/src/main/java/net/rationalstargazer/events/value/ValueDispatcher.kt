package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle

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