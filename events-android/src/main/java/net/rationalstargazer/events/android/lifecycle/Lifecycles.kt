package net.rationalstargazer.events.android.lifecycle

// import androidx.activity.ComponentActivity
// import androidx.lifecycle.DefaultLifecycleObserver
// import androidx.lifecycle.Lifecycle as AndroidLifecycle
// import androidx.lifecycle.LifecycleOwner
// import net.rationalstargazer.events.*
//
// class ActivityLifecycle(activity: ComponentActivity) : ViewLifecycle {
//
//     override val active: Boolean get() {
//         return androidLifecycle?.currentState?.isAtLeast(AndroidLifecycle.State.STARTED) ?: false
//     }
//
//     override val closed: Boolean get() {
//         return androidLifecycle?.currentState?.equals(AndroidLifecycle.State.DESTROYED) ?: true
//     }
//
//     override val finished: Boolean get() = lifecycle.finished
//
//     override fun listenClose(callIfAlreadyClosed: Boolean, listener: Listener<Boolean>) {
//         lifecycle.listenClose(callIfAlreadyClosed, listener)
//     }
//
//     override fun listenStateChange(listener: Listener<SuspendableLifecycle.State>) {
//         stateChangeDispatcher.listen(listener)
//     }
//
//     private val lifecycle = LifecycleDispatcher()
//
//     private var androidLifecycle: AndroidLifecycle? = activity.lifecycle
//
//     private val stateChangeDispatcher = EventDispatcher<SuspendableLifecycle.State>(lifecycle)
//
//     init {
//         androidLifecycle?.addObserver(object : DefaultLifecycleObserver {
//             override fun onStart(owner: LifecycleOwner) {
//                 stateChangeDispatcher.enqueueEvent(SuspendableLifecycle.State.Active)
//             }
//
//             override fun onStop(owner: LifecycleOwner) {
//                 stateChangeDispatcher.enqueueEvent(SuspendableLifecycle.State.Inactive)
//             }
//
//             override fun onDestroy(owner: LifecycleOwner) {
//                 stateChangeDispatcher.enqueueEvent(SuspendableLifecycle.State.Closed)
//                 lifecycle.close()
//                 androidLifecycle = null
//             }
//         })
//     }
// }

