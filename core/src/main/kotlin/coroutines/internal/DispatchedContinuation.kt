package coroutines.internal

import coroutines.*
import coroutines.CancellableContinuationImpl
import coroutines.MODE_ATOMIC_DEFAULT
import coroutines.MODE_CANCELLABLE
import coroutines.assert
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

//@SharedImmutable
private val UNDEFINED = Symbol("UNDEFINED")
//@SharedImmutable
@JvmField
internal val REUSABLE_CLAIMED = Symbol("REUSABLE_CLAIMED")

internal class DispatchedContinuation<in T>(
        @JvmField val dispatcher: CoroutineDispatcher,
        @JvmField val continuation: Continuation<T>
) : DispatchedTask<T>(MODE_ATOMIC_DEFAULT), CoroutineStackFrame, Continuation<T> by continuation {
    @JvmField
    @Suppress("PropertyName")
    internal var _state: Any? = UNDEFINED
    override val callerFrame: CoroutineStackFrame? = continuation as? CoroutineStackFrame
    override fun getStackTraceElement(): StackTraceElement? = null
    @JvmField // pre-cached value to avoid ctx.fold on every resumption
    internal val countOrElement = threadContextElements(context)

    /**
     * Possible states of reusability:
     *
     * 1) `null`. Cancellable continuation wasn't yet attempted to be reused or
     *     was used and then invalidated (e.g. because of the cancellation).
     * 2) [CancellableContinuation]. Continuation to be/that is being reused.
     * 3) [REUSABLE_CLAIMED]. CC is currently being reused and its owner executes `suspend` block:
     *    ```
     *    // state == null | CC
     *    suspendAtomicCancellableCoroutineReusable { cont ->
     *        // state == REUSABLE_CLAIMED
     *        block(cont)
     *    }
     *    // state == CC
     *    ```
     * 4) [Throwable] continuation was cancelled with this cause while being in [suspendAtomicCancellableCoroutineReusable],
     *    [CancellableContinuationImpl.getResult] will check for cancellation later.
     *
     * [REUSABLE_CLAIMED] state is required to prevent the lost resume in the channel.
     * AbstractChannel.receive method relies on the fact that the following pattern
     * ```
     * suspendAtomicCancellableCoroutineReusable { cont ->
     *     val result = pollFastPath()
     *     if (result != null) cont.resume(result)
     * }
     * ```
     * always succeeds.
     * To make it always successful, we actually postpone "reusable" cancellation
     * to this phase and set cancellation only at the moment of instantiation.
     */
    private val _reusableCancellableContinuation = atomic<Any?>(null)

    public val reusableCancellableContinuation: CancellableContinuationImpl<*>?
        get() = _reusableCancellableContinuation.value as? CancellableContinuationImpl<*>

    public val isReusable: Boolean
        get() = _reusableCancellableContinuation.value != null

    /**
     * Claims the continuation for [suspendAtomicCancellableCoroutineReusable] block,
     * so all cancellations will be postponed.
     */
    @Suppress("UNCHECKED_CAST")
    fun claimReusableCancellableContinuation(): CancellableContinuationImpl<T>? {
        /*
         * Transitions:
         * 1) `null` -> claimed, caller will instantiate CC instance
         * 2) `CC` -> claimed, caller will reuse CC instance
         */
        _reusableCancellableContinuation.loop { state ->
            when {
                state === null -> {
                    /*
                     * null -> CC was not yet published -> we do not compete with cancel
                     * -> can use plain store instead of CAS
                     */
                    _reusableCancellableContinuation.value = REUSABLE_CLAIMED
                    return null
                }
                state is CancellableContinuationImpl<*> -> {
                    if (_reusableCancellableContinuation.compareAndSet(state, REUSABLE_CLAIMED)) {
                        return state as CancellableContinuationImpl<T>
                    }
                }
                else -> error("Inconsistent state $state")
            }
        }
    }

    override fun takeState(): Any? {
        val state = _state
        assert { state !== UNDEFINED } // fail-fast if repeatedly invoked
        _state = UNDEFINED
        return state
    }

    override val delegate: Continuation<T>
        get() = this

    @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
    inline fun resumeCancellable(value: T) {
        if (dispatcher.isDispatchNeeded(context)) {
            _state = value
            resumeMode = MODE_CANCELLABLE
            dispatcher.dispatch(context, this)
        } else {
            executeUnconfined(value, MODE_CANCELLABLE) {
                if (!resumeCancelled()) {
                    resumeUndispatched(value)
                }
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
    inline fun resumeCancellableWithException(exception: Throwable) {
        val context = continuation.context
        val state = CompletedExceptionally(exception)
        if (dispatcher.isDispatchNeeded(context)) {
            _state = CompletedExceptionally(exception)
            resumeMode = MODE_CANCELLABLE
            dispatcher.dispatch(context, this)
        } else {
            executeUnconfined(state, MODE_CANCELLABLE) {
                if (!resumeCancelled()) {
                    resumeUndispatchedWithException(exception)
                }
            }
        }
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

    @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
    inline fun resumeUndispatched(value: T) {
        withCoroutineContext(context, countOrElement) {
            continuation.resume(value)
        }
    }

    @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
    inline fun resumeUndispatchedWithException(exception: Throwable) {
        withCoroutineContext(context, countOrElement) {
            continuation.resumeWithStackTrace(exception)
        }
    }
}

/**
 * Executes given [block] as part of current event loop, updating current continuation
 * mode and state if continuation is not resumed immediately.
 * [doYield] indicates whether current continuation is yielding (to provide fast-path if event-loop is empty).
 * Returns `true` if execution of continuation was queued (trampolined) or `false` otherwise.
 */
private inline fun DispatchedContinuation<*>.executeUnconfined(
        contState: Any?, mode: Int, doYield: Boolean = false,
        block: () -> Unit
): Boolean {
    val eventLoop = ThreadLocalEventLoop.eventLoop
    // If we are yielding and unconfined queue is empty, we can bail out as part of fast path
    if (doYield && eventLoop.isUnconfinedQueueEmpty) return false
    return if (eventLoop.isUnconfinedLoopActive) {
        // When unconfined loop is active -- dispatch continuation for execution to avoid stack overflow
        _state = contState
        resumeMode = mode
        eventLoop.dispatchUnconfined(this)
        true // queued into the active loop
    } else {
        // Was not active -- run event loop until all unconfined tasks are executed
        runUnconfinedEventLoop(eventLoop, block = block)
        false
    }
}
