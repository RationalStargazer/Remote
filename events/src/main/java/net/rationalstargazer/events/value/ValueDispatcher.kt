package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle

class RStaValueDispatcher<T> private constructor(
    private val handler: ValueGenericConsumer<T>
) : RStaValue<T> by handler {

    constructor(
        lifecycle: RStaLifecycle,
        defaultValue: T,
        handler: ((ValueGenericConsumer.ChangeData<T>) -> Unit)? = null
    ) : this(
        ValueGenericConsumer(
            lifecycle,
            defaultValue,
            skipSameValue = true,
            assignValueImmediately = true,
            assignValueIfFinished = true,
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