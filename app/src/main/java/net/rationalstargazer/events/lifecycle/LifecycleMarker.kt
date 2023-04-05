package net.rationalstargazer.events.lifecycle

/**
 * Simplified interface of `lifecycle` (see [RStaLifecycleScope]) which is much easier to implement by yourself.
 * A `lifecycle` but without an option to listen to it.
 * Suitable for cases when you want to just check `finished` property from time to time.
 */
interface RStaLifecycleMarker {
	val finished: Boolean
}