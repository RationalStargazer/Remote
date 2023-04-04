package net.rationalstargazer.events

// import java.lang.ref.WeakReference
//
// class ListenerSkipWhenInactive<T>(
//     lifecycle: ViewLifecycle,
//     listenerFunction: (T) -> Unit
// ) : ListenerBase<T>(lifecycle, listenerFunction) {
//
//     override fun doNotify(value: T) {
//         if (lifecycle.get()?.active == true) {
//             listener?.invoke(value)
//         }
//     }
//
//     override fun onFinish() {
//         // nothing to do
//     }
// }
//
// class ValueConsumer<T> constructor(
//     lifecycle: ViewLifecycle,
//     source: SignalValue<T>,
//     consumeFirstValueImmediately: Boolean,
//     handler: (T) -> Unit
// ) {
//
//     constructor(
//         lifecycle: ViewLifecycle,
//         source: SignalValue<T>,
//         handler: (T) -> Unit
//     ) : this(lifecycle, source, true, handler)
//
//     init {
//         val listener = ListenerLiveData(lifecycle, handler)
//         source.listen(listener)
//
//         if (consumeFirstValueImmediately) listener.notify(source.value)
//     }
//
//     private class ListenerLiveData<T> constructor(
//         lifecycle: ViewLifecycle,
//         listenerFunction: (T) -> Unit
//     ) : ListenerBase<T>(lifecycle, listenerFunction) {
//
//         override fun doNotify(value: T) {
//             if (lifecycle.get()?.active == true) {
//                 listener?.invoke(value)
//             } else {
//                 delayedValue = ValueHolder(value)
//
//                 if (!listeningChanges) {
//                     lifecycle.get()?.let { l ->
//                         listeningChanges = true
//                         l.listenStateChange(ListenerSkipWhenInactive(l) {
//                             if (l.active) {
//                                 val v = delayedValue
//                                 delayedValue = null
//
//                                 if (v != null) {
//                                     listener?.invoke(v.value)
//                                 }
//                             }
//                         })
//                     }
//                 }
//             }
//         }
//
//         override fun onFinish() {
//             delayedValue = null
//         }
//
//         private var delayedValue: ValueHolder<T>? = null
//
//         private var listeningChanges: Boolean = false
//
//         private data class ValueHolder<T>(val value: T)
//     }
// }
//
// abstract class ListenerBase<T>(lifecycle: ViewLifecycle, listenerFunction: (T) -> Unit) : Listener<T> {
//
//     protected open fun checkFinished(): Boolean = lifecycle.get()?.closed ?: true
//
//     protected abstract fun doNotify(value: T)
//
//     protected abstract fun onFinish()
//
//     final override val finished: Boolean get() {
//         if (mFinished) return true
//
//         if (checkFinished()) {
//             finish()
//         }
//
//         return mFinished
//     }
//
//     final override fun notify(value: T) {
//         if (mFinished) {
//             return
//         }
//
//         if (checkFinished()) {
//             finish()
//             return
//         }
//
//         doNotify(value)
//     }
//
//     protected fun finish() {
//         onFinish()
//         mFinished = true
//         lifecycle.clear()
//         listener = null
//     }
//
//     protected var mFinished = false
//         private set
//
//     protected var lifecycle: WeakReference<ViewLifecycle> = WeakReference(lifecycle)
//         private set
//
//     protected var listener: ((T) -> Unit)? = listenerFunction
//         private set
// }