package coroutines

import coroutines.internal.DispatchedState
import kotlinx.atomicfu.atomic
import java.io.Serializable
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation

/**
 * Class for an internal state of a job that was cancelled (completed exceptionally).
 *
 * @param cause the exceptional completion cause. It's either original exceptional cause
 *        or artificial [CancellationException] if no cause was provided
 */
internal open class CompletedExceptionally(
        @JvmField val cause: Throwable,
        handled: Boolean = false
) : Serializable {
    private val _handled = atomic(handled)
    val handled: Boolean get() = _handled.value
    fun makeHandled(): Boolean = _handled.compareAndSet(false, true)
    override fun equals(other: Any?): Boolean = other is CompletedExceptionally && cause == other.cause
    override fun hashCode(): Int = cause.hashCode()
    override fun toString(): String = "$classSimpleName[$cause]"
}

/**
 * A specific subclass of [CompletedExceptionally] for cancelled [AbstractContinuation].
 *
 * @param continuation the continuation that was cancelled.
 * @param cause the exceptional completion cause. If `cause` is null, then a [CancellationException]
 *        if created on first access to [exception] property.
 */
internal class CancelledContinuation(
        continuation: Continuation<*>,
        cause: Throwable?,
        handled: Boolean
) : CompletedExceptionally(cause ?: CancellationException("Continuation $continuation was cancelled normally"), handled) {
    private val _resumed = atomic(false)
    fun makeResumed(): Boolean = _resumed.compareAndSet(false, true)
}
