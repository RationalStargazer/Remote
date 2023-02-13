package net.rationalstargazer.remote.sync

import net.rationalstargazer.ImmutableList

/*abstract class RemoteAgent<Key, Local, UploadData, Event> {

    data class NetworkingStatus<Key>(
        val itemInProgress: TreeItem<Key>?,
        val fails: List<TreeItem<Key>>
    ) {
        data class TreeItem<Key>(
            val key: Key,
            val result: QueueItemResult?,
            val failure: RemoteData<Unit>?,
            val subtree: List<TreeItem<Key>>
        )

        // fun nextNetworkingStart(nextItemInProgress: Key): NetworkingStatus<Key> {
        //     return NetworkingStatus(
        //         nextItemInProgress,
        //         fails - nextItemInProgress
        //     )
        // }
        //
        // fun nextFinishSuccessful(): NetworkingStatus<Key> {
        //     if (itemInProgress == null) {
        //         return this
        //     }
        //
        //     return NetworkingStatus(
        //         null,
        //         fails - itemInProgress
        //     )
        // }
        //
        // fun nextFinishFailure(
        //     failedItemInProgress: Key,
        //     fail: RemoteData.Fail
        // ): NetworkingStatus<Key> {
        //     return NetworkingStatus(
        //         null,
        //         fails - failedItemInProgress + (failedItemInProgress to fail)
        //     )
        // }
        //
        // fun nextFinishResultDiscarded(): NetworkingStatus<Key> {
        //     return nextFinishSuccessful()
        // }
    }

    data class QueueItem<Key, UploadData>(
        val operationId: Id,
        val key: Key,
        val uploadData: UploadData?
    )

    enum class QueueItemResult {
        Success,
        Cancelled,
        NetworkFailure,
        OtherFailure
    }

    data class TreeItem(
        val operationId: Id,
        val result: QueueItemResult?,
        val failure: RemoteData<Unit>?,
        val subtree: List<TreeItem>
    ) {
        fun get(subtreeIndices: List<Int>): TreeItem? {
            if (subtreeIndices.isEmpty()) {
                return null
            }

            val i = subtreeIndices[0]
            if (i < 0 || i >= subtree.size) {
                return null
            }

            val item = subtree[i]
            if (subtreeIndices.size == 1) {
                return item
            }

            return item.get(subtreeIndices.subList(1, subtreeIndices.size))
        }

        fun mutateSubtree(subtreeIndices: List<Int>, nextValue: TreeItem): TreeItem? {
            if (subtreeIndices.isEmpty()) {
                return null
            }

            val i = subtreeIndices[0]
            if (i < 0 || i >= subtree.size) {
                return null
            }

            val nextSubItem = if (subtreeIndices.size > 1) {
                subtree[i].mutateSubtree(subtreeIndices.subList(1, subtreeIndices.size), nextValue)
            } else {
                nextValue
            }

            if (nextSubItem == null) {
                return null
            }

            return copy(
                subtree = subtree
                    .toMutableList()
                    .also {
                        it[i] = nextSubItem
                    }
            )
        }
    }

    *//*protected data class QueueItemMutable<Key, UploadData, Event>(
        val key: Key,
        val uploadData: MutableList<UploadData>

        *//**//*
         * Sometimes queue item operation can be organized, subdivided, postponed or somehow depends on other operations.
         * Here you can store additional information about state of the operation.
         * For example, you can count number of failed attempts here. Or you can organize a list of dependent operations.
         * Bear in mind that usually you may want to ensure persistent storage for the value.
         *//**//*
        //var details: Details,
        //var result: RemoteData<Unit>?
    ) {
        // HOW EXACTLY DO WE WANT TO DEAL WITH RECURSIVE EVENTS?
        val dispatcher = MutableSharedFlow<Event>(1)

        fun toImmutableItem(): QueueItem<Key, Details> {
            return QueueItem(key*//**//*, details, result)
        }
    }
    *//*

    *//*protected sealed class QueueItemMutableBackupCopy<Key, UploadData, Event> {

        abstract val key: Key

        // HOW EXACTLY DO WE WANT TO DEAL WITH RECURSIVE EVENTS?
        val dispatcher = MutableSharedFlow<Event>(1)

        *//**//**
         * Sometimes queue item operation can be organized, subdivided or somehow depends on other operations.
         * Here you can store additional information.
         * For example in case of dependent aheads you can place here all commands for them to know theirs results
         *//**//*
        val subItems: MutableList<QueueItemMutable<Key, UploadData, Event>> = mutableListOf()

        data class DownloadMutable<Key, Event>(override val key: Key) : QueueItemMutable<Key, Nothing, Event>() {

            override fun toImmutableItem(): QueueItem<Key, Nothing> {
                return QueueItem.Download(key)
            }
        }

        data class UploadMutable<Key, UploadData, Event>(
            override val key: Key,
            val value: UploadData
        ): QueueItemMutable<Key, UploadData, Event>() {

            override fun toImmutableItem(): QueueItem<Key, UploadData> {
                return QueueItem.Upload(key, value)
            }
        }

        abstract fun toImmutableItem(): QueueItem<Key, out UploadData>
    }*//*

    fun check() {
        // check queue (execute)
    }

    suspend fun read(key: Key): Local? {
        // LOCK? (with timer?)
        return readItem(key)
    }

    enum class WriteOptions {
        Sync,
        NoSync,
        NoAnything;

        val eventsEnabled: Boolean get() = this != NoAnything
    }

    suspend fun write(key: Key, value: Local, writeOptions: WriteOptions): Flow<Pair<Key, Boolean>> {
        val r = writeItem(key, value, writeOptions.eventsEnabled)

        if (r) {
            // TODO: enqueue sync command
        }

        return
    }

    // we also need an ability to know when sync has finished
    // we can implement it simple or fancy. fancy way would be to create DSL for sequential execution of syncs
    fun sync(key: Key): Flow<Pair<Key, Boolean>> {
        // TODO: enqueue sync command
        return flowOf(key to false)
    }

    suspend fun changeLocally(key: Key, value: Local, dispatchEvent: Boolean): Boolean {
        return writeItem(key, value, dispatchEvent)
    }

    suspend fun changeThenEnqueueUpload(key: Key, value: Local): Boolean {
        val r = changeLocally(key, value, true)

        if (r) {
            // TODO: enqueue upload command
        }

        return r
    }

    *//**
     * Here you have to update the queue for next command. In simplest case you have to pull first item from the queue
     * and return it. Aheads, dependent aheads and so on are also checked here. It is your responsibility to check here
     * that your next operation won't disrupt existing data.
     * @return command that should be executed (when `null`: no command will be executed until user put new command in
     * the queue with [startDownload], [changeThenEnqueueUpload] or similar)
     *//*
    //protected abstract suspend fun handleQueue(): QueueItemMutable<Key, UploadData, Event>?

    // DO NOT FORGET TO LOCK HERE
    protected abstract suspend fun readItem(key: Key): Local?

    // DO NOT FORGET TO LOCK HERE
    protected abstract suspend fun writeItem(key: Key, value: Local, dispatchEvent: Boolean): Boolean

    protected abstract suspend fun syncItem(): Boolean

    protected open fun executeItem() {
        // do we need it?
    }

    // This can be done not here
    //abstract suspend fun dispatchEvent()

    var networking: NetworkingStatus<Key> = NetworkingStatus(null, emptyList())
        protected set

    abstract fun getQueue(): List<QueueItem<Key, UploadData>>

    abstract fun getItems(key: Key): List<QueueItem<Key, UploadData>>
}

abstract class DirectQueueAgent<Key, UploadData> {
    // stub, will be replaced with real scope
    val coroutineScope: CoroutineScope = GlobalScope

    abstract suspend fun readQueue(): List<RemoteAgent.QueueItem<Key, UploadData>>

    abstract suspend fun writeQueue(queue: List<RemoteAgent.QueueItem<Key, UploadData>>): Boolean
}

abstract class DirectRemoteAgent<Key, Local, Remote> {

    // stub, will be replaced with real scope
    val coroutineScope: CoroutineScope = GlobalScope

    // do not leak control into outer code (do not call unknown callbacks) because read/write access is locked here
    abstract suspend fun readLocally(key: Key): Local?

    // do not leak control (don't dispatch events or other callbacks) because read/write access is locked here
    abstract suspend fun writeLocally(key: Key, value: Local): Boolean

    abstract suspend fun download(key: Key): RemoteData<Remote>

    abstract suspend fun upload(key: Key): RemoteData<Remote>

    // This can be done not here
    //abstract suspend fun dispatchEvent()
}*/



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

