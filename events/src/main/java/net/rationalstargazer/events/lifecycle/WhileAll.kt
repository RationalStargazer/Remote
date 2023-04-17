package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

/**
 * Returns the lifecycle which is an intersection of given `lifecycles`.
 * When any of the `lifecycles` will become `finished` the result lifecycle will be finished.
 * Technically the result lifecycle can be finished at the same time or slightly before any of `lifecycles` will become `finished`.
 */
fun RStaLifecycles.whileAll(coordinator: RStaEventsQueueDispatcher, lifecycles: List<RStaLifecycle>): RStaLifecycle {
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