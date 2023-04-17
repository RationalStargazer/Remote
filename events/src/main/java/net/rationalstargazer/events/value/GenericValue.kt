package net.rationalstargazer.events.value

/**
 * Represents `strict` value source which is a source [value source][RStaGenericValue] that notifies about all changes soon after change is happened.
 * Strict value sources always pass the value of [value][RStaGenericValue.value] at the moment of event as `eventData` parameter
 * (thus value sources always have the same type for their `Value` and `Event` type parameters).
 *
 * Strict value sources are sources you want to listen to if every value is important no matter how often they are changed.
 *
 * See [RStaValueSource] for details about `relaxed` value sources.
 *
 * See [RStaGenericValue] for details about `value sources`.
 */
interface RStaValue<T> : RStaGenericValue<T, T>

/**
 * Represents `relaxed` (or `lazy`) value source which is a source that notifies about its changes only when it sees it is appropriate.
 * Not every change of `value` will trigger a notification of listeners.
 *
 * Relaxed (lazy) value sources are often sources that represent continuous values that change smoothly over time
 * and notify only about some important changes of their state according to their inner logic.
 *
 * For example `val timerSeconds: RStaGenericValue<Float, Boolean>` can represent the timer.
 * In such example `timerSeconds` can notify its listeners when it was started (`eventData == true`) or stopped (`eventData == false`).
 *
 * Another example is a value that reflects some complicated parameter that you want to be evaluated lazily.
 *
 * For example `val squareOfVeryComplexFigure: RStaValueSource<Double>` can use resource-consuming formula.
 * Instead of having to calculate the `value` every time the underlying figure has changed its shape
 * (possibly dozen of times per second in the case of animation)
 * it only have to calculate the `value` on demand when it is really needed (for example one time per second).
 *
 * `squareOfVeryComplexFigure` can notify its listeners from time to time that the value was somehow changed.
 * It doesn't need to really calculate the value to notify.
 * Often such sources pass `Unit` as `eventData` parameter.
 */
typealias RStaValueSource<T> = RStaGenericValue<T, Any?>

/**
 * Represents a `Value` that can be changed over time.
 *
 * `Value` type parameter indicates a type of [value].
 * `Event` type parameter indicates a type of change event.
 * See [RStaValue] and [RStaValueSource] about `strict` and `relaxed` (lazy) value sources.
 *
 * `net.rationalstargazer.events` package promotes the concept of "pulling data" (not "pushing data").
 *
 * Concept of "push data" has a lot of advantages, especially when the process is asynchronous in its nature.
 * At the same time "push data" means different parts of an application works with values that represent states of the past,
 * and it can be that for different handlers they are different values from different past times.
 * Interacting with each other the handlers can produce inconsistent state.
 *
 * To explain it in more details let us examine the work of common producer-consumer pattern
 * Imagine that we have `Producer<State>` and few `Consumer<State>` working on a single thread.
 * When `Producer` pushes next `state` to its consumers it starts from the first `Consumer`, then the next one, then next, and so on...
 * It means that there is a point in time when from the first Consumer's point of view the state is different than for the second one.
 * Now let's imagine that each consumer is also a producer,
 * first Consumer produces StateA, and the second one produces StateB which conceptually are different aspects of a State.
 * These StateA and StateB then handled as a single pair (`Pair<StateA, StateB>`) somewhere later.
 * In a straightforward implementation it can lead to a situation when value of `Pair` is actually inconsistent
 * because value of StateA and value of StateB were originated from different states.
 *
 * Now let's examine a different implementation using the concept of "pulling data".
 *
 * The basic idea is that sources of data will generate events to notify their listeners about changes,
 * but it will be the listeners who will get data from sources.
 *
 * Let's illustrate it with [RStaValueSource]<State>.
 *
 * ```
 *     val stateSource: RStaValueSource<State> = injectStateSource()
 * ```
 *
 * We also want to have two intermediate handlers which handle incoming `State` and produce `StateA` and `StateB` respectively.
 *
 * In our implementation they will be represented by instances of [RStaDynamicValue] type
 * (not because it is the best way to implement it in a real application but because `RStaDynamicValue` is the most fundamental implementation of [RStaValueSource] which helps to illustrate basic concepts).
 * To keep the example simple we will not instantiate `RStaDynamicValue` directly but imagine that we have `createExampleDynamicValue` method.
 * The advantage of the imaginary method is that it only requires to provide a [value] getter for the future instance
 * (in reality `RStaDynamicValue` also requires to provide` the implementation of [checkValue]).
 *
 * ```
 *     val stateASource: RStaDynamicValue<StateA> = createExampleDynamicValue<StateA>(
 *         // provide getter for stateASource.value
 *         valueGetter = {
 *             // imagine we handled stateSource.value and returned something smart here
 *             StateA(stateSource.value)
 *         }
 *     )
 *
 *     // now stateASource can provide actual value every time but it also should notify its listeners about changes
 *     // to do this we listen changes of stateSource.value and propagate change event to our listeners
 *     stateSource.listen(RStaValueEventSource.Invoke.No, lifecycle) {
 *         stateASource.notify(Unit)  // will call listeners of stateASource
 *     }
 *
 *     // the same for stateBSource
 *     val stateBSource: RStaValue<StateB>
 * ```
 *
 * Finally we also want to have the final element which maps both StateA and StateB to a single `StateAB`.
 *
 * ```
 *     val stateABSource: RStaDynamicValue<StateAB> = createExampleDynamicValue(
 *         // getter for stateABSource.value
 *         valueGetter = {
 *             // imagine we handled stateASource.value and stateBSource.value and returned something smart here
 *             StateAB(stateASource.value, stateBSource.value)
 *         }
 *     )
 *
 *     // to notify listeners of stateABSource about changes we will listen our sources and propagate change events to our listeners
 *     // 1.
 *     stateASource.listen(RStaValueEventSource.Invoke.No, lifecycle) {
 *         stateABSource.notify(Unit) // will call listeners of stateABSource
 *     }
 *     // 2.
 *     stateBSource.listen(RStaValueEventSource.Invoke.No, lifecycle) {
 *         stateABSource.notify(Unit) // will call listeners of stateABSource
 *     }
 * ```
 *
 * Let's highlight main points.
 *
 * An event change is not used to propagate a data.
 * Instead it is used to just notify listeners about changes.
 * A data itself is obtained and calculated not at the time of the creation of event but at the time when a listener is reading it from the source.
 * To calculate `stateABSource.value` we use direct calls to `stateASource.value` and `stateBSource.value`.
 * They in turn use direct calls to `stateSource.value`.
 
 * So events are pushed to listeners, data is pulled from a source.
 * This way we always have consistent values every time regardless of pipelines' complexities and interdependencies between them.
 *
 * Note that as we always use direct function calls to get data and we work inside a single thread
 * we can use any references we want to get all we need
 * (as opposed to producer-consumer pattern where all data are somehow pushed to a pipeline).
 */
