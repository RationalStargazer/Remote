package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.value.RStaValue

/**
 * Suspendable lifecycle ([RStaSuspendableLifecycle]) which represents the state of a view layer.
 * [RStaSuspendableLifecycle.active] is `true` means view is at least in a `Started` state (false otherwise).
 * In current implementation there is no differentiation between `Started` and `Resumed` state.
 */
interface RStaViewLifecycle : RStaLifecycle

/**
 * TODO: draft, not implemented
 * Most common example of suspendable lifecycle is a fragment's lifecycle ([RStaViewLifecycle]).
 * Suspendable lifecycle is the one that can be temporarily "activated" or "deactivated" multiple times,
 * before completely "finished" at the end.
 */
interface RStaSuspendableLifecycle : RStaLifecycleScope {
	
	/**
	 * The same lifecycle but represented as "continuous lifecycle" ([RStaLifecycle]).
	 * Conceptually it means "the time when the lifecycle could possibly be active" ("all the time before the finish").
	 */
	val scope: RStaLifecycle
	
	/**
	 * Indicates if the lifecycle is active (for example view's lifecycle is at least in `Started` state).
	 */
	val active: RStaValue<Boolean>
}