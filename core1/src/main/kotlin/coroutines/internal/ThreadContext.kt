package coroutines.internal

import coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

private val ZERO = Symbol("ZERO")

// Used when there are >= 2 active elements in the context
private class ThreadState(val context: CoroutineContext, n: Int) {
    private var a = arrayOfNulls<Any>(n)
    private var i = 0

    fun append(value: Any?) { a[i++] = value }
    fun take() = a[i++]
    fun start() { i = 0 }
}

// Counts ThreadContextElements in the context
// Any? here is Int | ThreadContextElement (when count is one)
private val countAll =
        fun (countOrElement: Any?, element: CoroutineContext.Element): Any? {
            if (element is ThreadContextElement<*>) {
                val inCount = countOrElement as? Int ?: 1
                return if (inCount == 0) element else inCount + 1
            }
            return countOrElement
        }

// Find one (first) ThreadContextElement in the context, it is used when we know there is exactly one
private val findOne =
        fun (found: ThreadContextElement<*>?, element: CoroutineContext.Element): ThreadContextElement<*>? {
            if (found != null) return found
            return element as? ThreadContextElement<*>
        }

// Updates state for ThreadContextElements in the context using the given ThreadState
private val updateState =
        fun (state: ThreadState, element: CoroutineContext.Element): ThreadState {
            if (element is ThreadContextElement<*>) {
                state.append(element.updateThreadContext(state.context))
            }
            return state
        }

// Restores state for all ThreadContextElements in the context from the given ThreadState
private val restoreState =
        fun (state: ThreadState, element: CoroutineContext.Element): ThreadState {
            @Suppress("UNCHECKED_CAST")
            if (element is ThreadContextElement<*>) {
                (element as ThreadContextElement<Any?>).restoreThreadContext(state.context, state.take())
            }
            return state
        }

internal fun threadContextElements(context: CoroutineContext): Any = context.fold(0, countAll)!!

// countOrElement is pre-cached in dispatched continuation
internal fun updateThreadContext(context: CoroutineContext, countOrElement: Any?): Any? {
    @Suppress("NAME_SHADOWING")
    val countOrElement = countOrElement ?: threadContextElements(context)
    @Suppress("IMPLICIT_BOXING_IN_IDENTITY_EQUALS")
    return when {
        countOrElement === 0 -> ZERO // very fast path when there are no active ThreadContextElements
        //    ^^^ identity comparison for speed, we know zero always has the same identity
        countOrElement is Int -> {
            // slow path for multiple active ThreadContextElements, allocates ThreadState for multiple old values
            context.fold(ThreadState(context, countOrElement), updateState)
        }
        else -> {
            // fast path for one ThreadContextElement (no allocations, no additional context scan)
            @Suppress("UNCHECKED_CAST")
            val element = countOrElement as ThreadContextElement<Any?>
            element.updateThreadContext(context)
        }
    }
}

internal fun restoreThreadContext(context: CoroutineContext, oldState: Any?) {
    when {
        oldState === ZERO -> return // very fast path when there are no ThreadContextElements
        oldState is ThreadState -> {
            // slow path with multiple stored ThreadContextElements
            oldState.start()
            context.fold(oldState, restoreState)
        }
        else -> {
            // fast path for one ThreadContextElement, but need to find it
            @Suppress("UNCHECKED_CAST")
            val element = context.fold(null, findOne) as ThreadContextElement<Any?>
            element.restoreThreadContext(context, oldState)
        }
    }
}
