package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle

class ValueMapper<out Value, in SourceValue> private constructor(
    private val base: ChainGenericItem<Value, Value, SourceValue>
) : RStaGenericValue<Value, Value> by base {

    constructor(
        lifecycle: RStaLifecycle,
        source: RStaGenericValue<SourceValue, SourceValue>,
        mapper: (SourceValue) -> Value
    ) : this(
        ChainGenericItem(
            lifecycle,
            source,
            mapper,
            source::checkValue,
        ) {
            mapper(source.value)
        }
    )
}