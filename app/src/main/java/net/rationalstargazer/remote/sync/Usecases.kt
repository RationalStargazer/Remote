package net.rationalstargazer.remote.sync

import net.rationalstargazer.events.Lifecycle
import net.rationalstargazer.remote.RemoteData
import kotlin.coroutines.CoroutineContext

//typealias ChatRepoNetworking = Map<ChatChannel, Usecases.NetworkData>
//
// class Usecases {
//
//     data class NetworkData(val inProgress: Boolean, val lastResult: RemoteData<Unit>?, val lastResultTime: Long)
//
//     lateinit var chatRepo: BaseWritableRemoteComplexDataSourceImpl<ChatRepoNetworking, ChatChannel, ChatData, SyncChatCommand>
//
//     suspend fun init(lifecycle: Lifecycle, context: CoroutineContext) {
//         val remote: DirectRemoteRepository.SenderReceiver<ChatChannel, ChatData> = XXX
//
//         val local: Repository.WriteAccess<ChatChannel, ChatData> = XXX
//
//         val commandsRepo: LocalListRepository.WriteAccess<SyncChatCommand> = XXX
//
//         val items = commandsRepo.getAll()
//             .map {
//                 RemoteQueueHandler.SyncCommand.Send(it.channel, SyncChatCommand(it.channel, it.data))
//             }
//
//         chatRepo = BaseWritableRemoteComplexDataSourceImpl<ChatRepoNetworking, ChatChannel, ChatData, SyncChatCommand>(
//             lifecycle,
//             context,
//             local,
//             true,
//             emptyMap(),
//             items,
//
//             { state, commands ->
//
//                 val queue = state.queue.toMutableList()
//
//                 for (command in commands) {
//                     when (command) {
//                         is RemoteQueueHandler.QueueCommand.Add -> {
//                             queue.add(command.syncCommand)
//                         }
//
//                         is RemoteQueueHandler.QueueCommand.Remove -> {
//                             val i = queue.indexOfFirst { it.id == command.commandId }
//                             if (i >= 0) {
//                                 queue.removeAt(i)
//                             }
//                         }
//
//                         is RemoteQueueHandler.QueueCommand.ReplaceAll -> {
//                             queue.clear()
//                             queue.addAll(command.commands)
//                         }
//                     }
//                 }
//
//                 RemoteQueueHandler.State(state.data, queue)
//             },
//
//             { key, initialValue, state ->
//                 ChatData(0, emptyList(), 0, 0)
//             },
//
//             { state, _, write ->
//                 val first = state.queue.firstOrNull()
//
//                 if (first == null) {
//                     return@BaseWritableRemoteComplexDataSourceImpl
//                 }
//
//                 val command = first.value
//
//                 val result = when (command) {
//                     is RemoteQueueHandler.SyncCommand.Receive -> {
//                         remote.get(command.key).getOrNull()
//                     }
//
//                     is RemoteQueueHandler.SyncCommand.Send -> {
//                         remote.send(command.key, command.command.data).getOrNull()
//                     }
//                 }
//
//                 val nextData = state.data.toMutableMap().also { it[command.key] = NetworkData(false, result, time) }
//
//                 write { writer ->
//                     if (result != null) {
//                         writer.write(ChatChannel(result.channelId.toString()), "data")
//                     }
//
//                     RemoteQueueHandler.State(
//                         nextData,
//                         state.queue.drop(1)
//                     )
//                 }
//             }
//         )
//
//         chatRepo.state.listen(false, lifecycle) {
//             val sendItems = chatRepo.state.value.queue
//                 .mapNotNull { item ->
//                     when (item.value) {
//                         is RemoteQueueHandler.SyncCommand.Send -> item.value.command
//
//                         is RemoteQueueHandler.SyncCommand.Receive -> null
//                     }
//                 }
//
//             commandsRepo.replaceAll(sendItems)
//         }
//     }
//
//     private val user: Repository.ReaderWriter<Unit, Int> = XXX
//     private val local: Repository.ReaderWriter<ChatChannel, ChatData> = XXX
//
//     suspend fun syncGeneralChat() {
//         val user = user.read(Unit)
//             //     ?: run {
//             //         initUserId()
//             //         local.getUserId()
//             //     }
//
//         if (user == null) {
//             return
//         }
//
//         chatRepo.ensureSynced(ChatChannel(user.toString()), SyncConditions.InLast(60000))
//     }
//
//     suspend fun initUserId() {
//
//     }
// }

data class ChatChannel(val id: String)

data class ChatData(
    val channelId: Int,
    val messages: List<Int>,
    val numOfUnread: Int,
    val draft: Int
)

data class SyncChatCommand(val channel: ChatChannel, val data: String)