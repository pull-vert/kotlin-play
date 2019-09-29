package coroutines.internal

import coroutines.*
import coroutines.assert
import coroutines.toDebugString
import kotlin.coroutines.Continuation
import kotlin.coroutines.resumeWithException

internal class DispatchedContinuation<T>(
        @JvmField val dispatcher: CoroutineDispatcher,
        @JvmField val continuation: Continuation<T>
) : DispatchedTask<T>(), Continuation<T> by continuation {

    @Suppress("PropertyName")
    internal var _state: DispatchedState<T> = DispatchedState.undefined()
    @JvmField // pre-cached value to avoid ctx.fold on every resumption
    internal val countOrElement = threadContextElements(context)

    override fun takeState(): DispatchedState<T> {
        val state = _state
        assert { state.isNotUndefined } // fail-fast if repeatedly invoked
        _state = DispatchedState.undefined()
        return state
    }

    override val delegate: Continuation<T>
        get() = this

    @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
    inline fun resumeCancellable(value: T) {
//        if (dispatcher.isDispatchNeeded(context)) {
        _state = DispatchedState.success(value)
//            resumeMode = MODE_CANCELLABLE
        dispatcher.dispatch(context, this)
//        } else {
//            executeUnconfined(value, MODE_CANCELLABLE) {
//                if (!resumeCancelled()) {
//                    resumeUndispatched(value)
//                }
//            }
    }

    @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
    inline fun resumeCancellableWithException(exception: Throwable) {
        val context = continuation.context
//        if (dispatcher.isDispatchNeeded(context)) {
            _state = DispatchedState.failure(exception)
//            resumeMode = MODE_CANCELLABLE
            dispatcher.dispatch(context, this)
//        } else {
//            executeUnconfined(state, MODE_CANCELLABLE) {
//                if (!resumeCancelled()) {
//                    resumeUndispatchedWithException(exception)
//                }
//            }
//        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun resumeCancelled(): Boolean {
        val job = context[Job]
        if (job != null && !job.isActive) {
            resumeWithException(job.getCancellationException())
            return true
        }

        return false
    }

    override fun toString(): String =
            "DispatchedContinuation[$dispatcher, ${continuation.toDebugString()}]"
}
