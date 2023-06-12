package net.rationalstargazer.networking

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import net.rationalstargazer.events.lifecycle.RStaCoroutineDispatcherFactory
import net.rationalstargazer.events.lifecycle.RStaCoroutineLifecycle
import net.rationalstargazer.events.lifecycle.RStaLifecycleDispatcher
import net.rationalstargazer.events.queue.RStaEventsQueueDispatcher
import net.rationalstargazer.events.value.RStaValue
import net.rationalstargazer.events.value.RStaValueDispatcher
import net.rationalstargazer.remote.RemoteData
import net.rationalstargazer.remote.RemoteData.Companion.handle
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Example of my skills.
 */

/**
 * Example data we will work with. Let's do something with it.
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
 * The state that describes the example screen.
 */
sealed class ExampleState {
    
    /**
     * Essential data is here
     */
    abstract val data: Data?
    
    /**
     * Initial loading is tracked here.
     * View layer can use it to show loading animation.
     * firstLoadingState.lastResult can be used to display what exactly went wrong (for example no internet)
     */
    abstract val firstLoadingState: NetworkingState<Unit>
    
    /**
     * Sending to the server is tracked here. View layer can use it to show submitting animation.
     */
    abstract val submittingState: SubmitNetworkingState<ExampleDataRejectedReason>
    
    val networkingInProgress: Boolean
        get() = firstLoadingState.inProgress || submittingState.inProgress
    
    val submitButtonEnabled: Boolean
        get() {
            val d = data
            if (d == null || networkingInProgress) {
                return false
            }
            
            return d.input != d.initialValues
        }
    
    /**
     * No data to display yet
     */
    sealed class FirstLoading : ExampleState() {
    
        final override val data: Nothing? = null
        
        final override val submittingState: SubmitNetworkingState<ExampleDataRejectedReason> =
            SubmitNetworkingState(
                false,
                null
            )
    
        data class InProgress(val inited: Boolean) : FirstLoading() {
            override val firstLoadingState: NetworkingState<Unit> = NetworkingState(true, null)
        }
        
        data class Failed(val fail: RemoteData.Fail) : FirstLoading() {
            override val firstLoadingState: NetworkingState<Unit> = NetworkingState(false, fail)
        }
    }
    
    /**
     * Data to display and edit.
     */
    sealed class Input : ExampleState() {
        
        abstract override val data: Data
    
        final override val firstLoadingState: NetworkingState<Unit> = NetworkingState(false, RemoteData.Data(Unit))
    
        /**
         * Data was sent to the server.
         */
        data class Saved(val initialValues: ExampleData) : Input() {
            override val data: Data = Data(
                initialValues = initialValues,
                input = initialValues,
                validationResult = emptyMap(),
            )
    
            override val submittingState: SubmitNetworkingState<ExampleDataRejectedReason> =
                SubmitNetworkingState(false, SubmitNetworkingState.SubmitResult.Success)
        }
    
        /**
         * User changed the data, data is not saved.
         */
        data class New(
            override val data: Data,
            val lastSubmitResult: SubmitNetworkingState.SubmitResult<ExampleDataRejectedReason>?
        ) : Input() {
            override val submittingState: SubmitNetworkingState<ExampleDataRejectedReason> =
                SubmitNetworkingState(false, lastSubmitResult)
        }
    }
    
    /**
     * Sending to the server.
     */
    data class Submitting(
        val initialValues: ExampleData,
        val valuesToSubmit: ExampleData
    ) : ExampleState() {
        override val data: Data = Data(
            initialValues,
            valuesToSubmit,
            emptyMap()
        )
        
        override val firstLoadingState: NetworkingState<Unit> = NetworkingState(false, RemoteData.Data(Unit))
        
        override val submittingState: SubmitNetworkingState<ExampleDataRejectedReason> =
            SubmitNetworkingState(true, null)
    }
    
