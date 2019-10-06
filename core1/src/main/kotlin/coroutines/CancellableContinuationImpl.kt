/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package coroutines

import kotlinx.atomicfu.*
import coroutines.internal.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.jvm.*

private const val UNDECIDED = 0
private const val SUSPENDED = 1
private const val RESUMED = 2

@JvmField
//@SharedImmutable
internal val RESUME_TOKEN = Symbol("RESUME_TOKEN")

/**
 * @suppress **This is unstable API and it is subject to change.**
 */
@PublishedApi
internal open class CancellableContinuationImpl<T>(
    final override val delegate: Continuation<T>,
    resumeMode: Int
) : DispatchedTask<T>(resumeMode), CancellableContinuation<T>, CoroutineStackFrame {
    public override val context: CoroutineContext = delegate.context

    /*
     * Implementation notes
     *
     * AbstractContinuation is a subset of Job with following limitations:
     * 1) It can have only cancellation listeners
     * 2) It always invokes cancellation listener if it's cancelled (no 'invokeImmediately')
     * 3) It can have at most one cancellation listener
     * 4) Its cancellation listeners cannot be deregistered
     * As a consequence it has much simpler state machine, more lightweight machinery and
     * less dependencies.
     */

    /* decision state machine

        +-----------+   trySuspend   +-----------+
        | UNDECIDED | -------------> | SUSPENDED |
        +-----------+                +-----------+
              |
              | tryResume
              V
        +-----------+
        |  RESUMED  |
        +-----------+

        Note: both tryResume and trySuspend can be invoked at most once, first invocation wins
     */
    private val _decision = atomic(UNDECIDED)

    /*
       === Internal states ===
       name        state class          public state    description
       ------      ------------         ------------    -----------
       ACTIVE      Active               : Active        active, no listeners
       SINGLE_A    CancelHandler        : Active        active, one cancellation listener
       CANCELLED   CancelledContinuation: Cancelled     cancelled (final state)
       COMPLETED   any                  : Completed     produced some result or threw an exception (final state)
     */
    private val _state = atomic<DispatchedState<T>>(DispatchedState.active())

    private val _parentHandle = atomic<DisposableHandle?>(null)
    private var parentHandle: DisposableHandle?
        get() = _parentHandle.value
        set(value) { _parentHandle.value = value }

    internal val state: DispatchedState<T> get() = _state.value

    public override val isActive: Boolean get() = state.isNotCompleted

    public override val isCompleted: Boolean get() = state.isCompleted

    public override val isCancelled: Boolean get() = state.isCancelledContinuation

//    public override fun initCancellability() {
//        // This method does nothing. Leftover for binary compatibility with old compiled code
//    }

    private fun isReusable(): Boolean = delegate is DispatchedContinuation<*> && delegate.isReusable

    /**
     * Resets cancellability state in order to [suspendAtomicCancellableCoroutineReusable] to work.
     * Invariant: used only by [suspendAtomicCancellableCoroutineReusable] in [REUSABLE_CLAIMED] state.
     */
    internal fun resetState(): Boolean {
        assert { parentHandle !== NonDisposableHandle }
        val state = _state.value
        assert { !state.isNotCompleted }
        if (state.isCompletedIdempotentResult) {
            detachChild()
            return false
        }
        _decision.value = UNDECIDED
        _state.value = DispatchedState.active()
        return true
    }

    /**
     * Setups parent cancellation and checks for postponed cancellation in the case of reusable continuations.
     * It is only invoked from an internal [getResult] function.
     */
    private fun setupCancellation() {
        if (checkCompleted()) return
        if (parentHandle !== null) return // fast path 2 -- was already initialized
        val parent = delegate.context[Job] ?: return // fast path 3 -- don't do anything without parent
        parent.start() // make sure the parent is started
        val handle = parent.invokeOnCompletion(
            onCancelling = true,
            handler = ChildContinuation(parent, this).asHandler
        )
        parentHandle = handle
        // now check our state _after_ registering (could have completed while we were registering)
        // Also note that we do not dispose parent for reusable continuations, dispatcher will do that for us
        if (isCompleted && !isReusable()) {
            handle.dispose() // it is Ok to call dispose twice -- here and in disposeParentHandle
            parentHandle = NonDisposableHandle // release it just in case, to aid GC
        }
    }

    private fun checkCompleted(): Boolean {
        val completed = isCompleted
        if (resumeMode != MODE_ATOMIC_DEFAULT) return completed // Do not check postponed cancellation for non-reusable continuations
        val dispatched = delegate as? DispatchedContinuation<*> ?: return completed
        val cause = dispatched.checkPostponedCancellation(this) ?: return completed
        if (!completed) {
            // Note: this cancel may fail if one more concurrent cancel is currently being invoked
            cancel(cause)
        }
        return true
    }

    public override val callerFrame: CoroutineStackFrame?
        get() = delegate as? CoroutineStackFrame

    public override fun getStackTraceElement(): StackTraceElement? = null

    override fun takeState(): DispatchedState<in T> = state

    override fun cancelResult(state: Any?, cause: Throwable) {
        if (state is CompletedWithCancellation) {
            invokeHandlerSafely {
                state.onCancellation(cause)
            }
        }
    }

    /*
     * Attempt to postpone cancellation for reusable cancellable continuation
     */
    private fun cancelLater(cause: Throwable): Boolean {
        if (resumeMode != MODE_ATOMIC_DEFAULT) return false
        val dispatched = (delegate as? DispatchedContinuation<*>) ?: return false
        return dispatched.postponeCancellation(cause)
    }

    public override fun cancel(cause: Throwable?): Boolean {
        _state.loop { state ->
            if (state.isCompleted) return false // false if already complete or cancelling
            // Active -- update to final state
            val update = CancelledContinuation(this, cause, handled = state.isCancelHandler)
            if (!_state.compareAndSet(state, DispatchedState.cancelledContinuation(update))) return@loop // retry on cas failure
            // Invoke cancel handler if it was present
            if (state.isCancelHandler) invokeHandlerSafely { (state.value as CancelHandler).invoke(cause) }
            // Complete state update
            detachChildIfNonResuable()
            dispatchResume(mode = MODE_ATOMIC_DEFAULT)
            return true
        }
    }

    internal fun parentCancelled(cause: Throwable) {
        if (cancelLater(cause)) return
        cancel(cause)
        // Even if cancellation has failed, we should detach child to avoid potential leak
        detachChildIfNonResuable()
    }

    private inline fun invokeHandlerSafely(block: () -> Unit) {
        try {
            block()
        } catch (ex: Throwable) {
            // Handler should never fail, if it does -- it is an unhandled exception
            handleCoroutineException(
                context,
                CompletionHandlerException("Exception in cancellation handler for $this", ex)
            )
        }
    }

    /**
     * It is used when parent is cancelled to get the cancellation cause for this continuation.
     */
    open fun getContinuationCancellationCause(parent: Job): Throwable =
        parent.getCancellationException()

    private fun trySuspend(): Boolean {
        _decision.loop { decision ->
            when (decision) {
                UNDECIDED -> if (this._decision.compareAndSet(UNDECIDED, SUSPENDED)) return true
                RESUMED -> return false
                else -> error("Already suspended")
            }
        }
    }

    private fun tryResume(): Boolean {
        _decision.loop { decision ->
            when (decision) {
                UNDECIDED -> if (this._decision.compareAndSet(UNDECIDED, RESUMED)) return true
                SUSPENDED -> return false
                else -> error("Already resumed")
            }
        }
    }

    @PublishedApi
    internal fun getResult(): Any? {
        setupCancellation()
        if (trySuspend()) return COROUTINE_SUSPENDED
        // otherwise, onCompletionInternal was already invoked & invoked tryResume, and the result is in the state
        val state = this.state
        if (state.isFailure) throw recoverStackTrace(state.exceptionOrNull()!!, this)
        // if the parent job was already cancelled, then throw the corresponding cancellation exception
        // otherwise, there is a race is suspendCancellableCoroutine { cont -> ... } does cont.resume(...)
        // before the block returns. This getResult would return a result as opposed to cancellation
        // exception that should have happened if the continuation is dispatched for execution later.
        if (resumeMode == MODE_CANCELLABLE) {
            val job = context[Job]
            if (job != null && !job.isActive) {
                val cause = job.getCancellationException()
                cancelResult(state, cause)
                throw recoverStackTrace(cause, this)
            }
        }
        return getSuccessfulResult(state) as Any?
    }

    override fun resumeWith(result: Result<T>) {
        resumeImpl(result.toState(this), resumeMode)
    }

    override fun resume(value: T, onCancellation: (cause: Throwable) -> Unit) {
        val cancelled = resumeImpl(
                DispatchedState.completedWithCancellation(CompletedWithCancellation(value, onCancellation)),
                resumeMode)
        if (cancelled != null) {
            // too late to resume (was cancelled) -- call handler
            invokeHandlerSafely {
                onCancellation(cancelled.cause)
            }
        }
    }

    public override fun invokeOnCancellation(handler: CompletionHandler) {
        var handleCache: CancelHandler? = null
        _state.loop { state ->
            when {
                state.isActive -> {
                    val node = handleCache ?: makeHandler(handler).also { handleCache = it }
                    if (_state.compareAndSet(state, DispatchedState.cancelHandle(node))) return // quit on cas success
                }
                state.isCancelHandler -> {
                    multipleHandlersError(handler, state.value)
                }
                state.value is CancelledContinuation -> {
                    /*
                         * Continuation was already cancelled, invoke directly.
                         * NOTE: multiple invokeOnCancellation calls with different handlers are not allowed,
                         * so we check to make sure that handler was installed just once.
                         */
                    if (!state.value.makeHandled()) multipleHandlersError(handler, state.value)
                    /*
                     * :KLUDGE: We have to invoke a handler in platform-specific way via `invokeIt` extension,
                     * because we play type tricks on Kotlin/JS and handler is not necessarily a function there
                     */
                    invokeHandlerSafely { handler.invokeIt(state.exceptionOrNull()) }
                    return
                }
                else -> {
                    /*
                     * Continuation was already completed, do nothing.
                     * NOTE: multiple invokeOnCancellation calls with different handlers are not allowed,
                     * but we have no way to check that it was installed just once in this case.
                     */
                    return
                }
            }
        }
    }

    private fun multipleHandlersError(handler: CompletionHandler, state: Any?) {
        error("It's prohibited to register multiple handlers, tried to register $handler, already has $state")
    }

    private fun makeHandler(handler: CompletionHandler): CancelHandler =
        if (handler is CancelHandler) handler else InvokeOnCancel(handler)

    private fun dispatchResume(mode: Int) {
        if (tryResume()) return // completed before getResult invocation -- bail out
        // otherwise, getResult has already commenced, i.e. completed later or in other thread
        dispatch(mode)
    }

    // returns null when successfully dispatched resumed, CancelledContinuation if too late (was already cancelled)
    private fun resumeImpl(proposedUpdate: DispatchedState<T>, resumeMode: Int): CancelledContinuation? {
        _state.loop { state ->
            when {
                state.isNotCompleted -> {
                    if (!_state.compareAndSet(state, proposedUpdate)) return@loop // retry on cas failure
                    detachChildIfNonResuable()
                    dispatchResume(resumeMode)
                    return null
                }
                state.value is CancelledContinuation -> {
                    /*
                     * If continuation was cancelled, then resume attempt must be ignored,
                     * because cancellation is asynchronous and may race with resume.
                     * Racy exceptions will be lost, too.
                     */
                    if (state.value.makeResumed()) return state.value // tried to resume just once, but was cancelled
                }
            }
            alreadyResumedError(proposedUpdate.value) // otherwise -- an error (second resume attempt)
        }
    }

    private fun alreadyResumedError(proposedUpdate: Any?) {
        error("Already resumed, but proposed with update $proposedUpdate")
    }

    // Unregister from parent job
    private fun detachChildIfNonResuable() {
        // If instance is reusable, do not detach on every reuse, #releaseInterceptedContinuation will do it for us in the end
        if (!isReusable()) detachChild()
    }

    /**
     * Detaches from the parent.
     * Invariant: used used from [CoroutineDispatcher.releaseInterceptedContinuation] iff [isReusable] is `true`
     */
    internal fun detachChild() {
        val handle = parentHandle
        handle?.dispose()
        parentHandle = NonDisposableHandle
    }

    // Note: Always returns RESUME_TOKEN | null
    override fun tryResume(value: T, idempotent: Any?): Any? {
        _state.loop { state ->
            when {
                state.isNotCompleted -> {
                    val update = if (idempotent == null) DispatchedState.success(value) else
                        DispatchedState.completedIdempotentResult(CompletedIdempotentResult(idempotent, value))
                    if (!_state.compareAndSet(state, update)) return@loop // retry on cas failure
                    detachChildIfNonResuable()
                    return RESUME_TOKEN
                }
                state.value is CompletedIdempotentResult -> {
                    return if (state.value.idempotentResume === idempotent) {
                        assert { state.value.result === value } // "Non-idempotent resume"
                        RESUME_TOKEN
                    } else {
                        null
                    }
                }
                else -> return null // cannot resume -- not active anymore
            }
        }
    }

    override fun tryResumeWithException(exception: Throwable): Any? {
        _state.loop { state ->
            when {
                state.isNotCompleted -> {
                    if (!_state.compareAndSet(state, DispatchedState.failure(exception))) return@loop // retry on cas failure
                    detachChildIfNonResuable()
                    return RESUME_TOKEN
                }
                else -> return null // cannot resume -- not active anymore
            }
        }
    }

    // note: token is always RESUME_TOKEN
    override fun completeResume(token: Any) {
        assert { token === RESUME_TOKEN }
        dispatchResume(resumeMode)
    }

    override fun CoroutineDispatcher.resumeUndispatched(value: T) {
        val dc = delegate as? DispatchedContinuation
        resumeImpl(DispatchedState.success(value), if (dc?.dispatcher === this) MODE_UNDISPATCHED else resumeMode)
    }

    override fun CoroutineDispatcher.resumeUndispatchedWithException(exception: Throwable) {
        val dc = delegate as? DispatchedContinuation
        resumeImpl(DispatchedState.failure(exception), if (dc?.dispatcher === this) MODE_UNDISPATCHED else resumeMode)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getSuccessfulResult(state: DispatchedState<in T>): T =
            when (val currentValue = state.value) {
                is CompletedIdempotentResult -> currentValue.result as T
                is CompletedWithCancellation -> currentValue.result as T
                else -> currentValue as T
            }

    // For nicer debugging
    public override fun toString(): String =
        "${nameString()}(${delegate.toDebugString()}){$state}@$hexAddress"

    protected open fun nameString(): String =
        "CancellableContinuation"

}

// Marker for active continuation
internal interface NotCompleted

internal abstract class CancelHandler : CancelHandlerBase(), NotCompleted

// Wrapper for lambdas, for the performance sake CancelHandler can be subclassed directly
private class InvokeOnCancel( // Clashes with InvokeOnCancellation
    private val handler: CompletionHandler
) : CancelHandler() {
    override fun invoke(cause: Throwable?) {
        handler.invoke(cause)
    }
    override fun toString() = "InvokeOnCancel[${handler.classSimpleName}@$hexAddress]"
}

internal class CompletedIdempotentResult(
    @JvmField val idempotentResume: Any?,
    @JvmField val result: Any?
) {
    override fun toString(): String = "CompletedIdempotentResult[$result]"
}

internal class CompletedWithCancellation(
    @JvmField val result: Any?,
    @JvmField val onCancellation: (cause: Throwable) -> Unit
) {
    override fun toString(): String = "CompletedWithCancellation[$result]"
}

