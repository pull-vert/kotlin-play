package coroutines.internal

import coroutines.CompletedExceptionally
import kotlin.contracts.*
import java.io.Serializable

/**
 * A discriminated union that encapsulates successful outcome with a value of type [T]
 * or a failure with an arbitrary [Throwable] exception.
 */
@Suppress("NON_PUBLIC_PRIMARY_CONSTRUCTOR_OF_INLINE_CLASS")
internal inline class DispatchedState<out T> internal constructor(
        internal val value: Any?
) : Serializable {
    // discovery

    /**
     * Returns `true` if this instance represents successful outcome.
     * In this case [isFailure] returns `false`.
     */
    val isSuccess: Boolean get() = value !is CompletedExceptionally

    /**
     * Returns `true` if this instance represents failed outcome.
     * In this case [isSuccess] returns `false`.
     */
    val isFailure: Boolean get() = value is CompletedExceptionally

    // value & exception retrieval

    /**
     * Returns the encapsulated value if this instance represents [success][DispatchedState.isSuccess] or `null`
     * if it is [failure][DispatchedState.isFailure].
     *
     * This function is shorthand for `getOrElse { null }` (see [getOrElse]) or
     * `fold(onSuccess = { it }, onFailure = { null })` (see [fold]).
     */
    inline fun getOrNull(): T? =
            when {
                isFailure -> null
                else -> value as T
            }

    /**
     * Returns the encapsulated exception if this instance represents [failure][isFailure] or `null`
     * if it is [success][isSuccess].
     *
     * This function is shorthand for `fold(onSuccess = { null }, onFailure = { it })` (see [fold]).
     */
    fun exceptionOrNull(): Throwable? =
            when (value) {
                is CompletedExceptionally -> value.cause
                else -> null
            }

    /**
     * Returns a string `Success(v)` if this instance represents [success][DispatchedState.isSuccess]
     * where `v` is a string representation of the value or a string `Failure(x)` if
     * it is [failure][isFailure] where `x` is a string representation of the exception.
     */
    override fun toString(): String =
            when (value) {
                is CompletedExceptionally -> value.toString()
                else -> "Success($value)"
            }

    // companion with constructors

    /**
     * Companion object for [Result] class that contains its constructor functions
     * [success] and [failure].
     */
    companion object {
        /**
         * Returns an instance that encapsulates the given [value] as successful value.
         */
        inline fun <T> success(value: T): DispatchedState<T> =
                DispatchedState(value)

        /**
         * Returns an instance that encapsulates the given [exception] as failure.
         */
        inline fun <T> failure(exception: Throwable): DispatchedState<T> =
                DispatchedState(createFailure(exception))
    }
}

/**
 * Creates an instance of internal marker [DispatchedState.CompletedExceptionally] class to
 * make sure that this class is not exposed in ABI.
 */
@PublishedApi
internal fun createFailure(exception: Throwable): Any = CompletedExceptionally(exception)

/**
 * Throws exception if the result is failure. This internal function minimizes
 * inlined bytecode for [getOrThrow] and makes sure that in the future we can
 * add some exception-augmenting logic here (if needed).
 */
@PublishedApi
internal fun DispatchedState<*>.throwOnFailure() {
    if (value is CompletedExceptionally) throw value.cause
}

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation was successful,
 * catching and encapsulating any thrown exception as a failure.
 */
internal inline fun <R> runCatching(block: () -> R): DispatchedState<R> {
    return try {
        DispatchedState.success(block())
    } catch (e: Throwable) {
        DispatchedState.failure(e)
    }
}

/**
 * Calls the specified function [block] with `this` value as its receiver and returns its encapsulated result
 * if invocation was successful, catching and encapsulating any thrown exception as a failure.
 */
internal inline fun <T, R> T.runCatching(block: T.() -> R): DispatchedState<R> {
    return try {
        DispatchedState.success(block())
    } catch (e: Throwable) {
        DispatchedState.failure(e)
    }
}

// -- extensions ---

/**
 * Returns the encapsulated value if this instance represents [success][DispatchedState.isSuccess] or throws the encapsulated exception
 * if it is [failure][DispatchedState.isFailure].
 *
 * This function is shorthand for `getOrElse { throw it }` (see [getOrElse]).
 */
internal inline fun <T> DispatchedState<T>.getOrThrow(): T {
    throwOnFailure()
    return value as T
}

/**
 * Returns the encapsulated value if this instance represents [success][DispatchedState.isSuccess] or the
 * result of [onFailure] function for encapsulated exception if it is [failure][DispatchedState.isFailure].
 *
 * Note, that an exception thrown by [onFailure] function is rethrown by this function.
 *
 * This function is shorthand for `fold(onSuccess = { it }, onFailure = onFailure)` (see [fold]).
 */
