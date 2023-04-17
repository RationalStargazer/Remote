package net.rationalstargazer.events

import net.rationalstargazer.events.lifecycle.RStaLifecycle

/**
 * Experimental.
 */
interface RStaListener<in T> {

    val lifecycleScope: RStaLifecycle

    fun notify(value: T)
}