package coroutines

import java.util.concurrent.CancellationException

/**
 * Thrown by cancellable suspending functions if the [Job] of the coroutine is cancelled or completed
 * without cause, or with a cause or exception that is not [CancellationException]
 * (see [Job.getCancellationException]).
 */
internal class JobCancellationException (
        message: String,
        cause: Throwable?,
        @JvmField internal val job: Job
) : CancellationException(message), CopyableThrowable<JobCancellationException> {

    init {
        if (cause != null) initCause(cause)
    }

    override fun fillInStackTrace(): Throwable {
        if (DEBUG) {
            return super.fillInStackTrace()
        }

        /*
         * In non-debug mode we don't want to have a stacktrace on every cancellation/close,
         * parent job reference is enough. Stacktrace of JCE is not needed most of the time (e.g., it is not logged)
         * and hurts performance.
         */
        return this
    }

    override fun createCopy(): JobCancellationException? {
        if (DEBUG) {
            return JobCancellationException(message!!, this, job)
        }

        /*
         * In non-debug mode we don't copy JCE for speed as it does not have the stack trace anyway.
         */
        return null
    }

    override fun toString(): String = "${super.toString()}; job=$job"

    override fun equals(other: Any?): Boolean =
            other === this ||
                    other is JobCancellationException && other.message == message && other.job == job && other.cause == cause
    override fun hashCode(): Int =
            (message!!.hashCode() * 31 + job.hashCode()) * 31 + (cause?.hashCode() ?: 0)
}

internal class CoroutinesInternalError(message: String, cause: Throwable) : Error(message, cause)

/**
 * This exception gets thrown if an exception is caught while processing [CompletionHandler] invocation for [Job].
 *
 * @suppress **This an internal API and should not be used from general code.**
 */
//@InternalCoroutinesApi
public class CompletionHandlerException (
        message: String,
        cause: Throwable
) : RuntimeException(message, cause)
