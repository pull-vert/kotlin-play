package coroutines

import coroutines.internal.DispatchedContinuation
import coroutines.internal.resumeCancellable
import coroutines.internal.resumeCancellableWithException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@PublishedApi internal const val MODE_ATOMIC_DEFAULT = 0 // schedule non-cancellable dispatch for suspendCoroutine
@PublishedApi internal const val MODE_CANCELLABLE = 1    // schedule cancellable dispatch for suspendCancellableCoroutine
@PublishedApi internal const val MODE_DIRECT = 2         // when the context is right just invoke the delegate continuation direct
@PublishedApi internal const val MODE_UNDISPATCHED = 3   // when the thread is right, but need to mark it with current coroutine
@PublishedApi internal const val MODE_IGNORE = 4         // don't do anything

internal val Int.isCancellableMode get() = this == MODE_CANCELLABLE
internal val Int.isDispatchedMode get() = this == MODE_ATOMIC_DEFAULT || this == MODE_CANCELLABLE

internal fun <T> Continuation<T>.resumeMode(value: T, mode: Int) {
    when (mode) {
        MODE_ATOMIC_DEFAULT -> resume(value)
        MODE_CANCELLABLE -> resumeCancellable(value)
//        MODE_DIRECT -> resumeDirect(value)
        MODE_UNDISPATCHED -> (this as DispatchedContinuation).resumeUndispatched(value)
        MODE_IGNORE -> {}
        else -> error("Invalid mode $mode")
    }
}

internal fun <T> Continuation<T>.resumeWithExceptionMode(exception: Throwable, mode: Int) {
    when (mode) {
        MODE_ATOMIC_DEFAULT -> resumeWithException(exception)
        MODE_CANCELLABLE -> resumeCancellableWithException(exception)
//        MODE_DIRECT -> resumeDirectWithException(exception)
        MODE_UNDISPATCHED -> (this as DispatchedContinuation).resumeUndispatchedWithException(exception)
        MODE_IGNORE -> {}
        else -> error("Invalid mode $mode")
    }
}
