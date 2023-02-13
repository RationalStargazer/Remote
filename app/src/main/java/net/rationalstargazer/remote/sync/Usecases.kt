package net.rationalstargazer.remote.sync

import net.rationalstargazer.considerImmutable
import net.rationalstargazer.events.Lifecycle
import kotlin.coroutines.CoroutineContext

/*
class Usecases {

    // val local = Local()
    //
    // val remote = Remote()

    private val syncChatAgent = SyncChatAgent()

    suspend fun syncGeneralChat() {
        val user = syncChatAgent.readLocally()
        user.
        // val user = local
        //     .getUserId()
        //     ?: run {
        //         initUserId()
        //         local.getUserId()
        //     }

        if (user == null) {
            return
        }

        remote.getGeneralChat(user)
    }

    suspend fun initUserId() {

    }
}

private class SyncChatAgent : RemoteAgent<ChatChannel, ChatData, Unit, Unit>() {

    private val operationFactory = Id.Factory.create()
    private var activeItem: TreeItem? = null

    private val queue = object : DirectQueueAgent<ChatChannel, Unit>() {
        override suspend fun readQueue(): List<QueueItem<ChatChannel, Unit>> {
            TODO("Not yet implemented")
        }

        override suspend fun writeQueue(queue: List<QueueItem<ChatChannel, Unit>>): Boolean {
            TODO("Not yet implemented")
        }
    }

    // override suspend fun handleQueue(): QueueItemMutable<ChatChannel, Unit, Unit>? {
    //     val first = _queue.removeFirstOrNull()
    //
    //     // think about dependent aheads here
    //
    //     // TODO: save updated queue here
    //
    //     return first
    // }

    override suspend fun readItem(key: ChatChannel): ChatData? {
        return direct.readLocally(key)
    }

    override suspend fun syncItem(): Boolean {
        // determine next item

        val queueFirst = queue.readQueue().firstOrNull()

        if (queueFirst == null) {
            return false
        }

        if (activeItem != null) {
            //TODO: log error here
        }

        var topItem = TreeItem(queueFirst.operationId, null, null, emptyList())
            .let { item ->
                // check dependent aheads here
                // (add them to subtree)
                item
            }

        activeItem = topItem

        // the result is a stack of indices you can use to retrieve the next unfinished item (taking subitem tree into consideration)
        fun findNext(item: TreeItem, level: Int = 0): List<Int>? {
            if (level > 1000) {
                // TODO: throwError here
            }

            var i: Int = -1
            var subtreeResult: List<Int>? = null
            while (++i < item.subtree.size) {
                subtreeResult = findNext(item.subtree[i], level + 1)
                if (subtreeResult != null) {
                    break
                }
            }

            if (subtreeResult != null) {
                return listOf(i) + subtreeResult
            }

            if (item.result == null) {
                return emptyList()
            }

            return null
        }

        val currentIndices = findNext(topItem)

        val resultItem = syncTreeItem(topItem)

        topItem = topItem.mutateSubtree()

        return success
    }

    private fun syncTreeItem(item: TreeItem): TreeItem {
        //val value = direct.readLocally(queueFirst.key)

        networking = networking.nextNetworkingStart(
            NetworkingStatus.ItemInProgress(key, NetworkingStatus.Direction.Downloading)
        )

        //TODO: dispatch event here

        // download
        val nextRemote = direct.download(key)

        // we check again we don't have aheads
        getQueued(key).firstOrNull()
            ?.let {
                networking = networking.nextFinishResultDiscarded()
                return false
            }

        // --- acquire lock here (with a timer?) ---

        val success = nextRemote.handle(
            { chatData ->
                // write success result
                networking = networking.nextFinishSuccessful()
                direct.writeLocally(key, chatData)
            },

            { fail ->
                // handle fail result
                networking = networking.nextFinishFailure(
                    NetworkingStatus.ItemInProgress(key, NetworkingStatus.Direction.Downloading),
                    fail
                )

                false
            }
        )

        // --- release lock here ---

        // TODO: dispatch event
    }

    override suspend fun writeItem(key: ChatChannel, value: ChatData, dispatchEvent: Boolean): Boolean {
        // --- acquire lock here (with a timer?) ---
        val r = direct.writeLocally(key, value)
        // --- release lock here ---

        if (dispatchEvent) {
            TODO()
        }

        return r
    }

    override fun getQueue(): List<QueueItem<ChatChannel, Unit>> {
        return _queue.map { it.toImmutableItem() }
    }

    override fun getItems(key: ChatChannel): List<QueueItem<ChatChannel, Unit>> {
        return _queue
            .filter { it.key == key }
            .map { it.toImmutableItem() }
    }

    private data class AheadUpload(
        val item: QueueItemMutable<ChatChannel, Unit, Unit>,
        val result: RemoteResult<Unit, Unit>?
    )

    private val direct = SyncChatDirect()

    private suspend fun upload(
        key: ChatChannel,
        notifyBefore: Boolean,
        notifyAfter: Boolean
    ): RemoteData<ChatData> {
        val item = NetworkingStatus.ItemInProgress(key, NetworkingStatus.Direction.Uploading)
        networking = networking.nextNetworkingStart(item)

        if (notifyBefore) {
            TODO()
        }

        val result = direct.upload(key)

        networking = result.handle(
            {
                networking.nextFinishSuccessful()
            },

            { fail ->
                networking.nextFinishFailure(item, fail)
            }
        )

        if (notifyAfter) {
            TODO()
        }

        return result
    }
}

private class SyncChatDirect : DirectRemoteAgent<ChatChannel, ChatData, ChatData>() {
    override suspend fun readLocally(key: ChatChannel): ChatData? {
        TODO("Not yet implemented")
    }

    override suspend fun writeLocally(key: ChatChannel, value: ChatData): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun download(key: ChatChannel): RemoteData<ChatData> {
        TODO("Not yet implemented")
    }

    override suspend fun upload(key: ChatChannel): RemoteData<ChatData> {
        TODO("Not yet implemented")
    }
}

data class ChatChannel(val id: String)

data class ChatData(
    val channelId: Int,
    val messages: List<Int>,
    val numOfUnread: Int,
    val draft: Int
)*/

