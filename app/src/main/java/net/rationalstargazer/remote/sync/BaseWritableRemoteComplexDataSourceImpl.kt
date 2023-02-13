package net.rationalstargazer.remote.sync

import net.rationalstargazer.ImmutableList
import net.rationalstargazer.events.Lifecycle
import net.rationalstargazer.events.RStaValue
import net.rationalstargazer.events.ValueDispatcher
import kotlin.coroutines.CoroutineContext

 class BaseWritableRemoteComplexDataSourceImpl<Key, Value, Command>(
    val lifecycle: Lifecycle,
    queueContext: CoroutineContext,
    private val local: LocalRepository.WriteAccess<Key, Value>,
    private val startNow: Boolean,
    initialCommands: ImmutableList<RemoteQueueHandler.SyncCommand<Key, Command>>,

    private val commandsReducer: (
        state: RemoteComplexDataSourceState<Key, Command>,
        waitingCommands: RemoteComplexDataSourceCommands<Key, Command>
    ) -> RemoteComplexDataSourceState<Key, Command>,

    private val localValueReducer: (
        key: Key,
        initialValue: Value?,
        commands: RemoteComplexDataSourceState<Key, Command>
    ) -> Value?,

    private val handler: suspend (
        state: RemoteComplexDataSourceState<Key, Command>,
        read: LocalRepository.ReadAccess<Key, Value>,
        write: suspend (suspend (LocalRepository.Writer<Key, Value>) -> RemoteComplexDataSourceState<Key, Command>) -> Unit
    ) -> Unit,
): BaseWritableRemoteComplexDataSource<Key, Value, Command> {

    //TODO: it is wrong, switch to VariableDispatcher (SignalValue version) because all values are essential
    private val _state = ValueDispatcher<RemoteComplexDataSourceState<Key, Command>>(lifecycle, mutableListOf())

    val state: RStaValue<RemoteComplexDataSourceState<Key, Command>> = _state

    //TODO: it is wrong, switch to VariableDispatcher (SignalValue version) because all values are essential
    private val _waiting = ValueDispatcher<RemoteComplexDataSourceCommands<Key, Command>>(lifecycle, emptyList())

    val waiting: RStaValue<RemoteComplexDataSourceCommands<Key, Command>> = _waiting

    override fun ensureSynced(key: Key, conditions: SyncConditions): Id {
        val command = IdContainer(ids.newId(), RemoteQueueHandler.SyncCommand.Receive(key, conditions))
        val item = RemoteQueueHandler.QueueCommand.Add(command)
        _waiting.value = _waiting.value + item
        commandsQueue.add(Unit)
        return command.id
    }

    override suspend fun read(key: Key): Value? {
        val initial = local.read(key)

        val reduced = commandsReducer(_state.value, _waiting.value)
        val result = localValueReducer(key, initial, reduced)
        return result
    }

    override fun write(key: Key, command: Command): Id {
        val syncCommand = IdContainer(ids.newId(), RemoteQueueHandler.SyncCommand.Send(key, command))
        val item = RemoteQueueHandler.QueueCommand.Add(syncCommand)
        _waiting.value = _waiting.value + item
        commandsQueue.add(Unit)
        return syncCommand.id
    }

    override fun cancelCommand(commandId: Id) {
        val item = RemoteQueueHandler.QueueCommand.Remove<Key>(commandId)
        _waiting.value = _waiting.value + item
        commandsQueue.add(Unit)
    }

    fun start() {
        if (active) {
            return
        }

        active = true

        commandsQueue.start()
    }

    fun pause() {
        if (!active) {
            return
        }

        active = false

        commandsQueue.pause()
    }

    private var active: Boolean = false

    private val ids = Id.Factory.create()

    private val commandsQueue = BaseMessageQueueHandlerImpl<Unit>(lifecycle, queueContext, this::handleCommands)

    private suspend fun handleCommands(any: Unit) {
        while (_waiting.value.isNotEmpty() || _state.value.isNotEmpty()) {
            val reduced = if (_waiting.value.isEmpty()) {
                _state.value
            } else {
                val state = commandsReducer(_state.value, _waiting.value)
                _waiting.value = emptyList()
                _state.value = state
                state
            }

            if (reduced.isEmpty()) {
                continue
            }

            handler(reduced, local.readOnlyAccess, this::writer)
        }
    }

    private suspend fun writer(
        block: suspend (LocalRepository.Writer<Key, Value>) -> RemoteComplexDataSourceState<Key, Command>
    ) {
        local.sole {
            val state = block(it)
            _state.value = state
        }
    }
}