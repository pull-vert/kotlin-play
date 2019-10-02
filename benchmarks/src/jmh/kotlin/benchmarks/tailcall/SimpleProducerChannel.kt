package benchmarks.tailcall

import coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

interface SimpleProducerChannel<out T> {
    suspend fun receive(): T
}

class NonCancellableProducerChannel<R, T>(iterable: Iterable<R>, private val producerBlock: suspend (R) -> T) : SimpleProducerChannel<T>{
    private val iterator = iterable.iterator()

    override suspend fun receive(): T = suspendCoroutineUninterceptedOrReturn {
        producerBlock.createCoroutineUnintercepted(iterator.next(), it.intercepted()).resume(Unit)
        COROUTINE_SUSPENDED
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
class CancellableProducerReusableChannel<R, T>(iterable: Iterable<R>, private val producerBlock: suspend (R) -> T) : SimpleProducerChannel<T>{
    private val iterator = iterable.iterator()

    override suspend fun receive(): T = suspendAtomicCancellableCoroutineReusable {
        producerBlock.createCoroutineUnintercepted(iterator.next(), it.intercepted()).resume(Unit)
        COROUTINE_SUSPENDED
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
class CancellableIterableReusableChannel<out T> (iterable: Iterable<T>) : SimpleProducerChannel<T> {
    private val iterator = iterable.iterator()

    override suspend fun receive(): T = suspendAtomicCancellableCoroutineReusable {
        it.intercepted().resume(iterator.next())
        COROUTINE_SUSPENDED
    }
}

class NonCancellableIterableChannel<out T> (iterable: Iterable<T>) : SimpleProducerChannel<T> {
    private val iterator = iterable.iterator()

    override suspend fun receive(): T = suspendCoroutineUninterceptedOrReturn {
        it.intercepted().resume(iterator.next())
        COROUTINE_SUSPENDED
    }
}
