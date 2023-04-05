package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

object RStaIntersectionLifecycle {

    fun get(coordinator: RStaEventsQueueDispatcher, lifecycles: List<RStaLifecycle>): RStaLifecycle {
        if (lifecycles.isEmpty() || lifecycles.any { it.finished }) {
            return RStaLifecycleDispatcher.Finished(coordinator)
        }

        val first = lifecycles[0]

        if (lifecycles.all { it == first }) {
            return first
        }

        val dispatcher = RStaLifecycleDispatcher(coordinator)
        lifecycles.forEach {
            it.listenBeforeFinish(true, dispatcher) {
                dispatcher.close()
            }
        }

        return dispatcher
    }
}