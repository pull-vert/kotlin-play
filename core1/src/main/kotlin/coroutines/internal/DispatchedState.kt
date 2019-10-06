package coroutines.internal

import coroutines.*
import java.io.Serializable

internal val UNDEFINED = Symbol("UNDEFINED")

internal object Active : NotCompleted {
    override fun toString(): String = "Active"
}

/**
 * A discriminated union that encapsulates successful outcome with a value of type [T]
 * or a failure with an arbitrary [Throwable] exception.
 */
@Suppress("NON_PUBLIC_PRIMARY_CONSTRUCTOR_OF_INLINE_CLASS")
internal inline class DispatchedState<T> private constructor(
        internal val value: Any?
) : Serializable {
    // discovery

    /**
     * Returns `true` if this instance represents successful outcome.
     * In this case [isFailure] returns `false`.
     */
    val isSuccess: Boolean get() = value !is CompletedExceptionally

    /**
     * Returns `true` if this instance represents CompletedExceptionally (=failed outcome).
     * In this case [isSuccess] returns `false`.
     */
    val isFailure: Boolean get() = value is CompletedExceptionally

    /**
     * Returns `true` if this instance not represents UNDEFINED.
     */
    val isNotUndefined: Boolean get() = value !== UNDEFINED

    /**
     * Returns `true` if this instance represents NotCompleted.
     * In this case [isFailure] returns `false`.
     */
    val isNotCompleted: Boolean get() = value is NotCompleted

    /**
     * Returns `true` if this instance represents Active.
     * In this case [isFailure] returns `false`.
     */
    val isActive: Boolean get() = value is Active

    /**
     * Returns `true` if this instance not represents NotCompleted.
     * Can be a success or a failure
     */
    val isCompleted: Boolean get() = value !is NotCompleted

    /**
     * Returns `true` if this instance represents CompletedIdempotentResult.
     * In this case [isFailure] returns `false`.
     */
    val isCompletedIdempotentResult: Boolean get() = value is CompletedIdempotentResult


    /**
     * Returns `true` if this instance represents failed outcome.
     * In this case [isSuccess] returns `false`.
     */
    val isCancelledContinuation: Boolean get() = value is CancelledContinuation

    /**
     * Returns `true` if this instance represents CancelHandler.
     * In this case [isFailure] returns `false`.
     */
    val isCancelHandler: Boolean get() = value is CancelHandler

    // value & exception retrieval

//    /**
//     * Returns the encapsulated value if this instance represents [success][DispatchedState.isSuccess] or `null`
//     * if it is [failure][DispatchedState.isFailure].
//     *
//     * This function is shorthand for `getOrElse { null }` (see [getOrElse]) or
//     * `fold(onSuccess = { it }, onFailure = { null })` (see [fold]).
//     */
//    inline fun getOrNull(): T? =
//            when {
//                isFailure -> null
//                else -> value as T
//            }

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
                DispatchedState(CompletedExceptionally(exception))

        /**
         * Returns an instance that encapsulates the given [cancelledContinuation] as failure.
         */
        inline fun <T> cancelledContinuation(cancelledContinuation: CancelledContinuation): DispatchedState<T> =
                DispatchedState(cancelledContinuation)

        /**
         * Returns an instance that encapsulates UNDEFINED as successful value.
         */
        inline fun <T> undefined(): DispatchedState<T> =
                DispatchedState(UNDEFINED)

        /**
         * Returns an instance that encapsulates Active as successful value.
         */
        inline fun <T> active(): DispatchedState<T> =
                DispatchedState(Active)

        /**
         * Returns an instance that encapsulates the given [cancelHandler] as successful value.
         */
        inline fun <T> cancelHandle(cancelHandler: CancelHandler): DispatchedState<T> =
                DispatchedState(cancelHandler)

        /**
         * Returns an instance that encapsulates the given [completedWithCancellation] as successful value.
         */
        inline fun <T> completedWithCancellation(completedWithCancellation: CompletedWithCancellation): DispatchedState<T> =
                DispatchedState(completedWithCancellation)

        /**
         * Returns an instance that encapsulates the given [completedIdempotentResult] as successful value.
         */
        inline fun <T> completedIdempotentResult(completedIdempotentResult: CompletedIdempotentResult): DispatchedState<T> =
                DispatchedState(completedIdempotentResult)

    }
}

///**
// * Throws exception if the result is failure. This internal function minimizes
// * inlined bytecode for [getOrThrow] and makes sure that in the future we can
// * add some exception-augmenting logic here (if needed).
// */
//@PublishedApi
//internal fun DispatchedState<*>.throwOnFailure() {
//    if (value is CompletedExceptionally) throw value.cause
//}
//
///**
// * Calls the specified function [block] and returns its encapsulated result if invocation was successful,
// * catching and encapsulating any thrown exception as a failure.
// */
//internal inline fun <R> runCatching(block: () -> R): DispatchedState<R> {
//    return try {
//        DispatchedState.success(block())
//    } catch (e: Throwable) {
//        DispatchedState.failure(e)
//    }
//}
//
///**
// * Calls the specified function [block] with `this` value as its receiver and returns its encapsulated result
// * if invocation was successful, catching and encapsulating any thrown exception as a failure.
// */
//internal inline fun <T, R> T.runCatching(block: T.() -> R): DispatchedState<R> {
//    return try {
//        DispatchedState.success(block())
//    } catch (e: Throwable) {
//        DispatchedState.failure(e)
//    }
//}

// -- extensions ---

///**
// * Returns the encapsulated value if this instance represents [success][DispatchedState.isSuccess] or throws the encapsulated exception
// * if it is [failure][DispatchedState.isFailure].
// *
// * This function is shorthand for `getOrElse { throw it }` (see [getOrElse]).
// */
//internal inline fun <T> DispatchedState<T>.getOrThrow(): T {
//    throwOnFailure()
//    return value as T
//}

// "peek" onto value/exception and pipe

///**
// * Performs the given [action] on encapsulated exception if this instance represents [failure][DispatchedState.isFailure].
// * Returns the original `DispatchedState` unchanged.
// */
//@ExperimentalContracts
//internal inline fun <T> DispatchedState<T>.onFailure(action: (exception: Throwable) -> Unit): DispatchedState<T> {
//    contract {
//        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
//    }
//    exceptionOrNull()?.let { action(it) }
//    return this
//}
//
///**
// * Performs the given [action] on encapsulated value if this instance represents [success][DispatchedState.isSuccess].
// * Returns the original `Result` unchanged.
// */
//@ExperimentalContracts
//internal inline fun <T> DispatchedState<T>.onSuccess(action: (value: T) -> Unit): DispatchedState<T> {
//    contract {
//        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
//    }
//    if (isSuccess) action(value as T)
//    return this
//}

// -------------------

internal fun <T> Result<T>.toState(): DispatchedState<T> = fold({ DispatchedState.success(it) },
        { DispatchedState.failure(it) })

internal fun <T> Result<T>.toState(caller: CancellableContinuation<*>): DispatchedState<T> = fold({ DispatchedState.success(it) },
        { DispatchedState.failure(recoverStackTrace(it, caller)) })