@ExperimentalContracts
internal inline fun <R, T : R> DispatchedState<T>.getOrElse(onFailure: (exception: Throwable) -> R): R {
    contract {
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (val exception = exceptionOrNull()) {
        null -> value as T
        else -> onFailure(exception)
    }
}

/**
 * Returns the encapsulated value if this instance represents [success][DispatchedState.isSuccess] or the
 * [defaultValue] if it is [failure][DispatchedState.isFailure].
 *
 * This function is shorthand for `getOrElse { defaultValue }` (see [getOrElse]).
 */
internal inline fun <R, T : R> DispatchedState<T>.getOrDefault(defaultValue: R): R {
    if (isFailure) return defaultValue
    return value as T
}

/**
 * Returns the the result of [onSuccess] for encapsulated value if this instance represents [success][DispatchedState.isSuccess]
 * or the result of [onFailure] function for encapsulated exception if it is [failure][DispatchedState.isFailure].
 *
 * Note, that an exception thrown by [onSuccess] or by [onFailure] function is rethrown by this function.
 */
@ExperimentalContracts
internal inline fun <R, T> DispatchedState<T>.fold(
        onSuccess: (value: T) -> R,
        onFailure: (exception: Throwable) -> R
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (val exception = exceptionOrNull()) {
        null -> onSuccess(value as T)
        else -> onFailure(exception)
    }
}

// transformation

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated value
 * if this instance represents [success][DispatchedState.isSuccess] or the
 * original encapsulated exception if it is [failure][DispatchedState.isFailure].
 *
 * Note, that an exception thrown by [transform] function is rethrown by this function.
 * See [mapCatching] for an alternative that encapsulates exceptions.
 */
@ExperimentalContracts
internal inline fun <R, T> DispatchedState<T>.map(transform: (value: T) -> R): DispatchedState<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return when {
        isSuccess -> DispatchedState.success(transform(value as T))
        else -> DispatchedState(value)
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated value
 * if this instance represents [success][DispatchedState.isSuccess] or the
 * original encapsulated exception if it is [failure][DispatchedState.isFailure].
 *
 * Any exception thrown by [transform] function is caught, encapsulated as a failure and returned by this function.
 * See [map] for an alternative that rethrows exceptions.
 */
internal inline fun <R, T> DispatchedState<T>.mapCatching(transform: (value: T) -> R): DispatchedState<R> {
    return when {
        isSuccess -> runCatching { transform(value as T) }
        else -> DispatchedState(value)
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated exception
 * if this instance represents [failure][DispatchedState.isFailure] or the
 * original encapsulated value if it is [success][DispatchedState.isSuccess].
 *
 * Note, that an exception thrown by [transform] function is rethrown by this function.
 * See [recoverCatching] for an alternative that encapsulates exceptions.
 */
@ExperimentalContracts
internal inline fun <R, T : R> DispatchedState<T>.recover(transform: (exception: Throwable) -> R): DispatchedState<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> DispatchedState.success(transform(exception))
    }
}

/**
 * Returns the encapsulated result of the given [transform] function applied to encapsulated exception
 * if this instance represents [failure][DispatchedState.isFailure] or the
 * original encapsulated value if it is [success][DispatchedState.isSuccess].
 *
 * Any exception thrown by [transform] function is caught, encapsulated as a failure and returned by this function.
 * See [recover] for an alternative that rethrows exceptions.
 */
internal inline fun <R, T : R> DispatchedState<T>.recoverCatching(transform: (exception: Throwable) -> R): DispatchedState<R> {
    val value = value // workaround for inline classes BE bug
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> runCatching { transform(exception) }
    }
}

// "peek" onto value/exception and pipe

/**
 * Performs the given [action] on encapsulated exception if this instance represents [failure][DispatchedState.isFailure].
 * Returns the original `DispatchedState` unchanged.
 */
@ExperimentalContracts
internal inline fun <T> DispatchedState<T>.onFailure(action: (exception: Throwable) -> Unit): DispatchedState<T> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    exceptionOrNull()?.let { action(it) }
    return this
}

/**
 * Performs the given [action] on encapsulated value if this instance represents [success][DispatchedState.isSuccess].
 * Returns the original `Result` unchanged.
 */
@ExperimentalContracts
internal inline fun <T> DispatchedState<T>.onSuccess(action: (value: T) -> Unit): DispatchedState<T> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    if (isSuccess) action(value as T)
    return this
}

// -------------------

