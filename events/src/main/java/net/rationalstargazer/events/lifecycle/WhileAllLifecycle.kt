package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

/**
 * Returns the lifecycle which is an intersection of given `lifecycles`.
 * When any of the `lifecycles` will become `finished` the result lifecycle will be finished.
 * Technically the result lifecycle can be finished at the same time or slightly before any of `lifecycles` will become `finished`.
 */
@Suppress("FunctionName")
fun RStaWhileAllLifecycle(coordinator: RStaEventsQueueDispatcher, vararg lifecycles: RStaLifecycle): RStaLifecycle {
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

@Suppress("FunctionName")
fun RStaWhileAllLifecycle(coordinator: RStaEventsQueueDispatcher, lifecycles: List<RStaLifecycle>): RStaLifecycle {
    return RStaWhileAllLifecycle(coordinator, *Array(lifecycles.size) { lifecycles[it] })
}

@Suppress("FunctionName")
fun RStaWhileAllLifecycle(lifecycle0: RStaLifecycle, lifecycle1: RStaLifecycle): RStaLifecycle {
    val coordinator = lifecycle0.coordinator
    if (coordinator != lifecycle1.coordinator) {
        throw IllegalArgumentException("different coordinators")
    }

    return RStaWhileAllLifecycle(coordinator, lifecycle0, lifecycle1)
}

@Suppress("FunctionName")
fun RStaWhileAllLifecycle(lifecycle0: RStaLifecycle, lifecycle1: RStaLifecycle, lifecycle2: RStaLifecycle): RStaLifecycle {
    val coordinator = lifecycle0.coordinator
    if (coordinator != lifecycle1.coordinator || coordinator != lifecycle2.coordinator) {
        throw IllegalArgumentException("different coordinators")
    }

    return RStaWhileAllLifecycle(lifecycle0.coordinator, lifecycle0, lifecycle1, lifecycle2)
}