class Usecases {

    lateinit var chatRepo: BaseWritableRemoteComplexDataSource<ChatChannel, ChatData, SyncChatCommand>

    suspend fun init(lifecycle: Lifecycle, context: CoroutineContext) {
        val remote: DirectRemoteRepository.SenderReceiver<ChatChannel, ChatData> = XXX

        val local: LocalRepository.WriteAccess<ChatChannel, ChatData> = XXX

        val commandsRepo: LocalListRepository.WriteAccess<SyncChatCommand> = XXX

        commandsRepo.sole { repo ->
            val size = repo.findSize()
            val items = repo.sublist(0..size)
                .map {
                    RemoteQueueHandler.SyncCommand.Send(it.channel, SyncChatCommand(it.channel, it.data))
                }
                .considerImmutable()

            chatRepo = BaseWritableRemoteComplexDataSourceImpl(
                lifecycle,
                context,
                local,
                true,
                items,

                {
                    state: RemoteComplexDataSourceState<ChatChannel, SyncChatCommand>,
                    waitingCommands: RemoteComplexDataSourceCommands<ChatChannel, SyncChatCommand> ->

                    val nextState = state.toMutableList()

                    for (command in waitingCommands) {
                        when (command) {
                            is RemoteQueueHandler.QueueCommand.Add -> {
                                nextState.add(command.syncCommand)
                            }

                            is RemoteQueueHandler.QueueCommand.Remove -> {
                                val i = nextState.indexOfFirst { it.id == command.commandId }
                                if (i >= 0) {
                                    nextState.removeAt(i)
                                }
                            }

                            is RemoteQueueHandler.QueueCommand.ReplaceAll -> {
                                nextState.clear()
                                nextState.addAll(command.commands)
                            }
                        }
                    }

                    nextState
                },

                {
                    key: ChatChannel,
                    initialValue: ChatData?,
                    commands: RemoteComplexDataSourceState<ChatChannel, SyncChatCommand> ->

                    ChatData(0, emptyList(), 0, 0)
                },

                { state, read, write ->
                    val first = state.firstOrNull()

                    if (first == null) {
                        return@BaseWritableRemoteComplexDataSourceImpl
                    }

                    val command = first.value

                    val result = when (command) {
                        is RemoteQueueHandler.SyncCommand.Receive -> {
                            remote.get(command.key).getOrNull()
                        }

                        is RemoteQueueHandler.SyncCommand.Send -> {
                            remote.send(command.key, command.command.data).getOrNull()
                        }
                    }

                    if (result != null) {
                        write { writer ->
                            writer.write(ChatChannel(result.channelId.toString()), "data")
                            state.drop(1)
                        }
                    } else {
                        TODO("handle error here")
                    }
                }
            )
        }
    }

    private val user: LocalRepository.ReaderWriter<Unit, Int> = XXX
    private val local: LocalRepository.ReaderWriter<ChatChannel, ChatData> = XXX
    private val chat = ChatRemoteRepository()

    suspend fun syncGeneralChat() {
        val user = user.read(Unit)
            //     ?: run {
            //         initUserId()
            //         local.getUserId()
            //     }

        if (user == null) {
            return
        }

        chat.ensureSynced(ChatChannel(user.toString()), SyncConditions.InLast(60000))
    }

    suspend fun initUserId() {

    }
}

class ChatRemoteRepository : BaseWritableRemoteComplexDataSourceImpl<ChatChannel, ChatData, ChatChannel>(
    local,
    commands,
    commandsHandler,
    commandsReducer
) {

}

data class ChatChannel(val id: String)

data class ChatData(
    val channelId: Int,
    val messages: List<Int>,
    val numOfUnread: Int,
    val draft: Int
)

data class SyncChatCommand(val channel: ChatChannel, val data: String)