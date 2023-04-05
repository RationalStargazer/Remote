package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycleScope
import net.rationalstargazer.events.value.RStaValueEventSource

interface RStaListener<in T> {

    val lifecycleScope: RStaLifecycle

    fun notify(value: T)
}