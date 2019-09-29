package coroutines.internal

import coroutines.*
import coroutines.scheduling.CompletableTask
import coroutines.scheduling.DeferredCompleter
import kotlin.contracts.ExperimentalContracts
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.runCatching

internal abstract class DispatchedTask<T> : CompletableTask, Runnable {
    internal abstract val delegate: Continuation<T>

    internal abstract fun takeState(): DispatchedState<T>

    internal open fun cancelResult(state: Any?, cause: Throwable) {}

    @ExperimentalContracts
    override fun run(taskCompleter: DeferredCompleter) {
        var fatalException: Throwable? = null
        try {
            run()
        } catch (e: Throwable) {
            // This instead of runCatching to have nicer stacktrace and debug experience
            fatalException = e
        } finally {
            val result = runCatching { taskCompleter.complete() }
            handleFatalException(fatalException, result.exceptionOrNull())
        }
    }

    @ExperimentalContracts
    override fun run() {
        val delegate = delegate as DispatchedContinuation<T>
        val continuation = delegate.continuation
        val context = continuation.context
        val state = takeState() // NOTE: Must take state in any case, even if cancelled
        withCoroutineContext(context, delegate.countOrElement) {
            try {
                val value = state.getOrThrow()
                val job = context[Job]
                /* Check whether continuation was originally resumed with an exception.
                * If so, it dominates cancellation, otherwise the original exception
                * will be silently lost.
                */
                if (job != null && !job.isActive) {
                    val cause = job.getCancellationException()
                    cancelResult(state, cause)
                    continuation.resumeWithStackTrace(cause)
                }
                continuation.resume(value)
            } catch (exception: Throwable) {
                continuation.resumeWithStackTrace(exception)
            }
        }
    }

    /**
     * Machinery that handles fatal exceptions in kotlinx.coroutines.
     * There are two kinds of fatal exceptions:
     *
     * 1) Exceptions from kotlinx.coroutines code. Such exceptions indicate that either
     *    the library or the compiler has a bug that breaks internal invariants.
     *    They usually have specific workarounds, but require careful study of the cause and should
     *    be reported to the maintainers and fixed on the library's side anyway.
     *
     * 2) Exceptions from [ThreadContextElement.updateThreadContext] and [ThreadContextElement.restoreThreadContext].
     *    While a user code can trigger such exception by providing an improper implementation of [ThreadContextElement],
     *    we can't ignore it because it may leave coroutine in the inconsistent state.
     *    If you encounter such exception, you can either disable this context element or wrap it into
     *    another context element that catches all exceptions and handles it in the application specific manner.
     *
     * Fatal exception handling can be intercepted with [CoroutineExceptionHandler] element in the context of
     * a failed coroutine, but such exceptions should be reported anyway.
     */
    private fun handleFatalException(exception: Throwable?, finallyException: Throwable?) {
        if (exception === null && finallyException === null) return
        if (exception !== null && finallyException !== null) {
            exception.addSuppressed(finallyException)
        }

        val cause = exception ?: finallyException
        val reason = CoroutinesInternalError("Fatal exception in coroutines machinery for $this. " +
                "Please read KDoc to 'handleFatalException' method and report this incident to maintainers", cause!!)
        handleCoroutineException(this.delegate.context, reason)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Continuation<*>.resumeWithStackTrace(exception: Throwable) {
    resumeWith(Result.failure(/*recoverStackTrace(*/exception/*, this)*/))
}
