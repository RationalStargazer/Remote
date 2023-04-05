package net.rationalstargazer.events.lifecycle

/**
 * Manually closeable lifecycle
 */
interface RStaControlledLifecycle : RStaLifecycle {
	
	/**
	 * Initiates the finishing process. For all details see [RStaLifecycleScope].
	 */
	fun close()
	
	// TODO: not implemented yet
	//fun close(onConsumed: () -> Unit)
}