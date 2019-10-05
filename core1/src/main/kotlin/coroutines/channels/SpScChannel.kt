@file:JvmName("SpScChannel")

package coroutines.channels

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original License: https://github.com/JCTools/JCTools/blob/master/LICENSE
 * Original location: https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/atomic/SpscAtomicArrayQueue.java
 */
import coroutines.classSimpleName
import coroutines.suspendAtomicCancellableCoroutineReusable
import kotlinx.atomicfu.atomic
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.resume

/**
 *
 * Lock-free Single-Producer Single-Consumer Queue backed by a pre-allocated buffer.
 * Based on Ring Buffer = circular array = circular buffer
 * http://psy-lob-saw.blogspot.fr/2014/04/notes-on-concurrent-ring-buffer-queue.html
 * http://psy-lob-saw.blogspot.fr/2014/01/picking-2013-spsc-queue-champion.html
 * Inspirition https://www.codeproject.com/Articles/43510/Lock-Free-Single-Producer-Single-Consumer-Circular
 *
 * <p>
 * This implementation is a mashup of the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * algorithm with an optimization of the offer method taken from the <a
 * href="http://staff.ustc.edu.cn/~bhua/publications/IJPP_draft.pdf">BQueue</a> algorithm (a variation on Fast
 * Flow), and adjusted to comply with Queue.offer semantics with regards to capacity.<br>
 * For convenience the relevant papers are available in the resources folder:<br>
 * <i>2010 - Pisa - SPSC Queues on Shared Cache Multi-Core Systems.pdf<br>
 * 2012 - Junchang- BQueue- Efﬁcient and Practical Queuing.pdf <br>
 * </i> This implementation is wait free.
 *
 * @param <E> Not null value
 * @author nitsanw, adapted by pull-vert
 */
open class SpScChannel<E : Any>(
        /**
         * Buffer capacity.
         */
        capacity: Int
) : SpscAtomicArrayQueueL5Pad<ValueOrClosed<E>>(capacity), Channel<E> {

    // State transitions: null -> handler -> HANDLER_INVOKED
    private val onCloseHandler = atomic<Any?>(null)

    override val isClosedForSend: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override suspend fun send(element: E) {
        val producerIndex = lvProducerIndex()
        sendInternal(element, producerIndex)
    }

    private suspend fun sendInternal(element: E, producerIndex: Long) {
        // fast path -- try offer non-blocking
        if (offerInternal(ValueOrClosed.value(element), producerIndex)) return
        // slow-path does suspend
        sendSuspend()
        sendInternal(element, producerIndex) // re-call send after suspension
    }

    override fun offer(element: E): Boolean {
        val producerIndex = lvProducerIndex()
        // fast path -- try offer non-blocking
        return offerInternal(ValueOrClosed.value(element), producerIndex)
    }

    override fun close(cause: Throwable?): Boolean {
        val closed = ValueOrClosed.closed<E>(cause)
        /*
         * Try to commit close by adding a close token to the end of the queue.
         * Successful -> we're now responsible for closing receivers
         * Not successful -> help closing pending receivers to maintain invariant
         * "if (!close()) next send will throw"
         */
        // simple implementation : just add it or throw on continuation
        while (true) {
            val producerIndex = lvProducerIndex()
            // fast path -- try offer non-blocking
            val closeAdded = offerInternal(closed, producerIndex)
            if (closeAdded) {
                invokeOnCloseHandler(cause)
                return true
            }
        }
    }

    private fun invokeOnCloseHandler(cause: Throwable?) {
        val handler = onCloseHandler.value
        if (handler !== null && handler !== HANDLER_INVOKED
                && onCloseHandler.compareAndSet(handler, HANDLER_INVOKED)) {
            // CAS failed -> concurrent invokeOnClose() invoked handler
            @Suppress("UNCHECKED_CAST")
            (handler as Handler)(cause)
        }
    }

    override fun invokeOnClose(handler: Handler) {
        // Intricate dance for concurrent invokeOnClose and close calls
        if (!onCloseHandler.compareAndSet(null, handler)) {
            val value = onCloseHandler.value
            if (value === HANDLER_INVOKED) {
                throw IllegalStateException("Another handler was already registered and successfully invoked")
            }

            throw IllegalStateException("Another handler was already registered: $value")
        } else {
            // todo
//            val closedToken = closedForSend
//            if (closedToken != null && onCloseHandler.compareAndSet(handler, HANDLER_INVOKED)) {
//                // CAS failed -> close() call invoked handler
//                (handler)(closedToken.closeCause)
//            }
        }
    }

    override val isClosedForReceive: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val isEmpty: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override suspend fun receive(): E {
        val consumerIndex = lvConsumerIndex()
        return receiveInternal(consumerIndex).value
    }

    override suspend fun receiveOrClosed(): ValueOrClosed<E> {
        val consumerIndex = lvConsumerIndex()
        return receiveInternal(consumerIndex)
    }

    override fun poll(): E? {
        val consumerIndex = lvConsumerIndex()
        return pollInternal(consumerIndex)?.valueOrNull
    }

    override fun cancel(cause: CancellationException?) {
        cancelInternal(cause ?: CancellationException("$classSimpleName was cancelled"))
    }

    // It needs to be internal to support deprecated cancel(Throwable?) API
    internal fun cancelInternal(cause: Throwable?): Boolean =
            close(cause)//.also {
//                onCancelIdempotent(it)
//            }

    private fun tryResumeReceive() {
        val empty = loGetAndSetNullEmpty()
        empty?.resume(Unit)
    }

    private fun tryResumeSend() {
        val full = loGetAndSetNullFull()
        full?.resume(Unit)
    }

    /**
     * Offer the value in buffer
     * Return true if there is room left in buffer, false otherwise
     * <p>
     * This implementation is correct for single producer use only.
     */
    private fun offerInternal(item: ValueOrClosed<E>, producerIndex: Long): Boolean {
        val mask = this.mask
        val offset = calcElementOffset(producerIndex, mask)
        if (!loCompareAndSetExpectedNullElement(offset, item)) return false
        if (null != item.closeCause) {
            tryResumeReceive()
            return true
        }
        // ordered store -> atomic and ordered for size()
        soLazyProducerIndex(producerIndex + 1)
        // handle empty case (= suspended Consumer)
        tryResumeReceive()
        return true
    }

    private suspend fun sendSuspend(): Unit = suspendAtomicCancellableCoroutineReusable { cont ->
        soFull(cont.intercepted())
        tryResumeReceive()
        COROUTINE_SUSPENDED
    }

    /**
     * Poll a value from buffer
     * Return the value if present, null otherwise
     * <p>
     * This implementation is correct for single consumer use only.
     */
    private fun pollInternal(consumerIndex: Long): ValueOrClosed<E>? {
        val offset = calcElementOffset(consumerIndex)
        val value = loGetAndSetNullElement(offset) ?: return null
        // Check if Closed
        if (value.isClosed) {
//            val cause = value.closeCause
//            if (cause != null) throw cause else throw ClosedReceiveChannelException(DEFAULT_CLOSE_MESSAGE)
            return value
        }
        // ordered store -> atomic and ordered for size()
        soLazyConsumerIndex(consumerIndex + 1)
        // we consumed the value from buffer, now check if Producer is full
        tryResumeSend()
        return value
    }

    suspend fun receiveInternal(consumerIndex: Long): ValueOrClosed<E> {
        // fast path -- try poll non-blocking
        val result = pollInternal(consumerIndex)
        if (null != result) return result
        // slow-path does suspend
        suspendReceive()
        return receiveInternal(consumerIndex) // re-call receive after suspension
    }

    private suspend fun suspendReceive(): Unit = suspendAtomicCancellableCoroutineReusable { cont ->
        soEmpty(cont.intercepted())
        tryResumeSend()
        COROUTINE_SUSPENDED
    }
}

