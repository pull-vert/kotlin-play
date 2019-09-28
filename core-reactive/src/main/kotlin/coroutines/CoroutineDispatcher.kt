package coroutines

import coroutines.internal.DispatchedContinuation
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Base class to be extended by all coroutine dispatcher implementations.
 *
 * The following standard implementations are provided by `kotlinx.coroutines` as properties on
 * the [Dispatchers] object:
 *
 * * [Dispatchers.Default] &mdash; is used by all standard builders if no dispatcher or any other [ContinuationInterceptor]
 *   is specified in their context. It uses a common pool of shared background threads.
 *   This is an appropriate choice for compute-intensive coroutines that consume CPU resources.
 * * [Dispatchers.IO] &mdash; uses a shared pool of on-demand created threads and is designed for offloading of IO-intensive _blocking_
 *   operations (like file I/O and blocking socket I/O).
 * * [Dispatchers.Unconfined] &mdash; starts coroutine execution in the current call-frame until the first suspension,
 *   whereupon the coroutine builder function returns.
 *   The coroutine will later resume in whatever thread used by the
 *   corresponding suspending function, without confining it to any specific thread or pool.
 *   **The `Unconfined` dispatcher should not normally be used in code**.
 * * Private thread pools can be created with [newSingleThreadContext] and [newFixedThreadPoolContext].
 * * An arbitrary [Executor][java.util.concurrent.Executor] can be converted to a dispatcher with the [asCoroutineDispatcher] extension function.
 *
 * This class ensures that debugging facilities in [newCoroutineContext] function work properly.
 */
abstract class CoroutineDispatcher : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {

    /**
     * Dispatches execution of a runnable [block] onto another thread in the given [context].
     *
     * This method should generally be exception-safe. An exception thrown from this method
     * may leave the coroutines that use this dispatcher in the inconsistent and hard to debug state.
     */
    fun dispatch(context: CoroutineContext, task: Runnable) {

    }

    /**
     * Returns a continuation that wraps the provided [continuation], thus intercepting all resumptions.
     *
     * This method should generally be exception-safe. An exception thrown from this method
     * may leave the coroutines that use this dispatcher in the inconsistent and hard to debug state.
     */
    final override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
            DispatchedContinuation(this, continuation)

//    @InternalCoroutinesApi
    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
//        (continuation as DispatchedContinuation<*>).reusableCancellableContinuation?.detachChild()
    }

    /** @suppress for nicer debugging */
    override fun toString(): String = "$classSimpleName@$hexAddress"
}
