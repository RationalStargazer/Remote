package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.RStaEventSource
import net.rationalstargazer.events.RStaListener
import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher
import net.rationalstargazer.events.value.RStaGenericValue

/**
 * Represents continuous lifecycle which is the simplest variant of `lifecycle` (see [RStaLifecycleScope] for details).
 *
 * Continuous lifecycle (or just "lifecycle" as it is the most common one) represents that the `thing` is active
 * (has been providing useful result) all the time since the creation of the `thing` and until the `lifecycle` will be finished.
 *
 * When the `lifecycle` has been finished the `thing` is supposed to automatically release all the resources it had been keeping.
 * After that the `thing` remains in non-functional state, prepared to be garbage-collected.
 */
interface RStaLifecycle : RStaLifecycleScope

/**
 * Base class for continuous ([RStaLifecycle]) and suspendable ([RStaSuspendableLifecycle]) lifecycles.
 *
 * Concept of `lifecycle` is used with event sources ([RStaEventSource]), observable values (see [RStaGenericValue]),
 * and other classes to automatically free resources when `lifecycle` is [finished].
 * For example [RStaEventSource] and [RStaGenericValue] automatically removes all listeners after the end of theirs lifecycle.
 *
 * Also `lifecycles` serve as holders of a [coordinator] queue which main role is to enqueue events to execute them sequentially one after another
 * (see [RStaEventsQueueDispatcher] for details about sequence of events)
 *
 * Normally all lifecycles that belong to the same thread share one `coordinator`.
 * It is supposed that different threads should have their own coordinators.
 * Current implementation of `coordinator` doesn't have any thread-switching (or thread-synchronizing) logic.
 * The use of lifecycles with `coordinator` bound to the different thread
 * (other than the one the `lifecycle` is supposed to be used) is highly discouraged.
 * Current implementations of event sources and observable values are supposed to work on a single thread.
 *
 * When lifecycle's life has ended the lifecycle calls `BeforeFinish` listeners,
 * then sets `finished` to `true` and calls `Finished` listeners (at the "same time", see the explanation in all details below).
 *
 * Finishing process in all details:
 *
 * First, the lifecycle makes calls to all `BeforeFinish` listeners (including the ones that were possibly added during the calls).
 *
 * Then it builds the list of active `Finished` listeners (listeners whose `lifecycle.finished == false`).
 *
 * After that `finished` property will become `true`.
 *
 * Then the list of `Finished` listeners will be called.
 *
 * (This sequence means you can use `this` lifecycle as a `Finished` listener's lifecycle and the listener will be called because `lifecycle.finished` check was made before `finished` became `true`.)
 *
 * Then if there are unhandled `BeforeFinish` or `Finished` listeners
 * (that were added with the parameter `callIfAlreadyFinished == true` during the call of `Finished` listeners)
 * `BeforeFinish` listeners will be called first (including those were freshly added during the calls),
 * then `Finished` listeners will be called next.
 * The execution of unhandled listeners will be repeated until no unhandled listeners will be left.
 *
 * Then [consumed] will be set to `true`.
 */
interface RStaLifecycleScope : RStaLifecycleMarker {
	
	/**
	 * Used by event sources to enqueue events.
	 *
	 * Normally all lifecycles that belong to the same thread share one `coordinator`.
	 * It is supposed that different threads should have their own coordinators.
	 * Current implementation of `coordinator` doesn't have any thread-switching (or thread-synchronizing) logic.
	 * The use of lifecycles with `coordinator` bound to the different thread
	 * (other than the one the `lifecycle` is supposed to be used) is highly discouraged.
	 */
	val coordinator: RStaEventsQueueDispatcher
	
	/**
	 * Indicates whether the lifecycle is finished.
	 *
	 * `finished` becomes `true` after the list of `Finished` listeners to be called is built,
	 * but before actually call them.
	 *
	 * It means `finished` will be `false` when `BeforeFinish` listeners will be handled
	 * (except for the listeners that were called because of `callIfAlreadyFinished`),
	 * and `true` when `Finished` listeners will be handled.
	 */
	override val finished: Boolean
	