abstract class AtomicReferenceArrayQueue<E : Any>(capacity: Int) {
    @JvmField protected val buffer = AtomicReferenceArray<E?>(capacity)
    @JvmField protected val mask: Int = capacity - 1

    init {
        check(capacity > 0) { "capacity must be positive" }
        check(capacity and mask == 0) { "capacity must be a power of 2" }
    }

    protected fun calcElementOffset(index: Long) = index.toInt() and mask

    protected fun loCompareAndSetExpectedNullElement(offset: Int, value: E?) = buffer.compareAndSet(offset, null, value)

    protected fun loGetAndSetNullElement(offset: Int) = buffer.getAndSet(offset, null)

    protected companion object {
        @JvmStatic
        protected fun calcElementOffset(index: Long, mask: Int) = index.toInt() and mask
    }
}

abstract class SpscAtomicArrayQueueL1Pad<E : Any>(capacity: Int) : AtomicReferenceArrayQueue<E>(capacity) {
    private val p01: Long = 0L;private val p02: Long = 0L;private val p03: Long = 0L;private val p04: Long = 0L;private val p05: Long = 0L;private val p06: Long = 0L;private val p07: Long = 0L

    private val p10: Long = 0L;private val p11: Long = 0L;private val p12: Long = 0L;private val p13: Long = 0L;private val p14: Long = 0L;private val p15: Long = 0L;private val p16: Long = 0L;private val p17: Long = 0L
}

abstract class SpscAtomicArrayQueueProducerIndexField<E : Any>(capacity: Int) : SpscAtomicArrayQueueL1Pad<E>(capacity) {
    private val P_INDEX_UPDATER = AtomicLongFieldUpdater.newUpdater<SpscAtomicArrayQueueProducerIndexField<*>>(SpscAtomicArrayQueueProducerIndexField::class.java, "producerIndex")
    @Volatile private var producerIndex: Long = 0L

