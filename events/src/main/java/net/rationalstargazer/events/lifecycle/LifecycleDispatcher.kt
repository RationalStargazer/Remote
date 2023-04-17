package net.rationalstargazer.events.lifecycle

import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher

internal typealias LifecycleDispatcher = RStaLifecycleDispatcher

/**
 * Implementation of [RStaControlledLifecycle].
 */
class RStaLifecycleDispatcher(override val coordinator: RStaEventsQueueDispatcher) : RStaControlledLifecycle {

    class Finished(override val coordinator: RStaEventsQueueDispatcher) : RStaControlledLifecycle {

        override val finished: Boolean = true

        override val consumed: Boolean = true

        override fun listenBeforeFinish(
            callIfAlreadyFinished: Boolean,
            listenerLifecycle: RStaLifecycle,
            listener: (Unit) -> Unit
        ) {
            if (callIfAlreadyFinished) {
                beforeFinishRegistry.add(this, listener)  // lifecycle = this because lifecycle doesn't matter now
                coordinator.enqueue(this::handleTail)
            }
        }

        override fun listenFinished(
            callIfAlreadyFinished: Boolean,
            listenerLifecycle: RStaLifecycle,
            listener: (Unit) -> Unit
        ) {
            if (callIfAlreadyFinished) {
                finishedRegistry.add(this, listener)  // lifecycle = this because lifecycle doesn't matter now
                coordinator.enqueue(this::handleTail)
            }
        }

        override fun watch(lifecycle: RStaLifecycleScope) {
            // do nothing
        }

        override fun close() {
            // do nothing
        }

        private fun handleTail() {
            val beforeFinishedListeners = beforeFinishRegistry.allListeners()
            if (beforeFinishedListeners.isNotEmpty()) {
                beforeFinishRegistry.clearMainAndOthers()
                coordinator.enqueue(beforeFinishedListeners, this::handleTail)
            } else {
                val finalListeners = finishedRegistry.allListeners()
                if (finalListeners.isNotEmpty()) {
                    finishedRegistry.clearMainAndOthers()
                    coordinator.enqueue(finalListeners, this::handleTail)
                }
            }
        }

        private val beforeFinishRegistry = ManualRegistry<Unit>(this)
        private val finishedRegistry = ManualRegistry<Unit>(this)
    }

    var closeCalled: Boolean = false
        private set

    override var finished: Boolean = false
        private set

    override var consumed: Boolean = false
        private set

    override fun listenBeforeFinish(
        callIfAlreadyFinished: Boolean,
        listenerLifecycle: RStaLifecycle,
        listener: (Unit) -> Unit
    ) {
        if (finished) {
            if (callIfAlreadyFinished) {
                beforeFinishRegistry.add(this, listener)  // lifecycle = this because lifecycle doesn't matter now
                coordinator.enqueue(this::handleTail)
            }

            return
        }

        val new = connectedLifecycles.add(listenerLifecycle)
        if (new) {
            listenerLifecycle.watch(this)
        }

        beforeFinishRegistry.add(listenerLifecycle, listener)
    }

    override fun listenFinished(
        callIfAlreadyFinished: Boolean,
        listenerLifecycle: RStaLifecycle,
        listener: (Unit) -> Unit
    ) {
        if (finished) {
            if (callIfAlreadyFinished) {
                finishedRegistry.add(this, listener)  // lifecycle = this because lifecycle doesn't matter now
                coordinator.enqueue(this::handleTail)
            }

            return
        }

        val new = connectedLifecycles.add(listenerLifecycle)
        if (new) {
            listenerLifecycle.watch(this)
        }

        finishedRegistry.add(listenerLifecycle, listener)
    }

    override fun close() {
        startClose(null)
    }

    // override fun close(onConsumed: () -> Unit) {
    //     startClose(onConsumed)
    // }

    override fun watch(lifecycle: RStaLifecycleScope) {
        if (consumed) {
            return
        }

        if (lifecycle == this) {
            return
        }

        if (!lifecycle.consumed) {
            connectedLifecycles.add(lifecycle)
        } else {
            if (closeCalled) {
                // all references will be cleared anyway after closing procedure will be finished
                return
            }

            connectedLifecycles.remove(lifecycle)
            beforeFinishRegistry.clear(lifecycle)
            finishedRegistry.clear(lifecycle)
        }
    }

