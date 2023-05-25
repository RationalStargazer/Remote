package net.rationalstargazer.networking

import androidx.lifecycle.ViewModel
import example.WritableSimpleRemoteDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.rationalstargazer.events.lifecycle.RStaCoroutineDispatcherFactory
import net.rationalstargazer.events.lifecycle.RStaCoroutineLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycleDispatcher
import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher
import net.rationalstargazer.events.value.RStaValue
import net.rationalstargazer.events.value.RStaValueDispatcher
import net.rationalstargazer.logic.RStaBaseMessageQueueHandlerImpl
import net.rationalstargazer.remote.RemoteData
import net.rationalstargazer.remote.RemoteData.Companion.handle
import net.rationalstargazer.remote.sync.DirectRemoteRepository
import net.rationalstargazer.remote.sync.RemoteQueueHandler
import net.rationalstargazer.remote.sync.RemoteSyncTarget
import net.rationalstargazer.remote.sync.Repository
import kotlin.coroutines.CoroutineContext

/**
 * Example of my programming.
 */

/**
 * The state that describes the data for view layer
 */
data class ExampleState(
    /**
     * True when all data are loaded from the server
     */
    val loaded: Boolean,
    
    /**
     * True when `loaded` is true and all data that should be sent were sent successfully
     */
    val synced: Boolean,
    
    /**
     * Keeps last remote results to let a view layer explain what exactly went wrong.
     * (it can be network problems, or for example server could decide that the data is invalid)
     */
    val lastRemoteResult: Map<ExampleKey, RemoteData<SendResult<Unit, ExampleDataRejectedReason>>>,
    
    /**
     * Something we want to work with
     */
    val someThing: String?,
    
    /**
     * Another thing we want to work with
     */
    val anotherThing: String?
) {
    
    fun mutateExampleValue(key: ExampleKey, value: ExampleValue?): ExampleState {
        return when (key) {
            ExampleKey.SomeThing -> copy(someThing = value?.exampleData)
            ExampleKey.AnotherThing -> copy(anotherThing = value?.exampleData)
        }
    }
}

/**
 * View model
 */
class ExampleSimpleModel : ViewModel() {
    
    /**
     * Lifecycle is a thing you can track to get to know when it is time to stop working and release especially heavy references.
     * Basically it is a source of "time to finish" event anyone can listen to.
     * In onCleared() we call lifecycle.close() to start the notification of all its listeners
     * and when all of them will be handled the lifecycle will be moved to "finished" state.
     *
     * (domain coordinator we inject here is an event queue handler,
     * see explanations in [RStaEventsQueueDispatcher])
     */
    private val lifecycle = RStaLifecycleDispatcher(injectDomainCoordinator())
    
    /**
     * Coroutine dispatcher provides an ability to start new coroutine at any time, outside of current CoroutineScope.
     * Note the lifecycle that is provided as a parameter,
     * when the lifecycle will be finished no new coroutines will be provided
     * and those that were created with autoCancellableScope() will be closed automatically.
     * (used internally in some of my implementations)
     */
    private val dispatcher = RStaCoroutineDispatcherFactory.create(
        RStaCoroutineLifecycle(
            lifecycle,
            injectDomainCoroutineContext()  // CoroutineContext to use for the creation of coroutines
        )
    )
    
    private val repository: ExampleRepository = injectExampleRepository()
    
    /**
     * See [state]
     */
    private val _state = RStaValueDispatcher(
        lifecycle,
        // Initial state
        ExampleState(
            loaded = false,
            synced = false,
            lastRemoteResult = mapOf(),
            someThing = null,
            anotherThing = null
        )
    )
    
    /**
     * It holds the value and notifies its listeners when the value has changed.
     * See [net.rationalstargazer.events.value.RStaGenericValue] for details.
     */
    val state: RStaValue<ExampleState> = _state
    
    /**
     * Sync data with the server
     */
    fun sync(force: Boolean) {
        val syncTarget = if (force) {
            // update now
            RemoteSyncTarget.InLast(0)
        } else {
            // update if it was updated more than 10 minutes ago
            RemoteSyncTarget.InLast(600_000)
        }
        
        repository.sync(ExampleKey.SomeThing, syncTarget)
        repository.sync(ExampleKey.AnotherThing, syncTarget)
    }
    
    /**
     * Write data to local cache, then send it to the server
     */
    fun write(key: ExampleKey, value: String) {
        repository.write(key, ExampleValue(value))
    }
    
    /**
     * Jet Pack's method. We close our lifecycle here.
     */
    override fun onCleared() {
        lifecycle.close()
        super.onCleared()
    }
    
