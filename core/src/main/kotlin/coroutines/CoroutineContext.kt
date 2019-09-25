package coroutines

import coroutines.internal.restoreThreadContext
import coroutines.internal.updateThreadContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Executes a block using a given coroutine context.
 */
internal inline fun <T> withCoroutineContext(context: CoroutineContext, countOrElement: Any?, block: () -> T): T {
    val oldValue = updateThreadContext(context, countOrElement)
    try {
        return block()
    } finally {
        restoreThreadContext(context, oldValue)
    }
}

internal val CoroutineContext.coroutineName: String? get() {
    if (!DEBUG) return null
    val coroutineId = this[CoroutineId] ?: return null
    val coroutineName = this[CoroutineName]?.name ?: "coroutine"
    return "$coroutineName#${coroutineId.id}"
}

private const val DEBUG_THREAD_NAME_SEPARATOR = " @"

internal data class CoroutineId(
        val id: Long
) : ThreadContextElement<String>, AbstractCoroutineContextElement(CoroutineId) {
    companion object Key : CoroutineContext.Key<CoroutineId>
    override fun toString(): String = "CoroutineId($id)"

    override fun updateThreadContext(context: CoroutineContext): String {
        val coroutineName = context[CoroutineName]?.name ?: "coroutine"
        val currentThread = Thread.currentThread()
        val oldName = currentThread.name
        var lastIndex = oldName.lastIndexOf(DEBUG_THREAD_NAME_SEPARATOR)
        if (lastIndex < 0) lastIndex = oldName.length
        currentThread.name = buildString(lastIndex + coroutineName.length + 10) {
            append(oldName.substring(0, lastIndex))
            append(DEBUG_THREAD_NAME_SEPARATOR)
            append(coroutineName)
            append('#')
            append(id)
        }
        return oldName
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String) {
        Thread.currentThread().name = oldState
    }
}