	/**
	 * Indicates whether the lifecycle is finished, and all `Finished` listeners are executed.
	 * When `consumed == true` the lifecycle is not and will not keep any listeners,
	 * and will not call any listeners by its own (except for when `callIfAlreadyFinished == true` (see [listenFinished])).
	 * When `consumed` is `true` `finished` is always `true`. When `finished` is `false` `consumed` is always `false`.
	 */
	val consumed: Boolean
	
	/**
	 * See overloaded variant for details
	 */
	fun listenBeforeFinish(callIfAlreadyFinished: Boolean, listener: RStaListener<Unit>) {
		listenBeforeFinish(callIfAlreadyFinished, listener.lifecycleScope, listener::notify)
	}
	
	/**
	 * Adds the `listener` that will be called during the lifecycle's finishing process before the lifecycle will be considered `finished`.
	 * First, all listeners will be executed, then [finished] property will become `true`.
	 * (See [RStaLifecycleScope] for detailed explanation of the finishing process)
	 *
	 * @param callIfAlreadyFinished If `true` the `listener` will be called (enqueued as usually) even if the lifecycle is already finished
	 * If `false` the `listener` will not be called (completely ignored) if the lifecycle is already finished.
	 * If `false` and the listener is in the finishing stage but `finished` property is still `false`
	 * (for example the lifecycle is waiting for the execution of all remaining `BeforeFinish` listeners)
	 * the `listener` will be called.
	 *
	 * @param listenerLifecycle lifecycle of the [listener].
	 * When `listenerLifecycle.finished` property will become `true` the `listener` will be considered "removed" from the listeners,
	 * and the reference to `listener` will be removed shortly after that.
	 * `listenerLifecycle.finished` check is done immediately before the call of 'listener',
	 * if 'listenerLifecycle.finished == true` no call will be made, except for when [callIfAlreadyFinished] is `true`.
	 * If `callIfAlreadyFinished == true` the `listener` will be called regardless of `listenerLifecycle.finished`.
	 *
	 * You can use `this` lifecycle as a `listenerLifecycle` parameter if you want to listen the lifecycle forever.
	 */
	fun listenBeforeFinish(
		callIfAlreadyFinished: Boolean,
		listenerLifecycle: RStaLifecycle,
		listener: (Unit) -> Unit
	)
	
	/**
	 * See overloaded variant for details
	 */
	fun listenFinished(callIfAlreadyFinished: Boolean, listener: RStaListener<Unit>) {
		listenFinished(callIfAlreadyFinished, listener.lifecycleScope, listener::notify)
	}
	
	/**
	 * Adds the `listener` as a callback that will be called soon after the lifecycle will be finished
	 * ([finished] is `true`).
	 * (See [RStaLifecycleScope] for detailed explanation of the finishing process)
	 *
	 * @param callIfAlreadyFinished If `true` the `listener` will be called (enqueued as usually) even if the lifecycle is already finished
	 * If `false` and [finished] is `true` the `listener` will not be called.
	 *
	 * @param listenerLifecycle lifecycle of the [listener].
	 * When `listenerLifecycle.finished` property will become `true` the `listener` will be considered "removed" from the listeners,
	 * and the reference to `listener` will be removed shortly after that.
	 * `listenerLifecycle.finished` check is done just before `this` lifecycle's `finished` property will be set to `true`,
	 * if 'listenerLifecycle.finished == true` no call will be made, except for when [callIfAlreadyFinished] is `true`.
	 * If `callIfAlreadyFinished == true` the `listener` will be called regardless of `listenerLifecycle.finished`.
	 *
	 * You can use `this` lifecycle as a `listenerLifecycle` parameter if you want to listen the lifecycle forever
	 * (the `listener` will be called even though technically it will be made after the lifecycle has finished).
	 */
	fun listenFinished(
		callIfAlreadyFinished: Boolean,
		listenerLifecycle: RStaLifecycle,
		listener: (Unit) -> Unit
	)
	
	/**
	 * Internal method. Lifecycles use it to notify each other about their state when it is necessary for their work.
	 */
	fun watch(lifecycle: RStaLifecycleScope)
}