    init {
        // listen repository's changes
        repository.changeSource.listen(lifecycle) { key ->
            
            // start coroutine because the listener is not suspend function
            // "manually cancellable" means we don't want it to be cancelled at the moment when `lifecycle` was ended
            // instead we let the scope to finish the work
            // (if lifecycle was already ended at this moment,
            // manuallyCancellableScope will return `null` and `launch` call will be skipped)
            dispatcher.manuallyCancellableScope()?.launch {
                val (networkState, value) = repository.readWithState(key)
    
                // Let's notice that we have to work carefully with the states here.
                // As we started coroutine to handle the result
                // we have to consider that _state.value can be already changed to this time,
                // (and the state of the repository can be different too from the time when repository's event was dispatched)
                // so we have to make a decision what values exactly we want to use to make the result state.
    
                val s = _state.value
                
                // We decided to use latest state (the state after the coroutine was started and the value was read)
                // For some (not that straightforward as in the example) logic it could be a wrong decision,
                // but if we would decide to capture the state at the first line of the listener
                // we can face another synchronization issues like race condition.
                // This problem can be addressed by enqueueing all the handlers, and handle exactly one event at a time,
                // one after another.
                // This implementation is shown in ExampleModelWithQueue (see below)
                
                _state.value =
                    ExampleState(
                        loaded(networkState),
                        synced(networkState),
                        networkState.data.remoteResult,
                        s.someThing,
                        s.anotherThing
                    )
                    .mutateExampleValue(key, value)
            }
        }
        
        // do something once when ExampleModel is instantiated
        repository.sync(ExampleKey.SomeThing, RemoteSyncTarget.Once)
        repository.sync(ExampleKey.AnotherThing, RemoteSyncTarget.Once)
    }
}

/**
 * Example implementation where all changes are handled inside message (command) queue.
 */
class ExampleModelWithQueue : ViewModel() {
    
    /**
     * [ExampleReducerCommand] is all messages (commands) that can be enqueued.
     * ExampleCommand is subset of ExampleReducerCommand, they are commands that can be enqueued publicly
     * (from the outside of the class, see [submit])
     */
    sealed class ExampleCommand : ExampleReducerCommand {
    
        data class Sync(val force: Boolean) : ExampleCommand()
        
        data class Write(val key: ExampleKey, val value: String) : ExampleCommand()
    }
    
    private val lifecycle = RStaLifecycleDispatcher(injectDomainCoordinator())
    
    private val dispatcher = RStaCoroutineDispatcherFactory.create(
        RStaCoroutineLifecycle(
            lifecycle,
            injectDomainCoroutineContext()
        )
    )
    
    private val repository: ExampleRepository = injectExampleRepository()
    
    private val _state = RStaValueDispatcher(
        lifecycle,
        ExampleState(
            loaded = false,
            synced = false,
            lastRemoteResult = mapOf(),
            someThing = null,
            anotherThing = null
        )
    )
    
    val state: RStaValue<ExampleState> = _state
    
    /**
     * Now we want to use a queue.
     * Each interaction with ExampleModelWithQueue is described by interaction-specific message
     * (implemented as [ExampleReducerCommand] here).
     *
     * The messages are enqueued and executed in sequence
     * which allows us to use suspend functions to handle commands
     * and at the same time to be sure that there are no multiple executions in parallel which will complicate the logic.
     *
     * If some handling requires too much time,
     * you can launch separate CoroutineScope with the [dispatcher] and do the work there,
     * then add the result to the queue (using ExampleReducerCommand) to incorporate the result into the [state].
     *
     * By using a queue to handle all interactions we simplify a lot of things related to concurrent issues in other areas,
     * but here it means that user actions will be handled not exactly at the moment of the call but some time later.
     * In most situations it doesn't make things much more difficult but you may need to check carefully
     * if the command is still relevant before handle it.
     */
    fun submit(command: ExampleCommand) {
        reducer.add(command)
    }
    
    override fun onCleared() {
        lifecycle.close()
        super.onCleared()
    }
    
    /**
     * An implementation of message (command) queue.
     */
    private val reducer: RStaBaseMessageQueueHandlerImpl<ExampleReducerCommand>
    
    /**
     * Commands to be handled in the queue.
     */
    private sealed interface ExampleReducerCommand {
        /**
         * These classes represent private (inner) commands.
         */
        data class HandleRepositoryChange(
            val key: ExampleKey,
            val networkState: RemoteQueueHandler.State<ExampleNetworkingData, ExampleKey, ExampleValue>
        ) : ExampleReducerCommand
        
        // Other private commands can be here
        // data class OtherPrivateCommand() : ExampleReducerCommand
    }
    
