package net.rationalstargazer.remote.sync

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.rationalstargazer.ImmutableList
import net.rationalstargazer.events.Lifecycle
import net.rationalstargazer.events.RStaValue
import net.rationalstargazer.events.ValueDispatcher
import kotlin.coroutines.CoroutineContext

//typealias QueuedMessages<Message> = SimpleDataSource<EnumeratedMessageQueue<Message>>
//typealias MessagesConsumer<Message> = (List<IdContainer<Message>>) -> Unit

class BaseMessageQueueHandlerImpl<Message>(
    lifecycle: Lifecycle,  //TODO: change to something like CoroutineLifecycle
    queueContext: CoroutineContext,
    private val handler: suspend (Message) -> Unit
) : BaseMessageQueueHandler<Message> {

    //TODO: it is wrong, switch to VariableDispatcher (SignalValue version) because all values are essential
    private val _messages = ValueDispatcher<List<Message>>(lifecycle, emptyList())

    val messages: RStaValue<List<Message>> = _messages

    override fun add(message: Message) {
        _messages.value = _messages.value + message
        channel.trySend(Unit)
    }

    override fun removeAt(index: Int) {
        val messages = _messages.value
        if (index !in messages.indices) {
            //TODO: improve logging here
            return
        }

        _messages.value = messages.toMutableList().also { it.removeAt(index) }
    }

    override fun replaceAll(messages: ImmutableList<Message>) {
        _messages.value = messages
        channel.trySend(Unit)
    }

    private val channel = Channel<Unit>(Channel.CONFLATED)

    private suspend fun startHandling() {
        while(_messages.value.isNotEmpty()) {
            val message = _messages.value.first()
            _messages.value = _messages.value.drop(1)
            handler(message)
        }
    }

    init {
        //TODO: replace GlobalScope to the real one
        GlobalScope.launch(queueContext) {
            for (channelItem in channel) {
                startHandling()
            }
        }
    }
}