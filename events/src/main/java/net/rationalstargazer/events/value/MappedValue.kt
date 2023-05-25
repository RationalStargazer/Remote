package net.rationalstargazer.events.value

import net.rationalstargazer.events.lifecycle.RStaLifecycle

class RStaMappedValue<out Value, in SourceValue> private constructor(
    private val base: RStaGenericValue<Value, Value>
) : RStaValue<Value>, RStaGenericValue<Value, Value> by base {

    constructor(
        lifecycle: RStaLifecycle,
        source: RStaValue<SourceValue>,
        mapper: (SourceValue) -> Value
    ) : this(
        RStaChainGenericItem(
            lifecycle,
            true,
            source,
            mapper,
            source::checkValueVersion,
        ) {
            mapper(source.value)
        }
    )
}