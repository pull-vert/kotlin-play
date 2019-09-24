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

    // ------------ state update ------------

    /**
     * Starts coroutine related to this job (if any) if it was not started yet.
     * The result `true` if this invocation actually started coroutine or `false`
     * if it was already started or completed.
     */
    public fun start(): Boolean

    /**
     * Registers handler that is **synchronously** invoked once on cancellation or completion of this job.
     * when the job was already cancelled and is completed its execution, then the handler is immediately invoked
     * with the job's cancellation cause or `null` unless [invokeImmediately] is set to false.
     * Otherwise, handler will be invoked once when this job is cancelled or is complete.
     *
     * The meaning of `cause` that is passed to the handler:
     * * Cause is `null` when the job has completed normally.
     * * Cause is an instance of [CancellationException] when the job was cancelled _normally_.
     *   **It should not be treated as an error**. In particular, it should not be reported to error logs.
     * * Otherwise, the job had _failed_.
     *
     * Invocation of this handler on a transition to a _cancelling_ state
     * is controlled by [onCancelling] boolean parameter.
     * The handler is invoked when the job becomes _cancelling_ if [onCancelling] parameter is set to `true`.
     *
     * The resulting [DisposableHandle] can be used to [dispose][DisposableHandle.dispose] the
     * registration of this handler and release its memory if its invocation is no longer needed.
     * There is no need to dispose the handler after completion of this job. The references to
     * all the handlers are released when this job completes.
     *
     * Installed [handler] should not throw any exceptions. If it does, they will get caught,
     * wrapped into [CompletionHandlerException], and rethrown, potentially causing crash of unrelated code.
     *
     * **Note**: This function is a part of internal machinery that supports parent-child hierarchies
     * and allows for implementation of suspending functions that wait on the Job's state.
     * This function should not be used in general application code.
     * Implementation of `CompletionHandler` must be fast, non-blocking, and thread-safe.
     * This handler can be invoked concurrently with the surrounding code.
     * There is no guarantee on the execution context in which the [handler] is invoked.
     *
     * @param onCancelling when `true`, then the [handler] is invoked as soon as this job transitions to _cancelling_ state;
     *        when `false` then the [handler] is invoked only when it transitions to _completed_ state.
     * @param invokeImmediately when `true` and this job is already in the desired state (depending on [onCancelling]),
     *        then the [handler] is immediately and synchronously invoked and no-op [DisposableHandle] is returned;
     *        when `false` then no-op [DisposableHandle] is returned, but the [handler] is not invoked.
     * @param handler the handler.
     *
     * @suppress **This an internal API and should not be used from general code.**
     */
//    @InternalCoroutinesApi
    public fun invokeOnCompletion(
            onCancelling: Boolean = false,
            invokeImmediately: Boolean = true,
            handler: CompletionHandler): DisposableHandle
}

/**
 * A handle to an allocated object that can be disposed to make it eligible for garbage collection.
 */
public interface DisposableHandle {
    /**
     * Disposes the corresponding object, making it eligible for garbage collection.
     * Repeated invocation of this function has no effect.
     */
    public fun dispose()
}

/**
 * No-op implementation of [DisposableHandle].
 * @suppress **This an internal API and should not be used from general code.**
 */
//@InternalCoroutinesApi
public object NonDisposableHandle : DisposableHandle/*, ChildHandle*/ {
    /**
     * Does not do anything.
     * @suppress
     */
    override fun dispose() {}

//    /**
//     * Returns `false`.
//     * @suppress
//     */
//    override fun childCancelled(cause: Throwable): Boolean = false

    /**
     * Returns "NonDisposableHandle" string.
     * @suppress
     */
    override fun toString(): String = "NonDisposableHandle"
}
