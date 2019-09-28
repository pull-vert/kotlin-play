package coroutines

import coroutines.internal.restoreThreadContext
import coroutines.internal.updateThreadContext
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
