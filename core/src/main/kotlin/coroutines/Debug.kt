package coroutines

import coroutines.internal.systemProp
import java.util.concurrent.atomic.AtomicLong

/**
 * Name of the property that controls coroutine debugging.
 *
 * ### Debugging facilities
 *
 * In debug mode every coroutine is assigned a unique consecutive identifier.
 * Every thread that executes a coroutine has its name modified to include the name and identifier of
 * the currently running coroutine.
 *
 * Enable debugging facilities with "`kotlinx.coroutines.debug`" ([DEBUG_PROPERTY_NAME]) system property,
 * use the following values:
 *
 * * "`auto`" (default mode, [DEBUG_PROPERTY_VALUE_AUTO]) -- enabled when assertions are enabled with "`-ea`" JVM option.
 * * "`on`" ([DEBUG_PROPERTY_VALUE_ON]) or empty string -- enabled.
 * * "`off`" ([DEBUG_PROPERTY_VALUE_OFF]) -- disabled.
 *
 * Coroutine name can be explicitly assigned using [CoroutineName] context element.
 * The string "coroutine" is used as a default name.
 *
 * Debugging facilities are implemented by [newCoroutineContext][CoroutineScope.newCoroutineContext] function that
 * is used in all coroutine builders to create context of a new coroutine.
 */
public const val DEBUG_PROPERTY_NAME = "kotlinx.coroutines.debug"

/**
 * Name of the boolean property that controls stacktrace recovery (enabled by default) on JVM.
 * Stacktrace recovery is enabled if both debug and stacktrace recovery modes are enabled.
 *
 * Stacktrace recovery mode wraps every exception into the exception of the same type with original exception
 * as cause, but with stacktrace of the current coroutine.
 * Exception is instantiated using reflection by using no-arg, cause or cause and message constructor.
 *
 * This mechanism is currently supported for channels, [async], [launch], [coroutineScope], [supervisorScope]
 * and [withContext] builders.
 */
internal const val STACKTRACE_RECOVERY_PROPERTY_NAME = "kotlinx.coroutines.stacktrace.recovery"

/**
 * Throwable which can be cloned during stacktrace recovery in a class-specific way.
 * For additional information about stacktrace recovery see [STACKTRACE_RECOVERY_PROPERTY_NAME]
 *
 * Example of usage:
 * ```
 * class BadResponseCodeException(val responseCode: Int) : Exception(), CopyableThrowable<BadResponseCodeException> {
 *
 *  override fun createCopy(): BadResponseCodeException {
 *    val result = BadResponseCodeException(responseCode)
 *    result.initCause(this)
 *    return result
 *  }
 * ```
 */
//@ExperimentalCoroutinesApi
public interface CopyableThrowable<T> where T : Throwable, T : CopyableThrowable<T> {

    /**
     * Creates a copy of the current instance.
     * For better debuggability, it is recommended to use original exception as [cause][Throwable.cause] of the resulting one.
     * Stacktrace of copied exception will be overwritten by stacktrace recovery machinery by [Throwable.setStackTrace] call.
     * An exception can opt-out of copying by returning `null` from this function.
     */
    public fun createCopy(): T?
}

/**
 * Automatic debug configuration value for [DEBUG_PROPERTY_NAME].
 */
public const val DEBUG_PROPERTY_VALUE_AUTO = "auto"

/**
 * Debug turned on value for [DEBUG_PROPERTY_NAME].
 */
public const val DEBUG_PROPERTY_VALUE_ON = "on"

/**
 * Debug turned on value for [DEBUG_PROPERTY_NAME].
 */
public const val DEBUG_PROPERTY_VALUE_OFF = "off"

// @JvmField: Don't use JvmField here to enable R8 optimizations via "assumenosideeffects"
internal val ASSERTIONS_ENABLED = CoroutineId::class.java.desiredAssertionStatus()

// @JvmField: Don't use JvmField here to enable R8 optimizations via "assumenosideeffects"
internal val DEBUG = systemProp(DEBUG_PROPERTY_NAME).let { value ->
    when (value) {
        DEBUG_PROPERTY_VALUE_AUTO, null -> ASSERTIONS_ENABLED
        DEBUG_PROPERTY_VALUE_ON, "" -> true
        DEBUG_PROPERTY_VALUE_OFF -> false
        else -> error("System property '$DEBUG_PROPERTY_NAME' has unrecognized value '$value'")
    }
}

// Note: stack-trace recovery is enabled only in debug mode
// @JvmField: Don't use JvmField here to enable R8 optimizations via "assumenosideeffects"
internal val RECOVER_STACK_TRACES =
        DEBUG && systemProp(STACKTRACE_RECOVERY_PROPERTY_NAME, true)

//@InlineOnly
internal inline fun assert(value: () -> Boolean) {
    if (ASSERTIONS_ENABLED && !value()) throw AssertionError()
}

// It is used only in debug mode
internal val COROUTINE_ID = AtomicLong(0)
