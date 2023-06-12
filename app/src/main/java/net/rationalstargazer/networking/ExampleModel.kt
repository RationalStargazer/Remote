package net.rationalstargazer.networking

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import example.RemoteChangeableState
import example.RemoteState
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
 *
 * (In real world scenario a company may decide
 * it is worth to have complete UDF architecture (to decouple action dispatchers from reducer)
 * to reduce probability of programming errors which can be made by less experienced members of a team.
 * This kind of example would be excessively complicated to show it here.)
 */

/**
 * Example data.
 */
data class ExampleData(val someThing: String, val anotherThing: String) {
    fun mutate(key: ExampleKey, value: String): ExampleData {
        return when (key) {
            ExampleKey.SomeThing -> copy(someThing = value)
            ExampleKey.AnotherThing -> copy(anotherThing = value)
        }
    }
}

enum class ExampleKey { SomeThing, AnotherThing }

/**
 * The state that describes the data for view layer
 */
sealed class ExampleState {
    object FirstLoad : ExampleState()
    
    data class LoadFailed(val fail: RemoteData.Fail) : ExampleState()
    
    data class Data(
        /**
         * Values before they were edited
         */
        val initialValues: ExampleData,
    
        /**
         * Current values
         */
        val values: ExampleData,
        
        val validationResult: Map<ExampleKey, ValidationResult>,
    
        val submitResult: SubmitResult?,
    ) : ExampleState() {
        
        val dataChanged: Boolean = initialValues != values
    
        val submitButtonEnabled: Boolean = dataChanged
    }
    
    data class Submitting(
        val initialValues: ExampleData,
        val valuesToSubmit: ExampleData
    ) : ExampleState()
    
    sealed class SubmitResult {
        object Success : SubmitResult()
        
        data class NetworkFail(val fail: RemoteData.Fail) : SubmitResult()
        
        data class ValidationFail(val info: ExampleDataRejectedReason) : SubmitResult()
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
    
    private val simpleExampleReceiver = injectSimpleExampleReceiver()
    private val simpleExampleSender = injectSimpleExampleSender()
    
    /**
     * See [state]
     */
    private val _state = RStaValueDispatcher<ExampleState>(
        lifecycle,
        // Initial state
        ExampleState.FirstLoad
    )
    
    /**
     * It holds the value and notifies its listeners when the value has changed.
     * See [net.rationalstargazer.events.value.RStaGenericValue] for details.
     */
    val state: RStaValue<ExampleState> = _state
    
    fun retryLoad() {
        when (state.value) {
            is ExampleState.LoadFailed -> {
                load()
            }
            
            ExampleState.FirstLoad, is ExampleState.Data, is ExampleState.Submitting -> {}
        }
    }
    
    fun update(key: ExampleKey, value: String) {
        val state = (_state.value as? ExampleState.Data)
        if (state != null) {
            _state.value = state.mutateUpdate(key, value)
        }
    }
    
    fun submit() {
        val s = (_state.value as? ExampleState.Data)
        if (s != null) {
            if (!s.submitButtonEnabled) {
                return
            }
            
            // just validate is not empty for simplicity
            val validation = mutableMapOf<ExampleKey, ValidationResult>()
            
            if (s.values.someThing.isBlank()) {
                validation + (ExampleKey.SomeThing to ValidationResult.Invalid)
            } else {
                validation + (ExampleKey.SomeThing to ValidationResult.Valid)
            }
    
            if (s.values.anotherThing.isBlank()) {
                validation + (ExampleKey.AnotherThing to ValidationResult.Invalid)
            } else {
                validation + (ExampleKey.AnotherThing to ValidationResult.Valid)
            }
            
            if (validation.values.any { it != ValidationResult.Valid }) {
                _state.value = s.mutateValidation(validation)
                return
            }
    
            val submitState = s.mutateStartSubmit()
    
            _state.value = submitState
    
            dispatcher.launchNonCancellable {
                val remoteData = simpleExampleSender(submitState.valuesToSubmit)
                _state.value = submitState.mutateSubmitFinished(remoteData)
            }
        }
    }
    
    /**
     * Restore values to their correspondent initial values
     */
    fun discard() {
        val s = (_state.value as? ExampleState.Data)
        if (s != null) {
            _state.value = s.mutateDiscard()
        }
    }
    
