package net.rationalstargazer.events.queue

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