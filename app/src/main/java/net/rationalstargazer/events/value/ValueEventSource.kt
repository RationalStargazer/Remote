package net.rationalstargazer.events.value

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListener
import net.rationalstargazer.events.lifecycle.RStaLifecycle

interface RStaValueEventSource<out T> {

    enum class Invoke { YesNow, YesEnqueue, No }

    fun listen(invoke: Invoke, listener: RStaListener<T>) {
        listen(invoke, listener.lifecycleScope, listener::notify)
    }

    fun listen(invoke: Invoke, lifecycle: RStaLifecycle, listener: (eventData: T) -> Unit)

    fun asEventSource(): RStaEventSource<T>
}