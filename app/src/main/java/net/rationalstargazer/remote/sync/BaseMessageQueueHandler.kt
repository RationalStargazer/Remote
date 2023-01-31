package net.rationalstargazer.remote.sync

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.rationalstargazer.simpleevent.SimpleDataSource
import net.rationalstargazer.simpleevent.SimpleDynamicDataRawImpl
import kotlin.coroutines.CoroutineContext

typealias QueuedMessages<Message> = SimpleDataSource<EnumeratedMessageQueue<Message>>
typealias MessagesAcceptor<Message> = (List<IdContainer<Message>>) -> Unit

abstract class BaseMessageQueueHandlerImpl<Message>(
    lifecycle: Any,  //TODO: lifecycle currently ignored
    queueContext: CoroutineContext
) : BaseMessageQueueHandler<Message> {
    private val _messages = SimpleDynamicDataRawImpl<EnumeratedMessageQueue<Message>>(EnumeratedMessageQueue(null, emptyList()))

    final override val messages: SimpleDataSource<EnumeratedMessageQueue<Message>> = _messages

    override fun add(message: Message): Id {
        val newMessage = IdContainer(ids.newId(), message)
        _messages.value = _messages.value.copy(waiting = _messages.value.waiting + newMessage)
        channel.trySend(Unit)
        return newMessage.id
    }

    override fun remove(messageId: Id) {
        val indexToRemove = _messages.value.waiting.indexOfFirst { it.id == messageId }
        if (indexToRemove >= 0) {
            val nextWaitingList = _messages.value.waiting.toMutableList()
                .also {
                    it.removeAt(indexToRemove)
                }

            _messages.value = _messages.value.copy(waiting = nextWaitingList)
            channel.trySend(Unit)
        }
    }

    override fun replaceAll(messages: List<Message>) {
        val nextList = messages.map { IdContainer(ids.newId(), it) }
        _messages.value = _messages.value.copy(waiting = nextList)
        channel.trySend(Unit)
    }

    private val ids = Id.Factory.create()

    private val channel = Channel<Unit>(Channel.CONFLATED)

    protected open suspend fun startHandling() {
        while(messages.value.waiting.isNotEmpty()) {
            val current = handleBeforeNextMessage()

            //TODO: still a bit messy because stub event system is used.
            //check(current != null || waitingMessages.value.isEmpty(), "handleBeforeNextMessage() returned null while queue is not empty")

            if (current == null) {
                return
            }

            //inProgress = current
            handleMessage(current)

            //inProgress = null
            handleMessageFinished(current)
        }
    }

    //TODO: still a bit messy about what exactly should be return result and how to work with dynamic properties
    protected open suspend fun handleBeforeNextMessage(): IdContainer<Message>? {
        val nextMessage = messages.value.waiting.firstOrNull()
        _messages.value = EnumeratedMessageQueue(nextMessage, messages.value.waiting.drop(1))
        return nextMessage
    }

    protected abstract suspend fun handleMessage(message: IdContainer<Message>)

    protected abstract suspend fun handleMessageFinished(message: IdContainer<Message>)

    init {
        //TODO: replace GlobalScope to the real one
        GlobalScope.launch(queueContext) {
            for (channelItem in channel) {
                startHandling()
            }
        }
    }
}