    init {
        // Main view model logic is here.
        // It is an implementation of command queue.
        // In the lambda parameter we provide the handler to handle the commands.
        // All commands are executed sequentially, one at a time,
        // so we can be sure `state` value will not be changed outside of the handler in the middle of the handling.
        reducer = RStaBaseMessageQueueHandlerImpl<ExampleReducerCommand>(
            dispatcher
        ) { command ->
            // handle the command
            when (command) {
                is ExampleCommand.Sync -> {
                    val syncTarget = if (command.force) {
                        // update now
                        RemoteSyncTarget.InLast(0)
                    } else {
                        // update if it was updated more than 10 minutes ago
                        RemoteSyncTarget.InLast(600_000)
                    }
    
                    repository.sync(ExampleKey.SomeThing, syncTarget)
                    repository.sync(ExampleKey.AnotherThing, syncTarget)
                }
                
                is ExampleCommand.Write -> {
                    repository.write(command.key, ExampleValue(command.value))
                }
                
                is ExampleReducerCommand.HandleRepositoryChange -> {
                    // we can use suspend functions here
                    val nextValue = repository.read(command.key)
                    if (nextValue == null) {
                        // Here is an example of handling when we want to do something that requires a lot of time.
                        // We don't want to delay execution of subsequent commands so we start a separate coroutine.
                        // As we use the same dispatcher we used as a parameter for RStaBaseMessageQueueHandlerImpl
                        // and we know that the CoroutineContext for the dispatcher
                        // is configured to use a fixed thread for all coroutines,
                        // we know it will not create concurrency problems, because this lambda
                        // will be enqueued to be executed on the same thread.
                        dispatcher.manuallyCancellableScope()?.launch {
                            delay(10000)
                            
                            // However in this scope (inside the launch lambda) we shouldn't update the state
                            // or else we will introduce unnecessary concurrent complexity.
                            // We just make a call to the repository
                            // and because we listen all the changes of it
                            // we will handle the result of the call there
                            
                            // Here we enqueue sync command to the repository.
                            // If the repository will already have sync command for the same key,
                            // it will not lead to excessive networking because we have specified RemoteSyncTarget.Once parameter
                            repository.sync(command.key, RemoteSyncTarget.Once)
                        }
                    } else {
                        _state.value =
                            ExampleState(
                                loaded(command.networkState),
                                synced(command.networkState),
                                command.networkState.data.remoteResult,
                                _state.value.someThing,
                                _state.value.anotherThing,
                            )
                            .mutateExampleValue(command.key, nextValue)
                    }
                }
            }
        }
        
        // listen repository's changes
        repository.changeSource.listen(lifecycle) { key ->
            // we handle the changes here by adding appropriate commands to the queue
            reducer.add(ExampleReducerCommand.HandleRepositoryChange(key, repository.state.value))
        }
    
        // do something once when ExampleModel is instantiated
        repository.sync(ExampleKey.SomeThing, RemoteSyncTarget.Once)
        repository.sync(ExampleKey.AnotherThing, RemoteSyncTarget.Once)
    }
}

// This method should belong both to ExampleModel and ExampleModelWithQueue
// (it is moved out and shared between the models for simplicity of the example)
private fun loaded(networkState: RemoteQueueHandler.State<ExampleNetworkingData, ExampleKey, ExampleValue>): Boolean {
    val failedMap = networkState.failedAttempts
    if (failedMap[ExampleKey.SomeThing] != null || failedMap[ExampleKey.AnotherThing] != null) {
        return false
    }
    
    val successMap = networkState.lastSuccessfulElapsedRealTimes
    return successMap[ExampleKey.SomeThing] != null && successMap[ExampleKey.AnotherThing] != null
}

// This method should belong both to ExampleModel and ExampleModelWithQueue
// (it is moved out and shared between the models for simplicity of the example)
private fun synced(networkState: RemoteQueueHandler.State<ExampleNetworkingData, ExampleKey, ExampleValue>): Boolean {
    if (!loaded(networkState)) {
        return false
    }
    
    val inQueue = networkState.queue.any {
        it.value.key == ExampleKey.SomeThing || it.value.key == ExampleKey.AnotherThing
    }
    
    if (inQueue) {
        return false
    }
    
    val inFailedSends = networkState.failedSends.any {
        it.key == ExampleKey.SomeThing || it.key == ExampleKey.AnotherThing
    }
    
    if (inFailedSends) {
        return false
    }
    
    return true
}

// Should be in domain layer
interface ExampleRepository : WritableSimpleRemoteDataSource<ExampleNetworkingData, ExampleKey, ExampleValue>

