package net.rationalstargazer.remote.sync

import net.rationalstargazer.events.Lifecycle

abstract class RemoteQueueHandlerImpl<Message>(
    lifecycle: Lifecycle,  //TODO: change to something like CoroutineLifecycle
    queueContext: CoroutineContext
) : BaseMessageQueueHandler<Message> {

    private val _commands = mutableListOf<Command<Message>>()

    val commands: ImmutableList<Command<Message>>
        get() {
            return _commands.toImmutable()
        }

    //TODO: it is wrong, switch to VariableDispatcher (SignalValue version) because all values are essential
    private val _messages = ValueDispatcher<EnumeratedMessageQueue<Message>>(
        lifecycle,
        EnumeratedMessageQueue(null, emptyList())
    )

    val messages: Value<EnumeratedMessageQueue<Message>> = _messages

    final override fun add(message: Message): Id {
        val newMessage = IdContainer(ids.newId(), message)
        _commands.add(Command.Add(newMessage))
        channel.trySend(Unit)
        return newMessage.id
    }

    final override fun remove(messageId: Id) {
        _commands.add(Command.Remove(messageId))
        channel.trySend(Unit)
    }

    final override fun replaceAll(messages: List<Message>) {
        _commands.add(Command.ReplaceAll(messages.toImmutable()))
        channel.trySend(Unit)
    }

    private val channel = Channel<Unit>(Channel.CONFLATED)

    protected open suspend fun startHandling() {
        while(_commands.isNotEmpty() || _messages.value.waiting.isNotEmpty()) {
            //TODO
            //check(_messages.value.active == null, "active is not empty before starting next item")

            var waiting = _messages.value.waiting.considerImmutable()
            while (true) {
                val next = _commands.removeFirstOrNull()
                if (next == null) {
                    break
                }

                waiting = when (next) {
                    is Command.Add -> handleAdd(waiting, next.message)
                    is Command.Remove -> handleRemove(waiting, next.messageId)
                    is Command.ReplaceAll -> handleReplaceAll(waiting, next.messages)
                }
            }


            val state = handleBeforeMessage(waiting)

            val active = state.active
            //check(active != null || state.waiting.isEmpty(), "state.active is null while state.waiting is not empty")

            _messages.value = state

            if (active == null) {
                continue
            }

            val nextMessages = handleMessage(state)

            val nextState = EnumeratedMessageQueue(null, nextMessages)

            _messages.value = nextState

            handleMessageFinished(nextState, active)
        }
    }

    protected open suspend fun handleAdd(
        state: ImmutableList<IdContainer<Message>>,
        message: IdContainer<Message>
    ): ImmutableList<IdContainer<Message>> {
        return (state + message).considerImmutable()
    }

    protected open suspend fun handleRemove(
        state: ImmutableList<IdContainer<Message>>,
        messageId: Id
    ): ImmutableList<IdContainer<Message>> {
        val i = state.indexOfFirst { it.id == messageId }
        if (i >= 0) {
            return state.toMutableList()
                .also {
                    it.removeAt(i)
                }
                .considerImmutable()
        }

        return state
    }

    protected open suspend fun handleReplaceAll(
        state: ImmutableList<IdContainer<Message>>,
        messages: ImmutableList<Message>
    ): ImmutableList<IdContainer<Message>> {
        val nextState = messages.map { IdContainer(ids.newId(), it) }
        return nextState.considerImmutable()
    }

    protected open suspend fun handleBeforeMessage(messages: ImmutableList<IdContainer<Message>>): EnumeratedMessageQueue<Message> {
        val nextMessage = messages.firstOrNull()
        if (nextMessage == null) {
            return EnumeratedMessageQueue(null, emptyList())
        }

        val nextState = EnumeratedMessageQueue(nextMessage, messages.drop(1))
        return nextState
    }

    protected abstract suspend fun handleMessage(state: EnumeratedMessageQueue<Message>): ImmutableList<IdContainer<Message>>

    protected open suspend fun handleMessageFinished(state: EnumeratedMessageQueue<Message>, handledMessage: IdContainer<Message>) {
        // empty
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