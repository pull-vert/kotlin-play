package coroutines.internal

/**
 * A symbol class that is used to define unique constants that are self-explanatory in debugger.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
internal class Symbol(val symbol: String) {
    override fun toString(): String = symbol

    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    inline fun <T> unbox(value: Any?): T = if (value === this) null as T else value as T
}