interface RemoteQueueHandler<Key, Value, Command> {

    sealed class SyncCommand<Key, out Command> {
        data class Receive<Key>(val key: Key, val conditions: SyncConditions) : SyncCommand<Key, Nothing>()
        data class Send<Key, Command>(val key: Key, val command: Command) : SyncCommand<Key, Command>()
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

interface BaseMessageQueueHandler<Message> {
    fun add(message: Message)
    fun removeAt(index: Int)
    fun replaceAll(messages: ImmutableList<Message>)
    fun start()
    fun pause()
}

// interface BaseMessageQueueHandler<Message> {
//     //val messages: SimpleDataSource<EnumeratedMessageQueue<Message>>
//
//     fun add(message: Message): Id
//     fun remove(messageId: Id)
//     fun replaceAll(messages: List<Message>)
// }

sealed class SyncConditions {
    // object ExistsLocally : SyncConditions()

    object SinceStart : SyncConditions()
    data class InLast(val millisecs: Long) : SyncConditions()
}

typealias MutableRemoteComplexDataSourceState<Key, Command> = MutableList<IdContainer<RemoteQueueHandler.SyncCommand<Key, Command>>>
typealias RemoteComplexDataSourceState<Key, Command> = List<IdContainer<RemoteQueueHandler.SyncCommand<Key, Command>>>
typealias RemoteComplexDataSourceCommands<Key, Command> = List<RemoteQueueHandler.QueueCommand<Key, Command>>

interface BaseRemoteComplexDataSource<Key, Value> {