    /**
     * Jet Pack's method. We close our lifecycle here.
     */
    override fun onCleared() {
        lifecycle.close()
        super.onCleared()
    }
    
    init {
        load()
    }
    
    private fun load() {
        val state = ExampleState.FirstLoad
        _state.value = state
    
        // start coroutine to do some asynchronous work
        // "non cancellable" means we don't want it to be cancelled at the moment when `lifecycle` was ended
        // instead we let the scope to finish the work
        // (if lifecycle was already ended at this moment,
        // manuallyCancellableScope will return `null` and `launch` call will be skipped)
        dispatcher.launchNonCancellable {
        
            // Let's notice that we have to work carefully with the states here.
            // As we started the coroutine we have to consider that _state.value can be already changed to this time
        
            val remoteData = simpleExampleReceiver()
        
            remoteData.handle(
                { data ->
                    _state.value = state.mutateLoadSuccess(data)
                },
            
                { fail ->
                    _state.value = state.mutateLoadFailed(fail)
                }
            )
        }
    }
    
    private fun ExampleState.FirstLoad.mutateLoadSuccess(data: ExampleData): ExampleState.Data {
        return ExampleState.Data(
            initialValues = data,
            values = data,
            emptyMap(),
            null
        )
    }
    
    private fun ExampleState.FirstLoad.mutateLoadFailed(fail: RemoteData.Fail): ExampleState.LoadFailed {
        return ExampleState.LoadFailed(fail)
    }
    
    private fun ExampleState.LoadFailed.retryLoad(): ExampleState.FirstLoad {
        return ExampleState.FirstLoad
    }
    
    private fun ExampleState.Data.mutateUpdate(key: ExampleKey, value: String): ExampleState.Data {
        return ExampleState.Data(
            initialValues = initialValues,
            values = values.mutate(key, value),
            validationResult = validationResult - key,
            submitResult = null
        )
    }
    
    private fun ExampleState.Data.mutateDiscard(): ExampleState.Data {
        return ExampleState.Data(
            initialValues = initialValues,
            values = initialValues,
            validationResult = emptyMap(),
            submitResult = null
        )
    }
    
    private fun ExampleState.Data.mutateValidation(validation: Map<ExampleKey, ValidationResult>): ExampleState.Data {
        return ExampleState.Data(
            initialValues = initialValues,
            values = values,
            validationResult = validation,
            submitResult = submitResult
        )
    }
    
    private fun ExampleState.Data.mutateStartSubmit(): ExampleState.Submitting {
        return ExampleState.Submitting(
            initialValues = initialValues,
            valuesToSubmit = values
        )
    }
    
    private fun ExampleState.Submitting.mutateSubmitFinished(
        remoteData: RemoteData<SendResult<ExampleData, ExampleDataRejectedReason>>
    ): ExampleState.Data {
        val submitResult = remoteData.handle(
            {
                when (it) {
                    is SendResult.Data -> ExampleState.SubmitResult.Success
                    is SendResult.Rejected -> ExampleState.SubmitResult.ValidationFail(it.info)
                }
            },
    
            {
                ExampleState.SubmitResult.NetworkFail(it)
            }
        )
    
        return when (submitResult) {
            is ExampleState.SubmitResult.NetworkFail -> {
                ExampleState.Data(
                    initialValues = initialValues,
                    values = valuesToSubmit,
                    emptyMap(),
                    submitResult
                )
            }
    
            is ExampleState.SubmitResult.ValidationFail -> {
                ExampleState.Data(
                    initialValues = initialValues,
                    values = valuesToSubmit,
                    mapSubmitValidationToLocal(submitResult.info),
                    submitResult
                )
            }
        
            ExampleState.SubmitResult.Success -> {
                ExampleState.Data(
                    initialValues = valuesToSubmit,
                    values = valuesToSubmit,
                    emptyMap(),
                    submitResult
                )
            }
        }
    }
    
    private fun mapSubmitValidationToLocal(validation: ExampleDataRejectedReason): Map<ExampleKey, ValidationResult> {
        return when (validation) {
            ExampleDataRejectedReason.InvalidSomeThing -> mapOf(ExampleKey.SomeThing to ValidationResult.Invalid)
            ExampleDataRejectedReason.InvalidAnotherThing -> mapOf(ExampleKey.AnotherThing to ValidationResult.Invalid)
        }
    }
}

sealed class ExampleWithQueueState {
    
