package net.rationalstargazer.events

object RStaEventsQueueDispatcherFactory {

    /**
     * Creates new [RStaEventsQueueDispatcher] based on fresh (`queueHandler.inited == false`) `queueHandler`.
     * The call does [RStaQueueGenericHandler.init] with the values required for `RStaEventsQueueDispatcher`.
     * It means you can't use the same `queueHandler` for multiple RStaEventsQueueDispatcher-s.
     * @return null if `queueHandler.inited` is already true
     */
    fun createEventsQueue(queueHandler: RStaQueueGenericHandler): RStaEventsQueueDispatcher? {
        if (queueHandler.inited) {
            return null
        }

        return EventsQueueDispatcherImpl(queueHandler)
    }
}


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

interface RStaEventsQueueDispatcher {

    /**
     * Adds `block` to the queue (schedules for the execution in the future). Blocks of the queue are executed sequentially
     * one after another. After the execution of the block if next block is in the queue its execution will be started
     * immediately after the current block, until no blocks will be left in the queue.
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
     * Adds `block` to the queue (schedules for the execution in the future). Blocks of the queue are executed sequentially
     * one after another. After the execution of the block if next block is in the queue its execution will be started
     * immediately after the current block, until no blocks will be left in the queue.
     */
    fun enqueue(block: () -> Unit) {
        enqueue(block, null)
    }
}

private class EventsQueueDispatcherImpl(
    private val queueHandler: RStaQueueGenericHandler
) : RStaEventsQueueDispatcher {

    override fun enqueue(block: () -> Unit, afterHandled: (() -> Unit)?) {
        if (afterHandled != null) {
            callbacks.add(afterHandled)
        }

        queueHandler.post(block)
    }

    private val callbacks: MutableList<() -> Unit> = mutableListOf()

    private fun startCallbacks() {
        val f = callbacks.removeLastOrNull()
        if (f != null) {
            queueHandler.post(f)
        }
    }

    init {
        queueHandler.init(this::startCallbacks)
    }
}