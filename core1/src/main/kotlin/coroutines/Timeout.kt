/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package coroutines

import coroutines.internal.*
import coroutines.intrinsics.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.jvm.*

/**
 * Runs a given suspending [block] of code inside a coroutine with a specified [timeout][timeMillis] and throws
 * a [TimeoutCancellationException] if the timeout was exceeded.
 *
 * The code that is executing inside the [block] is cancelled on timeout and the active or next invocation of
 * the cancellable suspending function inside the block throws a [TimeoutCancellationException].
 *
 * The sibling function that does not throw an exception on timeout is [withTimeoutOrNull].
 * Note that the timeout action can be specified for a [select] invocation with [onTimeout][SelectBuilder.onTimeout] clause.
 *
 * Implementation note: how the time is tracked exactly is an implementation detail of the context's [CoroutineDispatcher].
 *
 * @param timeMillis timeout time in milliseconds.
 */
public suspend fun <T> withTimeout(timeMillis: Long, block: suspend CoroutineScope.() -> T): T {
    if (timeMillis <= 0L) throw TimeoutCancellationException("Timed out immediately")
    return suspendCoroutineUninterceptedOrReturn { uCont ->
        setupTimeout(TimeoutCoroutine(timeMillis, uCont), block)
    }
}

/**
 * Runs a given suspending block of code inside a coroutine with a specified [timeout][timeMillis] and returns
 * `null` if this timeout was exceeded.
 *
 * The code that is executing inside the [block] is cancelled on timeout and the active or next invocation of
 * cancellable suspending function inside the block throws a [TimeoutCancellationException].
 *
 * The sibling function that throws an exception on timeout is [withTimeout].
 * Note that the timeout action can be specified for a [select] invocation with [onTimeout][SelectBuilder.onTimeout] clause.
 *
 * Implementation note: how the time is tracked exactly is an implementation detail of the context's [CoroutineDispatcher].
 *
 * @param timeMillis timeout time in milliseconds.
 */
public suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend CoroutineScope.() -> T): T? {
    if (timeMillis <= 0L) return null

    var coroutine: TimeoutCoroutine<T?, T?>? = null
    try {
        return suspendCoroutineUninterceptedOrReturn { uCont ->
            val timeoutCoroutine = TimeoutCoroutine(timeMillis, uCont)
            coroutine = timeoutCoroutine
            setupTimeout<T?, T?>(timeoutCoroutine, block)
        }
    } catch (e: TimeoutCancellationException) {
        // Return null if it's our exception, otherwise propagate it upstream (e.g. in case of nested withTimeouts)
        if (e.coroutine === coroutine) {
            return null
        }
        throw e
    }
}

private fun <U, T: U> setupTimeout(
    coroutine: TimeoutCoroutine<U, T>,
    block: suspend CoroutineScope.() -> T
): Any? {
    // schedule cancellation of this coroutine on time
    val cont = coroutine.uCont
    val context = cont.context
    coroutine.disposeOnCompletion(context.delay.invokeOnTimeout(coroutine.time, coroutine))
    // restart the block using a new coroutine with a new job,
    // however, start it undispatched, because we already are in the proper context
    return coroutine.startUndispatchedOrReturnIgnoreTimeout(coroutine, block)
}

private open class TimeoutCoroutine<U, in T: U>(
    @JvmField val time: Long,
    uCont: Continuation<U> // unintercepted continuation
) : ScopeCoroutine<T>(uCont.context, uCont), Runnable {
    override fun run() {
        cancelCoroutine(TimeoutCancellationException(time, this))
    }

    override fun nameString(): String =
        "${super.nameString()}(timeMillis=$time)"
}

/**
 * This exception is thrown by [withTimeout] to indicate timeout.
 */
public class TimeoutCancellationException internal constructor(
    message: String,
    @JvmField internal val coroutine: Job?
) : CancellationException(message) {
    /**
     * Creates a timeout exception with the given message.
     * This constructor is needed for exception stack-traces recovery.
     */
    @Suppress("UNUSED")
    internal constructor(message: String) : this(message, null)
}

@Suppress("FunctionName")
internal fun TimeoutCancellationException(
    time: Long,
    coroutine: Job
) : TimeoutCancellationException = TimeoutCancellationException("Timed out waiting for $time ms", coroutine)


