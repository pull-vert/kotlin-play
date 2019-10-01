package coroutines.channels

import coroutines.*
import coroutines.intrinsics.startCoroutineUnintercepted
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
abstract class ProducerChannel {

    companion object {
        @Suppress("RESULT_CLASS_IN_RETURN_TYPE")
        val UNIT_RESULT = Result.success(Unit)
    }

    @JvmField
    protected var consumer: Continuation<Int>? = null

    abstract suspend fun receive(): Int
}

//class CancellableProducerChannel(private val producerBlock: suspend () -> Int) : ProducerChannel() {
//
//    override suspend fun receive(): Int =
//        suspendAtomicCancellableCoroutine {
//            consumer = it.intercepted()
//            producerBlock.createCoroutineUnintercepted(consumer!!).resume(Unit)
//            COROUTINE_SUSPENDED
//        }
//}
//
//class CancellableProducerReusableChannel(private var iterations: Int, private val producerBlock: suspend (Int) -> Int) : ProducerChannel() {
//    override suspend fun receive(): Int = suspendAtomicCancellableCoroutineReusable {
//        consumer = it.intercepted()
//        producerBlock.createCoroutineUnintercepted(iterations--, consumer!!).resume(Unit)
//        COROUTINE_SUSPENDED
//    }
//}

class NonCancellableProducerChannel(private var iterations: Int, private val producerBlock: suspend (Int) -> Int) : ProducerChannel() {
    override suspend fun receive(): Int = suspendCoroutineUninterceptedOrReturn {
        consumer = it.intercepted()
        producerBlock.createCoroutineUnintercepted(iterations--, consumer!!).resumeWith(UNIT_RESULT)
        COROUTINE_SUSPENDED
    }
}
