package net.rationalstargazer.events.lifecycle

class RStaNestedLifecycle private constructor(private val base: RStaControlledLifecycle) : RStaControlledLifecycle by base {

    constructor(outerLifecycle: RStaLifecycleScope) : this(
        if (outerLifecycle.finished) {
            RStaLifecycleDispatcher.Finished(outerLifecycle.coordinator)
        } else {
            RStaLifecycleDispatcher(outerLifecycle.coordinator)
        }
    ) {
        if (!outerLifecycle.finished) {
            outerLifecycle.listenBeforeFinish(true, base) {
                base.close()
            }
        }
    }
}