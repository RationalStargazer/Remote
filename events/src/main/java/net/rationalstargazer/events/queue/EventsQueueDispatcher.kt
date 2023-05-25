package net.rationalstargazer.events.queue


/**
 * Provides the ability to enqueue `block` of code to be executed in near future.
 *
 * On Android you can use [RStaEventsQueueDispatcherFactory.createEventsQueue] to instantiate standard implementation.
 *
 * Enqueued blocks form a queue (first in first out).
 * Blocks are executed sequentially one after another.
 *
 * Used by event sources to enqueue events.
 *
 * This approach removes a possibility of interleaving of events making them more natural and easier to predict:
 *
 * - Scheduling events doesn't lead to an interruption of control flow (comparing to direct function call).
 *
 * - Events that were scheduled earlier will be handled earlier
 *
 * - All listeners of an event will be executed before the listeners of the next event will be started;
 *
 * It is supposed that different threads should use their own instances of `RStaEventsQueueDispatcher`.
 * Current implementation doesn't have any thread-switching (or thread-synchronizing) logic.
 * Calling methods of the instance from another thread (other than the one it is bound to) is highly discouraged.
 * Current implementations of event sources and observable values are supposed to work on a single thread.
 */
interface RStaEventsQueueDispatcher {

    /**
     * Adds `block` to the queue (schedules for the execution in the future).
     * Blocks of the queue are executed sequentially one after another.
     * The execution of the next block (if there is one) will be started immediately after the execution of the current one,
     * until no blocks are left in the queue.
     *
     * @param afterHandled The callback that will be called after this block was handled and queue is empty (no other blocks left).
     *
     * In case of multiple `enqueue` calls (with non-null `afterHandled`) before the queue will become empty:
     * calls of multiple `afterHandled` callbacks will be stacked (last in first out).
     * If `enqueue` was called during the execution of the callback, the execution of remaining callbacks will be postponed
     * until the queue will be empty again.
     * Then calling of the callbacks will be continued taking into account all new callbacks that were possibly added to the stack in the meantime.
     *
     * This concept allows to treat subsequent `enqueue` blocks as a chain of "reaction" to the previous blocks (and sometimes they are).
     * `afterHandled` callback of the block will be called after all "reaction" (all later blocks along with theirs 'afterHandled' callbacks) will be handled.
     * You can think about the queue almost in the same way as about a stack of function calls
     * (later `enqueue` blocks in the queue are like nested function calls).
     */
    fun enqueue(block: () -> Unit, afterHandled: (() -> Unit)?)

    /**
     * Adds `block` to the queue (schedules for the execution in the future).
     * Blocks of the queue are executed sequentially one after another.
     * The execution of the next block (if there is one) will be started immediately after the execution of the current one,
     * until no blocks are left in the queue.
     */
    fun enqueue(block: () -> Unit) {
        enqueue(block, null)
    }
}