package coroutines.internal

import coroutines.CoroutineDispatcher
import coroutines.MODE_ATOMIC_DEFAULT
import coroutines.assert
import kotlin.coroutines.Continuation

//@SharedImmutable
private val UNDEFINED = Symbol("UNDEFINED")

// Internal to avoid synthetic accessors
//@SharedImmutable
@JvmField
internal val NON_REUSABLE = Symbol("NON_REUSABLE")

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

    override fun takeState(): Any? {
        val state = _state
        assert { state !== UNDEFINED } // fail-fast if repeatedly invoked
        _state = UNDEFINED
        return state
    }

    override val delegate: Continuation<T>
        get() = this
}