    data class Data(
        /**
         * Original values (before the edit)
         */
        val initialValues: ExampleData,
        
        /**
         * Current values
         */
        val input: ExampleData,
        
        val validationResult: Map<ExampleKey, ValidationResult>,
    )
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
     * see explanations in [RStaEventsQueueDispatcher] (https://github.com/RationalStargazer/events))
     */
    private val lifecycle = RStaLifecycleDispatcher(injectDomainCoordinator())
    
    /**
     * Coroutine dispatcher provides an ability to start new coroutine at any time, outside of current CoroutineScope.
     * Note the lifecycle that is provided as a parameter,
     * when the lifecycle will be finished no new coroutines will be provided
     * and those that were created with launchAutoCancellable() will be closed automatically.
     * (used internally in some of my implementations)
     */
    private val dispatcher = RStaCoroutineDispatcherFactory.create(
        RStaCoroutineLifecycle(
            lifecycle,
            // CoroutineContext to use for the creation of coroutines.
            // For simplicity it is supposed that it is always Dispatchers.Main
            // In most cases it is more than enough.
            // (CoroutineContext that is bound to another single thread is also possible if performance is important,
            // but additional thread switching adapter will be required)
            injectDomainCoroutineContext()
        )
    )
    
    // An abstraction of server calls
    private val simpleExampleReceiver = injectSimpleExampleReceiver()
    private val simpleExampleSender = injectSimpleExampleSender()
    
    /**
     * See [state]
     */
    private val _state = RStaValueDispatcher<ExampleState>(
        lifecycle,
        // Initial state
        ExampleState.FirstLoading.InProgress(false)
    )
    
    /**
     * Holds the value and notifies its listeners when the value has changed.
     * See [net.rationalstargazer.events.value.RStaGenericValue] for details.
     * (https://github.com/RationalStargazer/events)
     * Not production-ready but in most situations it would be better than LiveData, RxJava or Flow.
     * (I'm working on it for now)
     */
    val state: RStaValue<ExampleState> = _state
    
    /**
     * Try to load the data again (if it isn't loaded)
     */
    fun retryLoad() {
        whenState(_state.value, ExampleState.FirstLoading::class) {
            val startedState = it.mutateStartLoadOrNull()
            if (startedState == null) {
                return
            }
            
            _state.value = startedState
    
            // start coroutine to do some asynchronous work
            // "non cancellable" means we don't want it to be cancelled at the moment when `lifecycle` was ended
            // instead we let the scope to finish the work
            // (if lifecycle was already ended at this moment, the code block will be skipped)
            // logic inside launchNonCancellable() can track the completion of started coroutines to simplify tests
            // (it is supposed that one will not try to hang coroutines started with launchAutoCancellable and launchNonCancellable
            // (for example with `awaitCancellation`), it would complicate the tests)
            dispatcher.launchNonCancellable {
                val remoteData = simpleExampleReceiver()
    
                // Let's notice that we have to work carefully with the states here.
                // As we started the coroutine we have to consider that _state.value can be already changed to this time
                whenState(_state.value, ExampleState.FirstLoading.InProgress::class) { state ->
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
        }
    }
    
    /**
     * Changes the data
     */
    fun update(key: ExampleKey, value: String) {
        whenState(_state.value, ExampleState.Input::class) {
            _state.value = it.mutateUpdate(key, value)
        }
    }
    
    /**
     * Submits new data to the server
     */
    fun submit() {
        whenState(_state.value, ExampleState.Input.New::class) { state ->
            if (!state.submitButtonEnabled) {
                return
            }
    
            // just validate it is not empty for simplicity
            
            val someThingValidation = if (state.data.input.someThing.isBlank()) {
                ValidationResult.Empty
            } else {
                ValidationResult.Valid
            }
    
            val anotherThingValidation = if (state.data.input.anotherThing.isBlank()) {
                ValidationResult.Empty
            } else {
                ValidationResult.Valid
            }
            
            val validated = state
                .mutateValidation(ExampleKey.SomeThing, someThingValidation)
                .mutateValidation(ExampleKey.AnotherThing, anotherThingValidation)
            
            _state.value = validated
            
            val maybeValid = validated.toLocallyValidInputOrNull()
            if (maybeValid != null) {
                _state.value = maybeValid.mutateStartSubmit()
    
                dispatcher.launchNonCancellable {
                    val remoteData = simpleExampleSender(maybeValid.input)
    
                    whenState(_state.value, ExampleState.Submitting::class) {
                        _state.value = it.mutateSubmitFinished(remoteData)
                    }
                }
            }
        }
    }
    
    /**
     * Restore values to their correspondent initial values
     */
    fun discard() {
        whenState(_state.value, ExampleState.Input.New::class) {
            _state.value = it.mutateDiscard()
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
        // first load here
        retryLoad()
    }
    
    // From here onward: the extension functions that describes all possible ways to turn one state into another.
    // Each of them returns appropriate meaningful result in all situations.
    // This way we can enforce that only correct transformations will take place.
    
    private fun ExampleState.FirstLoading.mutateStartLoadOrNull(): ExampleState.FirstLoading.InProgress? {
        return ExampleState.FirstLoading.InProgress(true)
            .takeIf {
                when (this) {
                    is ExampleState.FirstLoading.InProgress -> !this.inited
                    is ExampleState.FirstLoading.Failed -> true
                }
            }
    }
    
    private fun ExampleState.FirstLoading.InProgress.mutateLoadSuccess(data: ExampleData): ExampleState.Input.Saved {
        return ExampleState.Input.Saved(initialValues = data)
    }
    
    private fun ExampleState.FirstLoading.InProgress.mutateLoadFailed(fail: RemoteData.Fail): ExampleState.FirstLoading.Failed {
        return ExampleState.FirstLoading.Failed(fail)
    }
    
    private fun ExampleState.Input.mutateUpdate(key: ExampleKey, value: String): ExampleState.Input.New {
        val data = ExampleState.Data(
            initialValues = data.initialValues,
            input = data.input.mutate(key, value),
            validationResult = data.validationResult - key,
        )
        
        return ExampleState.Input.New(data, submittingState.lastResult)
    }
    
    private fun ExampleState.Input.New.mutateDiscard(): ExampleState.Input.Saved {
        return ExampleState.Input.Saved(initialValues = data.initialValues)
    }
    
    private fun ExampleState.Input.mutateValidation(key: ExampleKey, value: ValidationResult?): ExampleState.Input.New {
        return ExampleState.Input.New(
            data = ExampleState.Data(
                initialValues = data.initialValues,
                input = data.input,
                validationResult = if (value != null) {
                    data.validationResult + (key to value)
                } else {
                    data.validationResult - key
                }
            ),
            
            lastSubmitResult = submittingState.lastResult
        )
    }
    
    private fun ExampleState.Input.New.toLocallyValidInputOrNull(): LocallyValidInput? {
        val valid = ExampleKey.values().all { key ->
            data.validationResult[key] == ValidationResult.Valid
        }
        
        if (!valid) {
            return null
        }
    
        return LocallyValidInput(initialValues = data.initialValues, input = data.input)
    }
    
    private fun LocallyValidInput.mutateStartSubmit(): ExampleState.Submitting {
        return ExampleState.Submitting(
            initialValues = initialValues,
            valuesToSubmit = input
        )
    }
    
    private fun ExampleState.Submitting.mutateSubmitFinished(
        remoteData: RemoteData<SendResult<ExampleData, ExampleDataRejectedReason>>
    ): ExampleState.Input {
        val submitState = remoteData.handle(
            {
                when (it) {
                    is SendResult.Data -> SubmitNetworkingState.SubmitResult.Success
                    is SendResult.Rejected -> SubmitNetworkingState.SubmitResult.Fail.Rejected(it.info)
                }
            },
    
            {
                SubmitNetworkingState.SubmitResult.Fail.Network(it)
            }
        )
    
        return when (submitState) {
            is SubmitNetworkingState.SubmitResult.Success -> {
                ExampleState.Input.Saved(initialValues = valuesToSubmit)
            }
    
            is SubmitNetworkingState.SubmitResult.Fail.Rejected -> {
                ExampleState.Input.New(
                    ExampleState.Data(
                        initialValues = initialValues,
                        input = valuesToSubmit,
                        validationResult = mapSubmitValidationToLocal(submitState.rejected)
                    ),
                    SubmitNetworkingState.SubmitResult.Fail.Rejected(submitState.rejected)
                )
            }
    
            is SubmitNetworkingState.SubmitResult.Fail.Network -> {
                ExampleState.Input.New(
                    ExampleState.Data(
                        initialValues = initialValues,
                        input = valuesToSubmit,
                        validationResult = emptyMap(),
                    ),
                    SubmitNetworkingState.SubmitResult.Fail.Network(submitState.fail)
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
    
    private inline fun <reified T : ExampleState> whenState(value: ExampleState, type: KClass<T>, block: (T) -> Unit) {
        if (value is T) {
            block(value)
        }
    }
    
    private data class LocallyValidInput(val initialValues: ExampleData, val input: ExampleData)
}

data class NetworkingState<out T>(val inProgress: Boolean, val lastResult: RemoteData<T>?)

data class SubmitNetworkingState<out T>(val inProgress: Boolean, val lastResult: SubmitResult<T>?) {
    sealed class SubmitResult<out T> {
        object Success : SubmitResult<Nothing>()
        
        sealed class Fail<out T> : SubmitResult<T>() {
            data class Network(val fail: RemoteData.Fail) : Fail<Nothing>()
            data class Rejected<out T>(val rejected: T) : Fail<T>()
        }
    }
}

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
    Empty, Invalid, Valid
}

// Example of specific Rejected type for SendResult.
enum class ExampleDataRejectedReason { InvalidSomeThing, InvalidAnotherThing }

// Interface for the source of current elapsed real time.
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