    protected fun lvProducerIndex() = producerIndex
    protected fun soLazyProducerIndex(newValue: Long) { P_INDEX_UPDATER.lazySet(this, newValue) }
}

abstract class SpscAtomicArrayQueueL2Pad<E : Any>(capacity: Int) : SpscAtomicArrayQueueProducerIndexField<E>(capacity) {
    private val p01: Long = 0L;private val p02: Long = 0L;private val p03: Long = 0L;private val p04: Long = 0L;private val p05: Long = 0L;private val p06: Long = 0L;private val p07: Long = 0L

    private val p10: Long = 0L;private val p11: Long = 0L;private val p12: Long = 0L;private val p13: Long = 0L;private val p14: Long = 0L;private val p15: Long = 0L;private val p16: Long = 0L;private val p17: Long = 0L
}

abstract class SpscAtomicArrayQueueConsumerIndexField<E : Any>(capacity: Int) : SpscAtomicArrayQueueL2Pad<E>(capacity) {
    private val C_INDEX_UPDATER  = AtomicLongFieldUpdater.newUpdater<SpscAtomicArrayQueueConsumerIndexField<*>>(SpscAtomicArrayQueueConsumerIndexField::class.java, "consumerIndex")
    @Volatile private var consumerIndex: Long = 0L

    protected fun lvConsumerIndex() = consumerIndex
    protected fun soLazyConsumerIndex(newValue: Long) { C_INDEX_UPDATER.lazySet(this, newValue) }
}

abstract class SpscAtomicArrayQueueL3Pad<E : Any>(capacity: Int) : SpscAtomicArrayQueueConsumerIndexField<E>(capacity) {
    private val p01: Long = 0L;private val p02: Long = 0L;private val p03: Long = 0L;private val p04: Long = 0L;private val p05: Long = 0L;private val p06: Long = 0L;private val p07: Long = 0L

    private val p10: Long = 0L;private val p11: Long = 0L;private val p12: Long = 0L;private val p13: Long = 0L;private val p14: Long = 0L;private val p15: Long = 0L;private val p16: Long = 0L;private val p17: Long = 0L
}

abstract class AtomicReferenceEmptyField<E : Any>(capacity: Int) : SpscAtomicArrayQueueL3Pad<E>(capacity) {
    private val EMPTY_UPDATER = AtomicReferenceFieldUpdater.newUpdater<AtomicReferenceEmptyField<*>, Continuation<*>>(AtomicReferenceEmptyField::class.java,
            Continuation::class.java, "empty")
    @Volatile private var empty: Continuation<Unit>? = null

    @Suppress("UNCHECKED_CAST")
    internal fun loGetAndSetNullEmpty() = EMPTY_UPDATER.getAndSet(this, null) as Continuation<Unit>?
    internal fun soEmpty(value: Continuation<Unit>) { EMPTY_UPDATER.set(this, value) }
}

abstract class SpscAtomicArrayQueueL4Pad<E : Any>(capacity: Int) : AtomicReferenceEmptyField<E>(capacity) {
    private val p01: Long = 0L;private val p02: Long = 0L;private val p03: Long = 0L;private val p04: Long = 0L;private val p05: Long = 0L;private val p06: Long = 0L;private val p07: Long = 0L

    private val p10: Long = 0L;private val p11: Long = 0L;private val p12: Long = 0L;private val p13: Long = 0L;private val p14: Long = 0L;private val p15: Long = 0L;private val p16: Long = 0L;private val p17: Long = 0L
}

abstract class AtomicReferenceFullField<E : Any>(capacity: Int) : SpscAtomicArrayQueueL4Pad<E>(capacity) {
    private val FULL_UPDATER = AtomicReferenceFieldUpdater.newUpdater<AtomicReferenceFullField<*>, Continuation<*>>(AtomicReferenceFullField::class.java,
            Continuation::class.java, "full")
    @Volatile private var full: Continuation<Unit>? = null

    @Suppress("UNCHECKED_CAST")
    internal fun loGetAndSetNullFull(): Continuation<Unit>? = FULL_UPDATER.getAndSet(this, null) as Continuation<Unit>?
    internal fun soFull(value: Continuation<Unit>) { FULL_UPDATER.set(this, value) }
}

abstract class SpscAtomicArrayQueueL5Pad<E : Any>(capacity: Int) : AtomicReferenceFullField<E>(capacity) {
    private val p01: Long = 0L;private val p02: Long = 0L;private val p03: Long = 0L;private val p04: Long = 0L;private val p05: Long = 0L;private val p06: Long = 0L;private val p07: Long = 0L

    private val p10: Long = 0L;private val p11: Long = 0L;private val p12: Long = 0L;private val p13: Long = 0L;private val p14: Long = 0L;private val p15: Long = 0L;private val p16: Long = 0L;private val p17: Long = 0L
}