    data class FirstLoading(val inProgress: Boolean, val lastError: RemoteData.Fail?) : ExampleWithQueueState()
    
    data class Data(
        val initialValues: ExampleData,
        val remoteDataState: RemoteChangeableState<ExampleData>,
        val values: ExampleData,
        val validationResult: Map<ExampleKey, ValidationResult>,
        val submitting: Boolean,
    ) : ExampleWithQueueState() {
    
        val dataChanged: Boolean = initialValues != values
    
        val submitButtonEnabled: Boolean = dataChanged && !submitting
    }
}

/**
 * Example implementation where all changes are handled inside message (command) queue.
 */
class ExampleWithQueueModel : ViewModel() {
    
    /**
     * [ExampleReducerCommand] is all messages (commands) that can be enqueued.
     * ExampleCommand is subset of ExampleReducerCommand, they are commands that can be enqueued publicly
     * (from the outside of the class, see [submit])
     */
    sealed class ExampleCommand : ExampleReducerCommand {
    
        object RetryLoad : ExampleCommand()
        
        data class Update(val key: ExampleKey, val value: String) : ExampleCommand()
        
        object Submit : ExampleCommand()
    }
    
    private val lifecycle = RStaLifecycleDispatcher(injectDomainCoordinator())
    
    private val dispatcher = RStaCoroutineDispatcherFactory.create(
        RStaCoroutineLifecycle(
            lifecycle,
            injectDomainCoroutineContext()
        )
    )
    
    private val elapsedRealTimeSource: ElapsedRealTimeSource = injectElapsedRealTimeSource()
    
    private val repository: ExampleRepository = injectExampleRepository()
    
    private val _state = RStaValueDispatcher<ExampleWithQueueState>(
        lifecycle,
        ExampleWithQueueState.FirstLoading(
            inProgress = true,
            lastError = null
        )
    )
    
    val state: RStaValue<ExampleWithQueueState> = _state
    
