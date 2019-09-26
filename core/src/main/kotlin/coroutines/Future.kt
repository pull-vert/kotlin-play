package coroutines

import java.util.concurrent.Future

/**
 * Cancels a specified [future] when this job is cancelled.
 * This is a shortcut for the following code with slightly more efficient implementation (one fewer object created).
 * ```
 * invokeOnCancellation { future.cancel(false) }
 * ```
 */
public fun CancellableContinuation<*>.cancelFutureOnCancellation(future: Future<*>) =
        invokeOnCancellation(handler = CancelFutureOnCancel(future))

private class CancelFutureOnCancel(private val future: Future<*>) : CancelHandler()  {
    override fun invoke(cause: Throwable?) {
        // Don't interrupt when cancelling future on completion, because no one is going to reset this
        // interruption flag and it will cause spurious failures elsewhere
        future.cancel(false)
    }
    override fun toString() = "CancelFutureOnCancel[$future]"
}
