package net.rationalstargazer.events.listeners

import net.rationalstargazer.events.Container
import net.rationalstargazer.events.RStaListener
import net.rationalstargazer.events.lifecycle.RStaLifecycle
import net.rationalstargazer.events.lifecycle.RStaSuspendableLifecycle
import net.rationalstargazer.events.value.RStaListenerInvoke

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
    private val listenerFunction: (T) -> Unit
): RStaListener<T> {

	override val lifecycleScope: RStaLifecycle = lifecycle.scope

	override fun notify(value: T) {
		if (lifecycle.active.value) {
			delayedValue = null
			listenerFunction(value)
		} else {
			if (delayedValue != null) {
				delayedValue!!.value = value
			} else {
				delayedValue = Container(value)
			}
		}
	}

	private var delayedValue: Container<T>? = null

	private fun activeStateListener(activeValue: Boolean) {
		if (lifecycle.active.value) {
			val v = delayedValue
			if (v != null) {
				delayedValue = null
				listenerFunction(v.value)
			}
		}
	}
	
	init {
		lifecycle.active.listen(RStaListenerInvoke.No, lifecycle.scope, ::activeStateListener)
    }
}