package net.rationalstargazer.logic

import kotlinx.coroutines.launch
import net.rationalstargazer.events.lifecycle.RStaLifecycleBasedSimpleCoroutineDispatcher
import net.rationalstargazer.types.RStaImmutableList

interface RStaBaseMessageQueueHandler<Message> {

    val messages: List<Message>

    fun add(message: Message)
    fun removeAt(index: Int)
    fun replaceAll(messages: RStaImmutableList<Message>)
    fun start()
    fun pause()
}

class RStaBaseMessageQueueHandlerImpl<Message> constructor(
    private val coroutineDispatcher: RStaLifecycleBasedSimpleCoroutineDispatcher,
    private val handler: suspend (Message) -> Unit
) : RStaBaseMessageQueueHandler<Message> {

    override var messages: List<Message> = emptyList()
        private set

    override fun add(message: Message) {
        messages = messages + message

        if (active) {
            startQueue()
        }
    }

    override fun removeAt(index: Int) {
        if (index !in messages.indices) {
            //TODO: improve logging here
            return
        }

        messages = messages.toMutableList().also { it.removeAt(index) }
    }

    override fun replaceAll(messages: RStaImmutableList<Message>) {
        this.messages = messages

        if (active) {
            startQueue()
        }
    }

    override fun start() {
        active = true
        startQueue()
    }

    override fun pause() {
        active = false
    }

    private var active: Boolean = true
    private var enqueued: Boolean = false

    private fun startQueue() {
        if (enqueued) {
            return
        }

        if (coroutineDispatcher.lifecycle.finished) {
            return
        }

        enqueued = true
        coroutineDispatcher.launchNonCancellable {
            while(active && messages.isNotEmpty()) {
                val message = messages.first()
                messages = messages.drop(1)
                handler(message)
            }

            enqueued = false
        }
    }
}