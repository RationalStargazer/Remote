package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle

class RStaValueDispatcher<T> private constructor(
    private val handler: RStaValueGenericConsumer<T>
) : RStaVariable<T>, RStaValue<T> by handler {

    constructor(
        lifecycle: RStaLifecycle,
        defaultValue: T,
        handler: (() -> Unit)? = null
    ) : this(
        RStaValueGenericConsumer(
            lifecycle,
            defaultValue,
            skipSameValue = true,
            assignValueImmediately = true,
            assignValueIfFinished = true,
            handler = handler?.let { h ->
                fun (any: RStaValueGenericConsumer.ChangeData<T>) {
                    h()
                }
            }
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