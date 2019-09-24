package coroutines

import coroutines.internal.LockFreeLinkedListHead

// -------- invokeOnCompletion nodes

internal interface Incomplete {
    val isActive: Boolean
    val list: NodeList? // is null only for Empty and JobNode incomplete state objects
}

internal abstract class JobNode<out J : Job>(
        @JvmField val job: J
) : CompletionHandlerBase(), DisposableHandle, Incomplete {
    override val isActive: Boolean get() = true
    override val list: NodeList? get() = null
    override fun dispose() = (job as JobSupport).removeNode(this)
}

internal class NodeList : LockFreeLinkedListHead(), Incomplete {
    override val isActive: Boolean get() = true
    override val list: NodeList get() = this

    fun getString(state: String) = buildString {
        append("List{")
        append(state)
        append("}[")
        var first = true
        this@NodeList.forEach<JobNode<*>> { node ->
            if (first) first = false else append(", ")
            append(node)
        }
        append("]")
    }

    override fun toString(): String =
            if (DEBUG) getString("Active") else super.toString()
}

// -------- invokeOnCancellation nodes

/**
 * Marker for node that shall be invoked on in _cancelling_ state.
 * **Note: may be invoked multiple times.**
 */
internal abstract class JobCancellingNode<out J : Job>(job: J) : JobNode<J>(job)

// Same as ChildHandleNode, but for cancellable continuation
internal class ChildContinuation(
        parent: Job,
        @JvmField val child: CancellableContinuationImpl<*>
) : JobCancellingNode<Job>(parent) {
    override fun invoke(cause: Throwable?) {
        child.parentCancelled(child.getContinuationCancellationCause(job))
    }
    override fun toString(): String =
            "ChildContinuation[$child]"
}
