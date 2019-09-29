@file:JvmName("JobKt")
@file:Suppress("DEPRECATION_ERROR", "RedundantUnitReturnType")

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
     * Returns `true` when this job has completed for any reason. A job that was cancelled or failed
     * and has finished its execution is also considered complete. Job becomes complete only after
     * all its [children] complete.
     *
     * See [Job] documentation for more details on job states.
     */
    public val isCompleted: Boolean

    /**
     * Cancels this job with an optional cancellation [cause].
     * A cause can be used to specify an error message or to provide other details on
     * the cancellation reason for debugging purposes.
     * See [Job] documentation for full explanation of cancellation machinery.
     */
    public fun cancel(cause: CancellationException? = null)

    /**
     * Attaches child job so that this job becomes its parent and
     * returns a handle that should be used to detach it.
     *
     * A parent-child relation has the following effect:
     * * Cancellation of parent with [cancel] or its exceptional completion (failure)
     *   immediately cancels all its children.
     * * Parent cannot complete until all its children are complete. Parent waits for all its children to
     *   complete in _completing_ or _cancelling_ states.
     *
     * **A child must store the resulting [ChildHandle] and [dispose][DisposableHandle.dispose] the attachment
     * to its parent on its own completion.**
     *
     * Coroutine builders and job factory functions that accept `parent` [CoroutineContext] parameter
     * lookup a [Job] instance in the parent context and use this function to attach themselves as a child.
     * They also store a reference to the resulting [ChildHandle] and dispose a handle when they complete.
     *
     * @suppress This is an internal API. This method is too error prone for public API.
     */
    // ChildJob and ChildHandle are made internal on purpose to further deter 3rd-party impl of Job
//    @InternalCoroutinesApi
    public fun attachChild(child: ChildJob): ChildHandle

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
 * Creates a job object in an active state.
 * A failure of any child of this job immediately causes this job to fail, too, and cancels the rest of its children.
 *
 * To handle children failure independently of each other use [SupervisorJob].
 *
 * If [parent] job is specified, then this job becomes a child job of its parent and
 * is cancelled when its parent fails or is cancelled. All this job's children are cancelled in this case, too.
 * The invocation of [cancel][Job.cancel] with exception (other than [CancellationException]) on this job also cancels parent.
 *
 * Conceptually, the resulting job works in the same way as the job created by the `launch { body }` invocation
 * (see [launch]), but without any code in the body. It is active until cancelled or completed. Invocation of
 * [CompletableJob.complete] or [CompletableJob.completeExceptionally] corresponds to the successful or
 * failed completion of the body of the coroutine.
 *
 * @param parent an optional parent job.
 */
@Suppress("FunctionName")
public fun Job(parent: Job? = null): CompletableJob = JobImpl(parent)

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
public object NonDisposableHandle : DisposableHandle, ChildHandle {
    /**
     * Does not do anything.
     * @suppress
     */
    override fun dispose() {}

    /**
     * Returns `false`.
     * @suppress
     */
    override fun childCancelled(cause: Throwable): Boolean = false

    /**
     * Returns "NonDisposableHandle" string.
     * @suppress
     */
    override fun toString(): String = "NonDisposableHandle"
}

/**
 * Ensures that current job is [active][Job.isActive].
 * If the job is no longer active, throws [CancellationException].
 * If the job was cancelled, thrown exception contains the original cancellation cause.
 *
 * This method is a drop-in replacement for the following code, but with more precise exception:
 * ```
 * if (!job.isActive) {
 *     throw CancellationException()
 * }
 * ```
 */
public fun Job.ensureActive(): Unit {
    if (!isActive) throw getCancellationException()
}

/**
 * Ensures that job in the current context is [active][Job.isActive].
 * Throws [IllegalStateException] if the context does not have a job in it.
 *
 * If the job is no longer active, throws [CancellationException].
 * If the job was cancelled, thrown exception contains the original cancellation cause.
 *
 * This method is a drop-in replacement for the following code, but with more precise exception:
 * ```
 * if (!isActive) {
 *     throw CancellationException()
 * }
 * ```
 */
public fun CoroutineContext.ensureActive(): Unit {
    val job = get(Job) ?: error("Context cannot be checked for liveness because it does not have a job: $this")
    job.ensureActive()
}

// -------------------- Parent-child communication --------------------

/**
 * A reference that parent receives from its child so that it can report its cancellation.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
//@InternalCoroutinesApi
@Deprecated(level = DeprecationLevel.ERROR, message = "This is internal API and may be removed in the future releases")
public interface ChildJob : Job {
    /**
     * Parent is cancelling its child by invoking this method.
     * Child finds the cancellation cause using [ParentJob.getChildJobCancellationCause].
     * This method does nothing is the child is already being cancelled.
     *
     * @suppress **This is unstable API and it is subject to change.**
     */
//    @InternalCoroutinesApi
    public fun parentCancelled(parentJob: ParentJob)
}

/**
 * A reference that child receives from its parent when it is being cancelled by the parent.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
//@InternalCoroutinesApi
@Deprecated(level = DeprecationLevel.ERROR, message = "This is internal API and may be removed in the future releases")
public interface ParentJob : Job {
    /**
     * Child job is using this method to learn its cancellation cause when the parent cancels it with [ChildJob.parentCancelled].
     * This method is invoked only if the child was not already being cancelled.
     *
     * Note that [CancellationException] is the method's return type: if child is cancelled by its parent,
     * then the original exception is **already** handled by either the parent or the original source of failure.
     *
     * @suppress **This is unstable API and it is subject to change.**
     */
//    @InternalCoroutinesApi
    public fun getChildJobCancellationCause(): CancellationException
}

/**
 * A handle that child keep onto its parent so that it is able to report its cancellation.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
//@InternalCoroutinesApi
@Deprecated(level = DeprecationLevel.ERROR, message = "This is internal API and may be removed in the future releases")
public interface ChildHandle : DisposableHandle {
    /**
     * Child is cancelling its parent by invoking this method.
     * This method is invoked by the child twice. The first time child report its root cause as soon as possible,
     * so that all its siblings and the parent can start cancelling their work asap. The second time
     * child invokes this method when it had aggregated and determined its final cancellation cause.
     *
     * @suppress **This is unstable API and it is subject to change.**
     */
//    @InternalCoroutinesApi
    public fun childCancelled(cause: Throwable): Boolean
}

// -------------------- CoroutineContext extensions --------------------

/**
 * Returns `true` when the [Job] of the coroutine in this context is still active
 * (has not completed and was not cancelled yet).
 *
 * Check this property in long-running computation loops to support cancellation
 * when [CoroutineScope.isActive] is not available:
 *
 * ```
 * while (coroutineContext.isActive) {
 *     // do some computation
 * }
 * ```
 *
 * The `coroutineContext.isActive` expression is a shortcut for `coroutineContext[Job]?.isActive == true`.
 * See [Job.isActive].
 */
public val CoroutineContext.isActive: Boolean
    get() = this[Job]?.isActive == true

/**
 * Cancels [Job] of this context with an optional cancellation cause.
 * See [Job.cancel] for details.
 */
public fun CoroutineContext.cancel(cause: CancellationException? = null) {
    this[Job]?.cancel(cause)
}