// Should be in data layer (factory function for ExampleRepository)
fun ExampleRepository(
    // in straightforward implementation it should be the same as CoroutineLifecyel that was passed to the `dispatcher` of ExampleModel
    // to do without thread switching logic
    // however thread switching is not hard to implement here if it is really needed
    dataLayerCoroutineLifecycle: RStaCoroutineLifecycle,
    
    // source of current elapsed real time
    // (can be used in network error's handling logic to track the time after unsuccessful networking
    // to not overload the server with immediate retries)
    elapsedRealTimeSource: ElapsedRealTimeSource,
    
    // access to local cache
    exampleLocalRepository: Repository.Writable<ExampleKey, ExampleValue>,
    
    // local cache for "locally ahead" values (see WritableSimpleRemoteDataSource for details)
    exampleLocalCommandsRepository: Repository.Writable<Unit, List<Pair<ExampleKey, ExampleValue>>>,
    
    // provides access to send and receive data to/from the server
    exampleRemoteRepository: DirectRemoteRepository.SenderReceiver<ExampleKey, ExampleValue, SendResult<ExampleValue, ExampleDataRejectedReason>>
): ExampleRepository {
    // See WritableSimpleRemoteDataSource
    val exampleRepository = WritableSimpleRemoteDataSource(
        dataLayerCoroutineLifecycle,
        elapsedRealTimeSource,
        exampleLocalRepository,
        exampleLocalCommandsRepository,
        ExampleNetworkingData(mapOf()),
        { state, command ->
            // Not very interesting
            state
        }
    ) { state, queueItem ->
        val command = queueItem.value
        val key = command.key
        
        val (handlerResult, remoteData) = when (command) {
            is RemoteQueueHandler.SyncCommand.Sync -> {
                // if we need to Sync, get the value from the server
                exampleRemoteRepository.get(command.key)
                    .handle(
                        {
                            // successful networking branch
                            WritableSimpleRemoteDataSource.HandlerResult.Success(it) to RemoteData.Data(SendResult.Data(Unit))
                        },
                        
                        {
                            // failed networking branch
                            WritableSimpleRemoteDataSource.HandlerResult.Fail to it
                        }
                    )
            }
            
            is RemoteQueueHandler.SyncCommand.Send -> {
                // send the value to the server
                exampleRemoteRepository.send(command.key, command.command)
                    .handle(
                        { sendResult ->
                            // successful networking branch
                            val result = when (sendResult) {
                                // server accepted our data
                                is SendResult.Data -> WritableSimpleRemoteDataSource.HandlerResult.Success(sendResult.value)
                                
                                // maybe we sent something wrong and server rejected the call
                                is SendResult.Rejected -> WritableSimpleRemoteDataSource.HandlerResult.Fail
                            }
                            
                            result to RemoteData.Data(sendResult.mapData { Unit })
                        },
                        
                        {
                            // failed networking branch
                            WritableSimpleRemoteDataSource.HandlerResult.Fail to it
                        }
                    )
            }
        }
        
        // return the handlerResult (success with the actual value or fail (value will be left without changes))
        // and next state (in our example it is the information about the last networking)
        handlerResult to
            state.copy(
                remoteResult = state.remoteResult + (key to remoteData)
            )
    }
    
    return object :
        ExampleRepository,
        WritableSimpleRemoteDataSource<ExampleNetworkingData, ExampleKey, ExampleValue> by exampleRepository {}
}

// Example of repository state (or more specifically it is a part of repository's state called `data`)
// In the example it holds the information about the last networking to provide feedback to user when something goes wrong
data class ExampleNetworkingData(
    val remoteResult: Map<ExampleKey, RemoteData<SendResult<Unit, ExampleDataRejectedReason>>>
)

enum class ExampleKey {
    SomeThing,
    AnotherThing
}

// Implemented as data class instead of just String to show the repository can work with any types of data, not just strings
data class ExampleValue(val exampleData: String)

// Example for the case when server can reject the data for some reason
// (for example if you can't validate the data completely before the send)
sealed class SendResult<out DataType, out RejectedType> {
    /**
     * All okay (`value` holds the data from server)
     */
    data class Data<DataType>(val value: DataType) : SendResult<DataType, Nothing>()
    
    /**
     * Not okay (`info` holds the data about an error)
     */
    data class Rejected<RejectedType>(val info: RejectedType) : SendResult<Nothing, RejectedType>()
    
    /**
     * Map SendResult.Data to another DataType (RejectedType will be the same)
     */
    fun <MappedData> mapData(block: (DataType) -> MappedData): SendResult<MappedData, RejectedType> {
        return when (this) {
            is Data -> Data(block(this.value))
            is Rejected -> Rejected(this.info)
        }
    }
}

// Example of specific Rejected type for SendResult.
enum class ExampleDataRejectedReason { InvalidData }

// Interface for the source of current elapsed real time.
// Made as interface for convenient injection.
interface ElapsedRealTimeSource : () -> Long

// injections
fun injectDomainCoordinator(): RStaEventsQueueDispatcher {
    TODO()
}

fun injectDomainCoroutineContext(): CoroutineContext {
    // Let's imagine that we have provided CoroutineContext here
    TODO()
}

fun injectExampleRepository(): ExampleRepository {
    // Let's imagine that we have provided an ExampleRepository
    TODO()
}