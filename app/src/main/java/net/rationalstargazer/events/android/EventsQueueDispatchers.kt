package net.rationalstargazer.events.android

import android.os.Handler
import net.rationalstargazer.events.RStaQueueGenericHandler

class RStaAndroidLooperHandler(androidHandler: Handler) : RStaQueueGenericHandler {

    private var handler: Handler? = androidHandler

    private val queue: MutableList<() -> Unit> = ArrayDeque()

    override fun init(queueHandledListener: () -> Unit): Boolean {
        if (inited || disposed) {
            return false
        }

        inited = true
        this.queueHandledListener = queueHandledListener
        return true
    }

    override var inited: Boolean = false
        private set

    override fun post(block: () -> Unit) {
        val h = handler

        if (h == null) {
            return
        }

        if (closed) {
            return
        }

        queue.add(block)
        enqueue()
    }

    private fun enqueue() {
        if (enqueued) {
            return
        }

        enqueued = true
        handler?.post(this::handler)
    }

    private fun handler() {
        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            f()
        }

        enqueued = false
        queueHandledListener?.invoke()

        if (closed) {
            dispose()
        }
    }

    override fun closeAndCompleteRemaining() {
        closed = true
        if (!enqueued) {
            dispose()
        }
    }

    private var enqueued: Boolean = false
    private var closed: Boolean = false
    private var disposed: Boolean = false
    private var queueHandledListener: (() -> Unit)? = null

    private fun dispose() {
        if (disposed) {
            return
        }

        disposed = true
        queue.clear()
        queueHandledListener = null
        handler = null
    }
}

