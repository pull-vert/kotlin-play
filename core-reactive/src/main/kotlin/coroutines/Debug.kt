package coroutines

// @JvmField: Don't use JvmField here to enable R8 optimizations via "assumenosideeffects"
internal val ASSERTIONS_ENABLED = CoroutineId::class.java.desiredAssertionStatus()

//@InlineOnly
internal inline fun assert(value: () -> Boolean) {
    if (ASSERTIONS_ENABLED && !value()) throw AssertionError()
}