    fun ensureSynced(key: Key, conditions: SyncConditions): Id

    suspend fun read(key: Key): Value?
}

interface BaseWritableRemoteComplexDataSource<Key, Value, Command> : BaseRemoteComplexDataSource<Key, Value> {

    fun write(key: Key, command: Command): Id

    fun cancelCommand(commandId: Id)
}

interface LocalRepository {

    interface ReadAccess<Key, Value> : Reader<Key, Value> {
        suspend fun sole(block: suspend (Reader<Key, Value>) -> Unit)
    }

    interface WriteAccess<Key, Value> : ReaderWriter<Key, Value> {
        suspend fun sole(block: suspend (ReaderWriter<Key, Value>) -> Unit)
        val readOnlyAccess: ReadAccess<Key, Value>
    }

    interface Reader<Key, Value> {
        suspend fun read(key: Key): Value?
    }

    interface Writer<Key, Value> {
        fun write(key: Key, value: Value)
    }

    interface ReaderWriter<Key, Value> : Reader<Key, Value>, Writer<Key, Value>
}

interface LocalListRepository {

    interface ReadAccess<T> : Reader<T> {
        suspend fun sole(block: suspend (Reader<T>) -> Unit)
    }

    interface WriteAccess<T> : ReaderWriter<T> {
        suspend fun sole(block: suspend (ReaderWriter<T>) -> Unit)
        val readOnlyAccess: ReadAccess<T>
    }

    interface Reader<T> : LocalRepository.Reader<Int, T> {
        val size: Int?

        suspend fun findSize(): Int

        suspend fun sublist(range: IntRange): List<T>
    }

    interface Writer<T> : LocalRepository.Writer<Int, T> {
        fun add(value: T)
    }

    interface ReaderWriter<T> : Reader<T>, Writer<T>
}

interface DirectLocalRepository {
    interface Reader<Key, Value> {
        suspend fun read(key: Key): Value?
    }

    interface Writer<Key, Value> {
        suspend fun write(key: Key, value: Value)
    }
}

interface DirectRemoteRepository<Key, Value> {
    interface Sender<Key, Value> {
        suspend fun send(key: Key, value: Value): Result<Value>
    }

    interface Receiver<Key, Value> {
        suspend fun get(key: Key): Result<Value>
    }

    interface SenderReceiver<Key, Value> : Sender<Key, Value>, Receiver<Key, Value>
}