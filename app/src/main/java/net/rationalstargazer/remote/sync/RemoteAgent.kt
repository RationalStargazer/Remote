package net.rationalstargazer.remote.sync

import net.rationalstargazer.types.ImmutableList

@JvmInline
value class Id private constructor(val value: Int) {

    class Factory private constructor(firstIdValue: Int) {
        companion object {
            fun create(): Factory {
                return Factory(0)
            }
        }

        private var count: Int = firstIdValue

        fun newId(): Id {
            if (count == Int.MAX_VALUE) {
                //TODO: think about throw modules
                //TODO: log "operation id has exceeded Int.MAX_VALUE"
            }

            return Id(count++)
        }
    }
}

data class IdContainer<out T>(val id: Id, val value: T)

//typealias MutableRemoteQueueHandlerQueue<Key, Command> = MutableList<IdContainer<RemoteQueueHandler.SyncCommand<Key, Command>>>
typealias RemoteQueueHandlerQueue<Key, Command> = List<IdContainer<RemoteQueueHandler.SyncCommand<Key, Command>>>
typealias RemoteQueueHandlerCommands<Key, Command> = List<RemoteQueueHandler.QueueCommand<Key, Command>>

interface RemoteQueueHandler<Key, Value, Command> {

    data class State<Data, Key, Commands>(val data: Data, val queue: RemoteQueueHandlerQueue<Key, Commands>)

    sealed class SyncCommand<Key, out Command> {

        abstract val key: Key

        data class Receive<Key>(override val key: Key, val conditions: SyncConditions) : SyncCommand<Key, Nothing>()
        data class Send<Key, Command>(override val key: Key, val command: Command) : SyncCommand<Key, Command>()
    }

    sealed class QueueCommand<Key, out Command> {
        data class Add<Key, out Command>(
            val syncCommand: IdContainer<SyncCommand<Key, Command>>
        ) : QueueCommand<Key, Command>()

        data class Remove<Key>(val commandId: Id) : QueueCommand<Key, Nothing>()

        data class ReplaceAll<Key, out Command>(
            val commands: List<IdContainer<SyncCommand<Key, Command>>>
        ) : QueueCommand<Key, Command>()
    }

    fun addReceive(key: Key, conditions: SyncConditions): Id
    fun addSent(key: Key, command: Command): Id
    fun remove(id: Id)
    fun replaceAll(list: ImmutableList<SyncCommand<Key, Command>>)
}

sealed class SyncConditions {
    // object ExistsLocally : SyncConditions()

    object SinceStart : SyncConditions()
    data class InLast(val millisecs: Long) : SyncConditions()
}

interface BaseRemoteComplexDataSource<Key, Value> {

    fun ensureSynced(key: Key, conditions: SyncConditions): Id

    suspend fun read(key: Key): Value?
}

interface BaseWritableRemoteComplexDataSource<Key, Value, Command> : BaseRemoteComplexDataSource<Key, Value> {

    fun write(key: Key, command: Command): Id

    fun cancelCommand(commandId: Id)
}

interface Repository {

    interface ReadAccess<Key, Value> : Reader<Key, Value> {
        suspend fun access(block: suspend (Reader<Key, Value>) -> Unit)
    }

    interface WriteAccess<Key, Value> : ReaderWriter<Key, Value> {
        suspend fun access(block: suspend (ReaderWriter<Key, Value>) -> Unit)
        val readOnly: ReadAccess<Key, Value>
    }

    interface Reader<Key, Value> {
        suspend fun read(key: Key): Value?
    }

    interface Writer<Key, Value> {
        suspend fun write(key: Key, value: Value)
    }

    interface ReaderWriter<Key, Value> : Reader<Key, Value>, Writer<Key, Value>
}

// interface LocalListRepository {
//
//     interface ReadAccess<T> : Reader<T> {
//         suspend fun <R> sole(block: suspend (Reader<T>) -> R): R
//     }
//
//     interface WriteAccess<T> : ReaderWriter<T> {
//         suspend fun <R> sole(block: suspend (ReaderWriter<T>) -> R): R
//         val readOnlyAccess: ReadAccess<T>
//     }
//
//     interface Reader<T> : Repository.Reader<Int, T> {
//         val size: Int?
//
//         suspend fun findSize(): Int
//
//         suspend fun sublist(range: IntRange): List<T>
//
//         suspend fun getAll(): List<T>
//     }
//
//     interface Writer<T> : Repository.Writer<Int, T> {
//         fun add(value: T)
//
//         fun replaceAll(list: List<T>)
//     }
//
//     interface ReaderWriter<T> : Reader<T>, Writer<T>
// }

interface DirectRemoteRepository<Key, Value> {
    interface Sender<Key, Value> {
        suspend fun send(key: Key, value: Value): Result<Value>
    }

    interface Receiver<Key, Value> {
        suspend fun get(key: Key): Result<Value>
    }

    interface SenderReceiver<Key, Value> : Sender<Key, Value>, Receiver<Key, Value>
}