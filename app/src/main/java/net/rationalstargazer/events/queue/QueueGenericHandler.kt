package net.rationalstargazer.events.queue

interface RStaQueueGenericHandler {

    /**
     * One-time set up of additional parameters.
     * Subsequent calls do nothing.
     * The call on the closed instance (see [closeAndCompleteRemaining]) does nothing.
     * @param queueHandledListener Sets listener that is called after all queued items are executed.
     * @return true if inited with the supplied arguments, false otherwise (not the first call or the call after a close has happened (see [closeAndCompleteRemaining]))
     */
    fun init(queueHandledListener: (() -> Unit)): Boolean

    /**
     * True if [init] was called, false otherwise
     */
    val inited: Boolean

    /**
     * Adds `block` to the queue (schedules for the execution in the future).
     * Blocks of the queue are executed sequentially one after another.
     * The execution of the next block (if there is one) will be started immediately after the execution of the current one,
     * until no blocks are left in the queue.
     * The call on the closed instance (see [closeAndCompleteRemaining]) does nothing.
     */
    fun post(block: () -> Unit)

    /**
     * Marks the handler to be closed after it will finish all existing (at the moment of the call) queue items.
     * `queueHandledListener` (see [init]) will be set to null (reference is released) after the close will happen.
     * No items will be added to the queue after the call.
     * The items that were enqueued before the call will be executed normally.
     * Subsequent calls do nothing.
     */
    fun closeAndCompleteRemaining()
}