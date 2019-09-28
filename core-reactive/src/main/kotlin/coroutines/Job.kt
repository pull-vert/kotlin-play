package coroutines

import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

interface Job : CoroutineContext.Element {
    /**
     * Key for [Job] instance in the coroutine context.
     */
    public companion object Key : CoroutineContext.Key<Job> {
        init {
            /*
             * Here we make sure that CoroutineExceptionHandler is always initialized in advance, so
             * that if a coroutine fails due to StackOverflowError we don't fail to report this error
             * trying to initialize CoroutineExceptionHandler
             */
            CoroutineExceptionHandler
        }
    }

    /**
     * Returns `true` when this job is active -- it was already started and has not completed nor was cancelled yet.
     * The job that is waiting for its [children] to complete is still considered to be active if it
     * was not cancelled nor failed.
     *
     * See [Job] documentation for more details on job states.
     */
    public val isActive: Boolean

    /**
     * Cancels this job with an optional cancellation [cause].
     * A cause can be used to specify an error message or to provide other details on
     * the cancellation reason for debugging purposes.
     * See [Job] documentation for full explanation of cancellation machinery.
     */
    public fun cancel(cause: CancellationException? = null)

    /**
     * Returns [CancellationException] that signals the completion of this job. This function is
     * used by [cancellable][suspendCancellableCoroutine] suspending functions. They throw exception
     * returned by this function when they suspend in the context of this job and this job becomes _complete_.
     *
     * This function returns the original [cancel] cause of this job if that `cause` was an instance of
     * [CancellationException]. Otherwise (if this job was cancelled with a cause of a different type, or
     * was cancelled without a cause, or had completed normally), an instance of [CancellationException] is
     * returned. The [CancellationException.cause] of the resulting [CancellationException] references
     * the original cancellation cause that was passed to [cancel] function.
     *
     * This function throws [IllegalStateException] when invoked on a job that is still active.
     *
     * @suppress **This an internal API and should not be used from general code.**
     */
    public fun getCancellationException(): CancellationException
}
