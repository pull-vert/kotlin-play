package coroutines

import kotlin.coroutines.ContinuationInterceptor

/**
 * Groups various implementations of [CoroutineDispatcher].
 */
public object Dispatchers {
    /**
     * The default [CoroutineDispatcher] that is used by all standard builders like
     * [launch][CoroutineScope.launch], [async][CoroutineScope.async], etc
     * if no dispatcher nor any other [ContinuationInterceptor] is specified in their context.
     *
     * It is backed by a shared pool of threads on JVM. By default, the maximal level of parallelism used
     * by this dispatcher is equal to the number of CPU cores, but is at least two.
     * Level of parallelism X guarantees that no more than X tasks can be executed in this dispatcher in parallel.
     */
    @JvmStatic
    public val Default: CoroutineDispatcher = createDefaultDispatcher()
}