    private fun startClose(onConsumed: (() -> Unit)?) {
        if (closeCalled) {
            if (onConsumed != null) {
                //TODO: handle the case:
                // 1. several calls of `close(onConsumed)` from different places, handlers should be added to list and dispatched after the close
                // 2. subsequent calls of close should enqueue onConsumed
            }

            return
        }

        closeCalled = true

        beforeFinishRegistry.otherLifecycles().forEach {
            if (it.finished) {
                beforeFinishRegistry.clear(it)
            }
        }

        finishedRegistry.otherLifecycles().forEach {
            if (it.finished) {
                finishedRegistry.clear(it)
            }
        }

        coordinator.enqueue(this::handleTail, onConsumed)
    }

    private fun handleTail() {
        val beforeFinishedListeners = beforeFinishRegistry.allListeners()
        if (beforeFinishedListeners.isNotEmpty()) {
            beforeFinishRegistry.clearMainAndOthers()
            coordinator.enqueue(beforeFinishedListeners, this::handleTail)
        } else {
            finished = true

            val finalListeners = finishedRegistry.allListeners()
            if (finalListeners.isNotEmpty()) {
                finishedRegistry.clearMainAndOthers()
                coordinator.enqueue(finalListeners, this::handleTail)
            } else {
                if (!consumed) {
                    consumed = true
                    beforeFinishRegistry.clearMainAndOthers()
                    finishedRegistry.clearMainAndOthers()
                    val lifecycles = connectedLifecycles.toSet()
                    connectedLifecycles.clear()
                    lifecycles.forEach {
                        it.watch(this)
                    }
                }
            }
        }
    }

    private val beforeFinishRegistry = ManualRegistry<Unit>(this)
    private val finishedRegistry = ManualRegistry<Unit>(this)
    private val connectedLifecycles = mutableSetOf<RStaLifecycleScope>()

    private class ManualRegistry<T>(private val mainLifecycle: RStaLifecycleScope) {

        fun add(listenerLifecycle: RStaLifecycleScope, listenerFunction: (T) -> Unit) {
            if (listenerLifecycle == mainLifecycle) {
                mainListeners.add(listenerFunction)
                return
            }

            val list = otherListeners[listenerLifecycle]
                ?: mutableListOf<(T) -> Unit>()
                    .also {
                        otherListeners[listenerLifecycle] = it
                    }

            list.add(listenerFunction)
        }

        fun clearMainAndOthers() {
            mainListeners.clear()
            val others = otherListeners.keys.toList()
            others.forEach {
                clear(it)
            }
        }

        fun clear(lifecycle: RStaLifecycleScope) {
            if (lifecycle == mainLifecycle) {
                mainListeners.clear()
                return
            }

            val list = otherListeners.remove(lifecycle)
            list?.clear()
        }

        fun allListeners(): List<(T) -> Unit> {
            val list = mainListeners.toMutableList()
            otherListeners.values.forEach {
                list.addAll(it)
            }

            return list
        }

        fun otherLifecycles(): List<RStaLifecycleScope> {
            return otherListeners.keys.toList()
        }

        fun mainPlusOtherNotFinished(): List<(T) -> Unit> {
            val list = mainListeners.toMutableList()
            otherListeners.forEach {
                if (!it.key.finished) {
                    list.addAll(it.value)
                }
            }

            return list
        }

        private val mainListeners: MutableList<(T) -> Unit> = mutableListOf()
        private val otherListeners: MutableMap<RStaLifecycleScope, MutableList<(T) -> Unit>> = mutableMapOf()
    }
}

private fun RStaEventsQueueDispatcher.enqueue(
    listeners: List<(Unit) -> Unit>,
    afterHandled: (() -> Unit)? = null
) {
    enqueue(
        {
            listeners.forEach {
                it(Unit)
            }
        },

        afterHandled
    )
}