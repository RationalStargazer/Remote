package net.rationalstargazer.events.value

/**
 * Represents `strict` value source which is a [value source][RStaGenericValue] that notifies about all changes soon after change is happened.
 * Strict value sources always pass the [value][RStaGenericValue.value] as it was at the moment of event as `eventData` parameter
 * (thus value sources always have the same type for their `Value` and `Event` type parameters).
 *
 * Strict value sources are sources you want to listen to if every value is important no matter how often they are changed.
 *
 * See [RStaValueSource] for details about `relaxed` value sources.
 *
 * See [RStaGenericValue] for details about `value sources`.
 */
interface RStaValue<out T> : RStaGenericValue<T, T>

interface RStaVariable<T> : RStaValue<T> {
    override var value: T
}

fun <T> RStaGenericValue<T, T>.considerStrictValue(): RStaValue<T> {
    class SupposedlyStrict(base: RStaGenericValue<T, T>) : RStaValue<T>, RStaGenericValue<T, T> by base

    if (this is RStaValue<T>) {
        return this
    }

    return SupposedlyStrict(this)
}

/**
 * Represents `relaxed` (or `lazy`) [value source][RStaGenericValue] which is a source that notifies about its changes only when it sees it is appropriate.
 * Not every change of `value` will trigger a notification of listeners.
 *
 * Relaxed (lazy) value sources are often sources that represent continuous values that change smoothly over time
 * and notify only about some important changes of their state according to their inner logic.
 *
 * For example `val timerSeconds: RStaValueSource<Float>` can represent the timer.
 * In such example `timerSeconds` can notify its listeners when it was started and stopped (with `eventData == Unit`).
 *
 * Another example is a value that reflects some complicated parameter that you want to be evaluated lazily.
 *
 * For example `val squareOfVeryComplexFigure: RStaValueSource<Double>` can use resource-consuming logic.
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
 * Standard implementations are:
 *
 * [RStaValueDispatcher] - value that can be changed by the owner
 *
 * [RStaValueGenericConsumer] - like `RStaValueDispatcher` but more flexible
 *
 * [RStaDerivedValue] - value which is computed based on multiple other values.
 *
 * [RStaDynamicValue] - value which is computed at every reading.
 *
 * [RStaValueGenericConsumer] - like `RStaValueDispatcher` but more flexible
 *
 * [RStaMappedValue] - value that is a map of (a result of a calculation) another value
 *
 * [RStaChainGenericItem] - value that depends on another value (like `RStaMappedValue` but more flexible)
 *
 * [RStaCombinedGenericItem], [RStaCombinedStrictItem], - value that depends on other values (like `RStaChainGenericItem` but depends on multiple values)
 *
 * `Value` type parameter indicates a type of [value].
 * `Event` type parameter indicates a type of change event (type of `eventData` parameter that is passed to a listener).
 * See [RStaValue] and [RStaValueSource] about `strict` and `relaxed` (lazy) value sources.
 *
 * `net.rationalstargazer.events` package promotes the concept of "pulling data" (not "pushing data").
 *
 * Concept of "push data" has a lot of advantages, especially when the process is asynchronous in its nature.
 * At the same time "push data" means different parts of an application works with values that represent states of the past,
 * and it is possible that different handlers will handle different values from different past times.
 * In complex pipelines of interdependent handlers it can lead to inconsistent result.
 *
 * To explain it in more details let us examine the work of common producer-consumer pattern
 * Imagine that we have `Producer<State>` and few `Consumer<State>` working on a single thread.
 * When `Producer` pushes next `state` to its consumers it starts from the first `Consumer`, then the next one, then next, and so on...
 * It means that there is a point in time when from the first Consumer's point of view the state is different than for the second one.
 * Now let's imagine that each consumer is also a producer,
 * first Consumer produces StateA, and the second one produces StateB which conceptually are different aspects of a State.
 * These StateA and StateB then handled as a single pair (`Pair<StateA, StateB>`) somewhere later.
 * In a straightforward implementation it can lead to a situation when value of `Pair` is actually inconsistent
 * because value of StateA and value of StateB were originated from different State.
 *
 * According to "pulling data" principle the same data flow can be represented by [RStaValue]<State>,
 * [RStaMappedValue]<StateA, State>,
 * RStaMappedValue<StateB, State>, and
 * RStaValue<Pair<StateA, StateB>> created by [RStaCombinedStrictItem] function.
 *
 * In this data flow change event is not used to propagate a data.
 * Instead it is used to just notify listeners about changes.
 * A data itself is obtained and calculated not at the time of the creation of event but at the time when a listener is reading it from the source.
 * To calculate `value` of RStaValue<Pair<StateA, StateB>> direct calls to RStaMappedValue<StateA, State> and RStaMappedValue<StateB, State> are made.
 * RStaMappedValue conceptually doesn't hold its `value`.
 * To return the values they make theirs calls to RStaValue<State>, transform the results of their calls and return them.
 * (Technically they use cached value and do a transformation only when source value is changed,
 * see [checkValueVersion] for details)
 *
 * So events are pushed to listeners, data is pulled from a source.
 * This way we always have consistent values every time regardless of pipelines' complexities and interdependencies between them.
 *
 * Note that as we always use direct ("instantaneous") function calls to get data and we work inside a single thread
 * we can use any references we want to get all we need
 * (as opposed to producer-consumer pattern where all data should be somehow pushed to a pipeline).
 *
 * To better understand inner workings of standard value sources' implementations
 * you can examine [RStaMappedValue] source code and trace it down to and inside of [RStaDynamicValue].
 * The source code demonstrates:
 * (1) events are relayed further down (from a source to a consumer),
 * (2) every reading of `value` results into checking its source's value,
 * (3) `checkValueVersion` is used to check if source's value is the same instead of reading its value every time.
 */
interface RStaGenericValue<out Value : Event, out Event> : RStaValueEventSource<Event> {

    /**
     * Standard implementations call `checkValueVersion` internally (to maintain the cache).
     * You don't need it (unless you implement `RStaGenericValue`).
     *
     * Details:
     *
     * Every call of `checkValueVersion` returns the same value as long as [value] remains the same.
     * If [value] has changed since the last call the returned value will be different.
     * The returned value is not the same as hash code.
     * The same [value] can correspond to a different result of 'checkValueVersion' in different or even the same runs.
     *
     * val i: RStaGenericValue<Int, Int>
     *
     * after `i.value` was changed to 10 `checkValueVersion` can return 1
     *
     * after `i.value` was changed to 100 `checkValueVersion` for example can return 2
     *
     * after `i.value` was changed back to 10 `checkValueVersion` can return 3
     *
     * Other implementations can choose to have the same `checkValueVersion` result for the same [value]s:
     *
     * val i: RStaGenericValue<Int, Int>
     *
     * after `i.value` was changed to 10 `checkValueVersion` can return 10
     *
     * after `i.value` was changed to 100 `checkValueVersion` for example can return 100
     *
     * after `i.value` was changed back to 10 `checkValueVersion` can return 10
     *
     * "Pulling data" concept assumes that to determine the result of `checkValueVersion`
     * the instance should call `checkValueVersion` of all the value sources it depends upon.
     * For example call to `checkValueVersion` of [RStaMappedValue] will result into call to `checkValueVersion` of its source.
     * A call to `checkValueVersion` is done automatically at every reading of [value].
     */
    fun checkValueVersion(): Long

    /**
     * Value.
     * If the `value` conceptually represents some dependent value
     * (as `squareOfCircle.value` depends on `circleRadius.value`)
     * it is recommended to not store the `value` but calculate it every time using `circleRadius.value`.
     */
    val value: Value
}