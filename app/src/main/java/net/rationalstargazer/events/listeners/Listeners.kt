package net.rationalstargazer.events.listeners

import net.rationalstargazer.events.lifecycle.RStaSuspendableLifecycle
import net.rationalstargazer.events.value.RStaValueEventSource
import net.rationalstargazer.events.value.RStaValueSource

// class ListenerSkipWhenInactive<T>(
//     private val lifecycle: RStaSuspendableLifecycle,
//     private val listenerFunction: (T) -> Unit
// ) : RStaListener<T> {
//
// 	override val lifecycleScope: RStaLifecycle = lifecycle.scope
//
//     override fun notify(value: T) {
//         if (lifecycle.active.value) {
//             listenerFunction(value)
//         }
//     }
// }

/**
 * TODO: experimental
 */
class RStaValueConsumer<T> constructor(
    val lifecycle: RStaSuspendableLifecycle,
    val source: RStaValueSource<T>,
    consumeFirst: RStaValueEventSource.Invoke,
    private val listenerFunction: (T) -> Unit
) {
	
	private var delayedValue: T? = null
	
	private fun consume(value: Any?) {
		if (lifecycle.active.value) {
			delayedValue = null
			listenerFunction(source.value)
		} else {
			delayedValue = source.value
		}
	}
	
	private fun activeStateListener(activeValue: Boolean) {
		if (lifecycle.active.value) {
			val value = delayedValue
			if (value != null) {
				delayedValue = null
				listenerFunction(value)
			}
		}
	}
	
	init {
		source.listen(consumeFirst, lifecycle.scope, ::consume)
        lifecycle.active.listen(RStaValueEventSource.Invoke.No, lifecycle.scope, ::activeStateListener)
    }
}