    /**
     * Now we want to use a queue.
     * Each interaction with ExampleWithQueueModel is described by interaction-specific message
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
            val networkState: RemoteQueueHandler.State<ExampleNetworkingData, Unit, ExampleData>
        ) : ExampleReducerCommand
        
        // Other private commands can be here
        // data class OtherPrivateCommand() : ExampleReducerCommand
    }
    
    private val syncTarget = RemoteSyncTarget.InLast(600_000)
    
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
            when (val state = _state.value) {
                is ExampleWithQueueState.FirstLoading -> {
                    when (command) {
                        is ExampleCommand.RetryLoad -> {
                            when (_state.value) {
                                is ExampleWithQueueState.FirstLoading -> {
                                    // update if the last update was more than 10 minutes ago
                                    repository.sync(Unit, syncTarget)
                                }
                
                                is ExampleWithQueueState.Data -> {}
                            }
                        }
        
                        is ExampleReducerCommand.HandleRepositoryChange -> {
                            // we can use suspend functions here
                            val localRemoteState = repository.readLocalRemoteWithState(Unit)
                            val remoteState = RemoteChangeableState.fromLocalRemoteWithState(
                                localRemoteState,
                                Unit,
                                RemoteSyncTarget.Once,
                                elapsedRealTimeSource(),
                                {
                                    (localRemoteState.first.data.remoteResult as? RemoteData.Fail)
                                },
                                {
                                    false
                                }
                            )
                            
                            when (remoteState.remote) {
                                is RemoteState.NotSynced -> {
                                    if (!remoteState.remote.inProgress) {
                                        // Here is an example of handling when we want to do something that requires a lot of time.
                                        // We don't want to delay execution of subsequent commands so we start a separate coroutine.
                                        // As we use the same dispatcher we used as a parameter for RStaBaseMessageQueueHandlerImpl
                                        // and we know that the CoroutineContext for the dispatcher
                                        // is configured to use a fixed thread for all coroutines,
                                        // we know it will not create concurrency problems, because this lambda
                                        // will be enqueued to be executed on the same thread.
                                        dispatcher.launchNonCancellable {
                                            delay(10000)
        
                                            // However in this scope (inside the launch lambda) we shouldn't update the state
                                            // or else we will introduce unnecessary concurrent complexity.
                                            // We just make a call to the repository
                                            // and because we listen all the changes of it
                                            // we will handle the result of the call there
        
                                            // Here we enqueue sync command to the repository.
                                            // If the repository will already have sync command for the same key,
                                            // it will not lead to excessive networking because we have specified RemoteSyncTarget.Once parameter
                                            repository.sync(Unit, syncTarget)
                                        }
                                    }
                                }
                                
                                is RemoteState.Synced -> {
                                    when (remoteState) {
                                        is RemoteChangeableState.Data -> {
                                            _state.value = state.mutateOnRepositoryData(remoteState)
                                        }
                                        
                                        is RemoteChangeableState.NoData -> {
                                            // do nothing
                                        }
                                    }
                                }
                            }
                            
                            // if (nextValue == null) {
                            //
                            // } else {
                            //     _state.value =
                            //         ExampleState(
                            //             loaded(command.networkState),
                            //             synced(command.networkState),
                            //             command.networkState.data.remoteResult,
                            //             _state.value.someThing,
                            //             _state.value.anotherThing,
                            //         )
                            //             .mutateExampleValue(command.key, nextValue)
                            // }
                        }
    
                        is ExampleCommand.Update -> {}
                        is ExampleCommand.Submit -> {}
                    }
                }
        
                is ExampleWithQueueState.Data -> {
                    when (command) {
                        is ExampleCommand.RetryLoad -> {}
        
                        is ExampleCommand.Update -> {
                            _state.value = state.mutateUpdate(command.key, command.value)
                        }
        
                        is ExampleReducerCommand.HandleRepositoryChange -> {
                            val localRemoteState = repository.readLocalRemoteWithState(Unit)
                            val remoteState = RemoteChangeableState.fromLocalRemoteWithState(
                                localRemoteState,
                                Unit,
                                RemoteSyncTarget.Once,
                                elapsedRealTimeSource(),
                                {
                                    (localRemoteState.first.data.remoteResult as? RemoteData.Fail)
                                },
                                {
                                    false
                                }
                            )
                            
                            when (remoteState) {
                                is RemoteChangeableState.Data -> {
                                    _state.value = state.mutateOnRepositoryChanged(remoteState)
                                }
                                
                                is RemoteChangeableState.NoData -> {}
                            }
                        }
                        
                        ExampleCommand.Submit -> {
                            if (state.dataChanged) {
                                // just validate is not empty for simplicity
                                val validation = mutableMapOf<ExampleKey, ValidationResult>()
    
                                if (state.values.someThing.isBlank()) {
                                    validation + (ExampleKey.SomeThing to ValidationResult.Invalid)
                                } else {
                                    validation + (ExampleKey.SomeThing to ValidationResult.Valid)
                                }
    
                                if (state.values.anotherThing.isBlank()) {
                                    validation + (ExampleKey.AnotherThing to ValidationResult.Invalid)
                                } else {
                                    validation + (ExampleKey.AnotherThing to ValidationResult.Valid)
                                }
    
                                _state.value = state.mutateValidation(validation)
                                
                                if (validation.values.all { it == ValidationResult.Valid }) {
                                    repository.write(Unit, state.values)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // listen repository's changes
        repository.changeSource.listen(lifecycle) { key ->
            // we handle the changes here by adding appropriate commands to the queue
            reducer.add(ExampleReducerCommand.HandleRepositoryChange(repository.state.value))
        }
    
        // do something once when ExampleWithQueueModel is instantiated
        reducer.add(ExampleCommand.RetryLoad)
    }
    
    private fun ExampleWithQueueState.FirstLoading.mutateOnRepositoryData(
        remoteData: RemoteChangeableState.Data<ExampleData>
    ): ExampleWithQueueState.Data {
        return ExampleWithQueueState.Data(
            initialValues = remoteData.local,
            remoteDataState = remoteData,
            values = remoteData.local,
            validationResult = emptyMap(),
            submitting = submitting(remoteData)
        )
    }
    
    private fun ExampleWithQueueState.FirstLoading.mutateOnRepositoryNoData(
        remoteData: RemoteChangeableState.NoData<ExampleData>
    ): ExampleWithQueueState.FirstLoading {
        return ExampleWithQueueState.FirstLoading(
            remoteData.state.inProgress,
            remoteData.state.lastError
        )
    }
    
    private fun ExampleWithQueueState.Data.mutateOnRepositoryChanged(
        remoteData: RemoteChangeableState.Data<ExampleData>
    ): ExampleWithQueueState.Data {
        if (submitting && !submitting(remoteData)) {
            return ExampleWithQueueState.Data(
                initialValues = if (remoteData.aheadInfo == null) remoteData.local else initialValues,
                remoteDataState = remoteData,
                values = remoteData.local,
                validationResult = validationResult,
                submitting = false
            )
        }
        
        return ExampleWithQueueState.Data(
            initialValues = initialValues,
            remoteDataState = remoteData,
            values = remoteData.local,
            validationResult = validationResult,
            submitting = submitting(remoteData)
        )
    }
    
    private fun ExampleWithQueueState.Data.mutateUpdate(key: ExampleKey, value: String): ExampleWithQueueState.Data {
        return ExampleWithQueueState.Data(
            initialValues = initialValues,
            remoteDataState = remoteDataState,
            values = values.mutate(key, value),
            validationResult = validationResult - key,
            submitting = submitting
        )
    }
    
    private fun ExampleWithQueueState.Data.mutateValidation(validation: Map<ExampleKey, ValidationResult>): ExampleWithQueueState.Data {
        return ExampleWithQueueState.Data(
            initialValues = initialValues,
            remoteDataState = remoteDataState,
            values = values,
            validationResult = validation,
            submitting = submitting
        )
    }
    
    private fun submitting(remoteData: RemoteChangeableState<ExampleData>): Boolean {
        return remoteData.aheadInfo?.inProgress == true
    }
}

// Should be in domain layer
interface ExampleRepository : WritableSimpleRemoteDataSource<ExampleNetworkingData, Unit, ExampleData>

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
    exampleLocalRepository: Repository.Writable<Unit, ExampleData>,
    
    // local cache for "locally ahead" values (see WritableSimpleRemoteDataSource for details)
    exampleLocalCommandsRepository: Repository.Writable<Unit, List<Pair<Unit, ExampleData>>>,
    
    // provides access to send and receive data to/from the server
    exampleRemoteRepository: DirectRemoteRepository.SenderReceiver<Unit, ExampleData, SendResult<ExampleData, ExampleDataRejectedReason>>
): ExampleRepository {
    // See WritableSimpleRemoteDataSource
    val exampleRepository = WritableSimpleRemoteDataSource<ExampleNetworkingData, Unit, ExampleData>(
        dataLayerCoroutineLifecycle,
        elapsedRealTimeSource,
        exampleLocalRepository,
        exampleLocalCommandsRepository,
        ExampleNetworkingData(null),
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
                exampleRemoteRepository.get(Unit)
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
                exampleRemoteRepository.send(Unit, command.command)
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
                remoteResult = remoteData
            )
    }
    
    return object :
        ExampleRepository,
        WritableSimpleRemoteDataSource<ExampleNetworkingData, Unit, ExampleData> by exampleRepository {}
}

// Example of repository state (or more specifically it is a part of repository's state called `data`)
// In the example it holds the information about the last networking to provide feedback to user when something goes wrong
data class ExampleNetworkingData(
    val remoteResult: RemoteData<SendResult<Unit, ExampleDataRejectedReason>>?
)

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

enum class ValidationResult {
    NotValidated, Empty, Invalid, Valid
}

// Example of specific Rejected type for SendResult.
enum class ExampleDataRejectedReason { InvalidSomeThing, InvalidAnotherThing }

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

fun injectSimpleExampleReceiver(): suspend () -> RemoteData<ExampleData> {
    // Let's imagine that we have provided an ExampleRepository
    TODO()
}

fun injectSimpleExampleSender(): suspend (ExampleData) -> RemoteData<SendResult<ExampleData, ExampleDataRejectedReason>> {
    // Let's imagine that we have provided an ExampleRepository
    TODO()
}

fun injectElapsedRealTimeSource(): ElapsedRealTimeSource {
    return object : ElapsedRealTimeSource {
        override fun invoke(): Long {
            return SystemClock.elapsedRealtime()
        }
    }
}

fun injectExampleRepository(): ExampleRepository {
    // Let's imagine that we have provided an ExampleRepository
    TODO()
}