package net.rationalstargazer.remote.sync

import net.rationalstargazer.events.lifecycle.LifecycleBasedSimpleCoroutineDispatcher
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.value.RStaValue
import net.rationalstargazer.events.value.RStaValueDispatcher
import net.rationalstargazer.logic.BaseMessageQueueHandlerImpl

class BaseWritableRemoteComplexDataSourceImpl<StateData, Key, Value, Command>(
    private val lifecycle: RStaLifecycle,
    private val coroutineDispatcher: LifecycleBasedSimpleCoroutineDispatcher,
    private val local: Repository.WriteAccess<Key, Value>,
    private val startNow: Boolean,
    initialData: StateData,
    initialCommands: List<RemoteQueueHandler.SyncCommand<Key, Command>>,

    private val stateReducer: (
        state: RemoteQueueHandler.State<StateData, Key, Command>,
        commands: RemoteQueueHandlerCommands<Key, Command>
    ) -> RemoteQueueHandler.State<StateData, Key, Command>,

    private val localValueReducer: (
        key: Key,
        initialValue: Value?,
        state: RemoteQueueHandler.State<StateData, Key, Command>
    ) -> Value?,

    private val handler: suspend (
        state: RemoteQueueHandler.State<StateData, Key, Command>,
        read: Repository.ReadAccess<Key, Value>,
        write: suspend (
            suspend (Repository.Writer<Key, Value>) -> RemoteQueueHandler.State<StateData, Key, Command>
        ) -> Unit
    ) -> Unit,
): BaseWritableRemoteComplexDataSource<Key, Value, Command> {

    private val ids = Id.Factory.create()

    private val _state = RStaValueDispatcher<RemoteQueueHandler.State<StateData, Key, Command>>(
        lifecycle,
        RemoteQueueHandler.State(
            initialData,
            initialCommands.map { IdContainer(ids.newId(), it) }
        )
    )

    val state: RStaValue<RemoteQueueHandler.State<StateData, Key, Command>> = _state

    private val _waiting = RStaValueDispatcher<RemoteQueueHandlerCommands<Key, Command>>(lifecycle, emptyList())

    val waiting: RStaValue<RemoteQueueHandlerCommands<Key, Command>> = _waiting

    override fun ensureSynced(key: Key, conditions: SyncConditions): Id {
        val id = ids.newId()

        if (lifecycle.finished || coroutineDispatcher.lifecycle.finished) {
            return id
        }

        val command = IdContainer(id, RemoteQueueHandler.SyncCommand.Receive(key, conditions))
        val item = RemoteQueueHandler.QueueCommand.Add(command)
        _waiting.value = _waiting.value + item
        commandsQueue.add(Unit)
        return command.id
    }

    override suspend fun read(key: Key): Value? {
        val initial = local.read(key)

        val reduced = stateReducer(_state.value, _waiting.value)
        val result = localValueReducer(key, initial, reduced)
        return result
    }

    override fun write(key: Key, command: Command): Id {
        val id = ids.newId()

        if (lifecycle.finished || coroutineDispatcher.lifecycle.finished) {
            return id
        }

        val syncCommand = IdContainer(id, RemoteQueueHandler.SyncCommand.Send(key, command))
        val item = RemoteQueueHandler.QueueCommand.Add(syncCommand)
        _waiting.value = _waiting.value + item
        commandsQueue.add(Unit)
        return syncCommand.id
    }

    override fun cancelCommand(commandId: Id) {
        if (lifecycle.finished || coroutineDispatcher.lifecycle.finished) {
            return
        }

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

    private val commandsQueue = BaseMessageQueueHandlerImpl<Unit>(coroutineDispatcher, this::handleCommands)

    private suspend fun handleCommands(@Suppress("UNUSED_PARAMETER") any: Unit) {
        while (_waiting.value.isNotEmpty() || _state.value.queue.isNotEmpty()) {
            val reduced = if (_waiting.value.isEmpty()) {
                _state.value
            } else {
                val state = stateReducer(_state.value, _waiting.value)
                _waiting.value = emptyList()
                _state.value = state
                state
            }

            if (reduced.queue.isEmpty()) {
                continue
            }

            handler(reduced, local.readOnly, this::writer)
        }
    }

    private suspend fun writer(
        block: suspend (Repository.Writer<Key, Value>) -> RemoteQueueHandler.State<StateData, Key, Command>
    ) {
        local.access {
            val state = block(it)
            _state.value = state
        }
    }

    init {
        if (startNow) {
            start()
        }
    }
}