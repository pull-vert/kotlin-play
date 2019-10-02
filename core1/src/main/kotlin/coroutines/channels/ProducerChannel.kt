package coroutines.channels

import coroutines.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class IterableChannel<out T>(iterable: Iterable<T>) : ReceiveChannel<T>{
    override val isClosedForReceive: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val isEmpty: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override suspend fun receiveOrClosed(): ValueOrClosed<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun poll(): T? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cancel(cause: CancellationException?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val iterator = iterable.iterator()

    override suspend fun receive(): T = suspendAtomicCancellableCoroutineReusable {
        it.intercepted().resume(iterator.next())
        COROUTINE_SUSPENDED
    }
}
