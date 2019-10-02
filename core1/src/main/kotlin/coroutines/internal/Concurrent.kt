package coroutines.internal

import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

internal fun <E> identitySet(expectedSize: Int): MutableSet<E> = Collections.newSetFromMap(IdentityHashMap(expectedSize))

private val REMOVE_FUTURE_ON_CANCEL: Method? = try {
    ScheduledThreadPoolExecutor::class.java.getMethod("setRemoveOnCancelPolicy", Boolean::class.java)
} catch (e: Throwable) {
    null
}

@Suppress("NAME_SHADOWING")
internal fun removeFutureOnCancel(executor: Executor): Boolean {
    try {
        val executor = executor as? ScheduledExecutorService ?: return false
        (REMOVE_FUTURE_ON_CANCEL ?: return false).invoke(executor, true)
        return true
    } catch (e: Throwable) {
        return true
    }
}