interface RStaGenericValue<out Value : Event, out Event> : RStaValueEventSource<Event> {

    /**
     * Standard implementations call `checkValue` internally (to maintain the cache) so you don't need to do it yourself.
     *
     * Details:
     *
     * Every call of `checkValue` returns the same value as long as [value] remains the same.
     * If [value] has changed since the last call the returned value will be different.
     * The returned value is not the same as hash code.
     * The same [value] can correspond to a different result of 'checkValue' in different or even the same runs.
     *
     * val i: RStaGenericValue<Int, Int>
     *
     * after `i.value` was changed to 10 `checkValue` can return 1
     *
     * after `i.value` was changed to 100 `checkValue` for example can return 2
     *
     * after `i.value` was changed back to 10 `checkValue` can return 3
     *
     * Other implementations can choose to have the same `checkValue` result for the same [value]s:
     *
     * val i: RStaGenericValue<Int, Int>
     *
     * after `i.value` was changed to 10 `checkValue` can return 10
     *
     * after `i.value` was changed to 100 `checkValue` for example can return 100
     *
     * after `i.value` was changed back to 10 `checkValue` can return 10
     */
    fun checkValue(): Long

    /**
     * Value.
     * If the `value` conceptually represents some dependent value
     * (as `squareOfCircle.value` depends on `circleRadius.value`)
     * it is recommended to not store the `value` but calculate it every time using `circleRadius.value`.
     *
     * See [RStaGenericValue] for details about concept of "pulling data".
     */
    val value: Value
}

//fun demo() {
//    class State
//    class StateA(val state: State)
//    class StateB(val state: State)
//
//    val lifecycle = LifecycleDispatcher(
//        RStaEventsQueueDispatcherFactory.createEventsQueue(RStaAndroidLooperHandler(Handler(Looper.getMainLooper())))!!
//    )
//
//    fun injectStateValue(): RStaValue<State> {
//        return RStaValueDispatcher(lifecycle, State()) {}
//    }
//
//    fun <T> createValueSource(valueGetter: () -> T): RStaDynamicValue<T, Any?> {
//        return RStaDynamicValue(lifecycle, { 0 }, valueGetter)
//    }
//
//    val stateSource: RStaValue<State> = injectStateValue()
//
//    // handler that produces StateA
//    stateSource.listen(RStaValueEventSource.Invoke.No, lifecycle) { it: State ->
//        StateA(it)  // imagine we have calculated something
//    }
//
//    stateSource.listen(RStaValueEventSource.Invoke.No, lifecycle) { it: State ->
//        StateB(it)
//    }
//
//    val stateASource: RStaDynamicValue<StateA, Any?> = createValueSource(
//        valueGetter = {
//            // imagine we have handled it somehow
//            StateA(stateSource.value)
//        }
//    )
//
//    stateSource.listen(RStaValueEventSource.Invoke.No, lifecycle) { it: Any? ->
//        stateASource.notifyChanged(Unit)
//    }
//
//    val stateBSource = RStaDynamicValue<StateB, Any?>(lifecycle, stateSource::checkValue) {
//        StateB(stateSource.value)
//    }
//
//    stateSource.listen(RStaValueEventSource.Invoke.No, lifecycle) { it: Any? ->
//        stateBSource.notifyChanged(it)
//    }
//
//    var stateAB_aGeneration = 0L
//    var stateAB_bGeneration = 0L
//    var stateAB_resultGeneration = 0L
//
//    val stateABSource = RStaDynamicValue<Pair<StateA, StateB>, Any?>(
//        lifecycle,
//        {
//            val a = stateASource.checkValue()
//            val b = stateBSource.checkValue()
//            if (a != stateAB_aGeneration || b != stateAB_bGeneration) {
//                stateAB_aGeneration = a
//                stateAB_bGeneration = b
//                stateAB_resultGeneration++
//            }
//
//            stateAB_resultGeneration
//        },
//        {
//            Pair(stateASource.value, stateBSource.value)
//        }
//    )
//}