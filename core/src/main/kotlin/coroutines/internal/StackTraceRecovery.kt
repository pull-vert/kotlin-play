package coroutines.internal

import coroutines.RECOVER_STACK_TRACES
import java.util.*
import kotlin.coroutines.Continuation

/*
 * `Class.forName(name).canonicalName` instead of plain `name` is required to properly handle
 * Android's minifier that renames these classes and breaks our recovery heuristic without such lookup.
 */
private const val baseContinuationImplClass = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

private val baseContinuationImplClassName = runCatching {
    Class.forName(baseContinuationImplClass).canonicalName
}.getOrElse { baseContinuationImplClass }

/**
 * Find initial cause of the exception without restored stacktrace.
 * Returns intermediate stacktrace as well in order to avoid excess cloning of array as an optimization.
 */
private fun <E : Throwable> E.causeAndStacktrace(): Pair<E, Array<StackTraceElement>> {
    val cause = cause
    return if (cause != null && cause.javaClass == javaClass) {
        val currentTrace = stackTrace
        if (currentTrace.any { it.isArtificial() })
            cause as E to currentTrace
        else this to emptyArray()
    } else {
        this to emptyArray()
    }
}

private fun mergeRecoveredTraces(recoveredStacktrace: Array<StackTraceElement>, result: ArrayDeque<StackTraceElement>) {
    // Merge two stacktraces and trim common prefix
    val startIndex = recoveredStacktrace.indexOfFirst { it.isArtificial() } + 1
    val lastFrameIndex = recoveredStacktrace.size - 1
    for (i in lastFrameIndex downTo startIndex) {
        val element = recoveredStacktrace[i]
        if (element.elementWiseEquals(result.last)) {
            result.removeLast()
        }
        result.addFirst(recoveredStacktrace[i])
    }
}

/**
 * @suppress
 */
//@InternalCoroutinesApi
public fun artificialFrame(message: String) = java.lang.StackTraceElement("\b\b\b($message", "\b", "\b", -1)
internal fun StackTraceElement.isArtificial() = className.startsWith("\b\b\b")
private fun Array<StackTraceElement>.frameIndex(methodName: String) = indexOfFirst { methodName == it.className }

internal fun <E : Throwable> recoverStackTrace(exception: E, continuation: Continuation<*>): E {
    if (!RECOVER_STACK_TRACES || continuation !is CoroutineStackFrame) return exception
    return recoverFromStackFrame(exception, continuation)
}

private fun <E : Throwable> recoverFromStackFrame(exception: E, continuation: CoroutineStackFrame): E {
    /*
    * Here we are checking whether exception has already recovered stacktrace.
    * If so, we extract initial and merge recovered stacktrace and current one
    */
    val (cause, recoveredStacktrace) = exception.causeAndStacktrace()

    // Try to create an exception of the same type and get stacktrace from continuation
    val newException = tryCopyException(cause) ?: return exception
    val stacktrace = createStackTrace(continuation)
    if (stacktrace.isEmpty()) return exception

    // Merge if necessary
    if (cause !== exception) {
        mergeRecoveredTraces(recoveredStacktrace, stacktrace)
    }

    // Take recovered stacktrace, merge it with existing one if necessary and return
    return createFinalException(cause, newException, stacktrace)
}

/*
 * Here we partially copy original exception stackTrace to make current one much prettier.
 * E.g. for
 * ```
 * fun foo() = async { error(...) }
 * suspend fun bar() = foo().await()
 * ```
 * we would like to produce following exception:
 * IllegalStateException
 *   at foo
 *   at kotlin.coroutines.resumeWith
 *   (Coroutine boundary)
 *   at bar
 *   ...real stackTrace...
 * caused by "IllegalStateException" (original one)
 */
private fun <E : Throwable> createFinalException(cause: E, result: E, resultStackTrace: ArrayDeque<StackTraceElement>): E {
    resultStackTrace.addFirst(artificialFrame("Coroutine boundary"))
    val causeTrace = cause.stackTrace
    val size = causeTrace.frameIndex(baseContinuationImplClassName)
    if (size == -1) {
        result.stackTrace = resultStackTrace.toTypedArray()
        return result
    }

    val mergedStackTrace = arrayOfNulls<StackTraceElement>(resultStackTrace.size + size)
    for (i in 0 until size) {
        mergedStackTrace[i] = causeTrace[i]
    }

    for ((index, element) in resultStackTrace.withIndex()) {
        mergedStackTrace[size + index] = element
    }

    result.stackTrace = mergedStackTrace
    return result
}

private fun StackTraceElement.elementWiseEquals(e: StackTraceElement): Boolean {
    /*
     * In order to work on Java 9 where modules and classloaders of enclosing class
     * are part of the comparison
     */
    return lineNumber == e.lineNumber && methodName == e.methodName
            && fileName == e.fileName && className == e.className
}

internal typealias CoroutineStackFrame = kotlin.coroutines.jvm.internal.CoroutineStackFrame

private fun createStackTrace(continuation: CoroutineStackFrame): ArrayDeque<StackTraceElement> {
    val stack = ArrayDeque<StackTraceElement>()
    continuation.getStackTraceElement()?.let { stack.add(it) }

    var last = continuation
    while (true) {
        last = (last as? CoroutineStackFrame)?.callerFrame ?: break
        last.getStackTraceElement()?.let { stack.add(it) }
    }
    return stack
}
