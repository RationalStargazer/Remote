package net.rationalstargazer.remote.sync

import net.rationalstargazer.remote.sync.BaseWritableRemoteComplexDataSource.SyncCommand

abstract class BaseWritableRemoteComplexDataSourceImpl<Key, Value, Command>(
    protected val data: LocalRepository.ReaderWriter<Key, Value>,
    initialCommands: QueuedMessages<SyncCommand<Key, Command>>,
    protected val commandsHandler: BaseMessageQueueHandler<SyncCommand<Key, Command>>,

    protected val localCommandsReducer: (
        key: Key,
        initialValue: Value?,
        commands: List<Pair<Key, Command>>
    ) -> Value?
) : BaseWritableRemoteComplexDataSource<Key, Value, Command> {

    override fun ensureSynced(key: Key, conditions: SyncConditions) {
        commandsHandler.add(SyncCommand.Receive(key, conditions))
    }

    override suspend fun read(key: Key): Value? {
        val initial = data.read(key)
        val result = localCommandsReducer(
            key,
            initial,
            commandsSource.value.activePlusWaiting
                .filterIsInstance<SyncCommand.Send<Key, Command>>()
                .map {
                    it.key to it.command
                }
        )

        return result
    }

    override fun write(key: Key, command: Command): Id {
        return commandsHandler.add(SyncCommand.Send(key, command))
    }

    override fun cancelCommand(commandId: Id) {
        commandsHandler.remove(commandId)
    }
}