package coroutines

import kotlin.coroutines.Continuation

// internal debugging tools for string representation

internal val Any.hexAddress: String
    get() = Integer.toHexString(System.identityHashCode(this))

internal fun Continuation<*>.toDebugString(): String = when (this) {
    is DispatchedContinuation -> toString()
    // Workaround for #858
    else -> runCatching { "$this@$hexAddress" }.getOrElse { "${this::class.java.name}@$hexAddress" }
}

internal val Any.classSimpleName: String get() = this::class.java.simpleName
