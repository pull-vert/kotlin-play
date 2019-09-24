package coroutines

import coroutines.internal.DispatchedContinuation
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

public abstract class CoroutineDispatcher :
        AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    /**
     * Returns `true` if the execution shall be dispatched onto another thread.
     * The default behavior for most dispatchers is to return `true`.
     *
     * This method should never be used from general code, it is used only by `kotlinx.coroutines`
     * internals and its contract with the rest of the API is an implementation detail.
     *
     * UI dispatchers _should not_ override `isDispatchNeeded`, but leave the default implementation that
     * returns `true`. To understand the rationale beyond this recommendation, consider the following code:
     *
     * ```kotlin
     * fun asyncUpdateUI() = async(Dispatchers.Main) {
     *     // do something here that updates something in UI
     * }
     * ```
     *
     * When you invoke `asyncUpdateUI` in some background thread, it immediately continues to the next
     * line, while the UI update happens asynchronously in the UI thread. However, if you invoke
     * it in the UI thread itself, it will update the UI _synchronously_ if your `isDispatchNeeded` is
     * overridden with a thread check. Checking if we are already in the UI thread seems more
     * efficient (and it might indeed save a few CPU cycles), but this subtle and context-sensitive
     * difference in behavior makes the resulting async code harder to debug.
     *
     * Basically, the choice here is between the "JS-style" asynchronous approach (async actions
     * are always postponed to be executed later in the event dispatch thread) and "C#-style" approach
     * (async actions are executed in the invoker thread until the first suspension point).
     * While the C# approach seems to be more efficient, it ends up with recommendations like
     * "use `yield` if you need to ....". This is error-prone. The JS-style approach is more consistent
     * and does not require programmers to think about whether they need to yield or not.
     *
     * However, coroutine builders like [launch][CoroutineScope.launch] and [async][CoroutineScope.async] accept an optional [CoroutineStart]
     * parameter that allows one to optionally choose the C#-style [CoroutineStart.UNDISPATCHED] behavior
     * whenever it is needed for efficiency.
     *
     * This method should generally be exception-safe. An exception thrown from this method
     * may leave the coroutines that use this dispatcher in the inconsistent and hard to debug state.
     *
     * **Note: This is an experimental api.** Execution semantics of coroutines may change in the future when this function returns `false`.
     */
    public open fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    /**
     * Dispatches execution of a runnable [block] onto another thread in the given [context].
     *
     * This method should generally be exception-safe. An exception thrown from this method
     * may leave the coroutines that use this dispatcher in the inconsistent and hard to debug state.
     */
    public abstract fun dispatch(context: CoroutineContext, block: Runnable)

    /**
     * Returns a continuation that wraps the provided [continuation], thus intercepting all resumptions.
     *
     * This method should generally be exception-safe. An exception thrown from this method
     * may leave the coroutines that use this dispatcher in the inconsistent and hard to debug state.
     */
    public final override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
            DispatchedContinuation(this, continuation)

//    @InternalCoroutinesApi
    public override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        (continuation as DispatchedContinuation<*>).reusableCancellableContinuation?.detachChild()
    }

    /** @suppress for nicer debugging */
    override fun toString(): String = "$classSimpleName@$hexAddress"
}
