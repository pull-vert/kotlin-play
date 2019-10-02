package coroutines.channels

import coroutines.*
import java.util.concurrent.CancellationException

class IterableChannel<out T>(iterable: Iterable<T>) : ReceiveChannel<T> {
    private val iterator = iterable.iterator()

    override val isClosedForReceive get() = !iterator.hasNext()
    override val isEmpty get() = !iterator.hasNext()

    var cancelled: CancellationException? = null

    override suspend fun receiveOrClosed(): ValueOrClosed<T> {
        val cancelled = this.cancelled
        return when {
            cancelled != null -> throw cancelled
            iterator.hasNext() -> ValueOrClosed.value(iterator.next())
            else -> ValueOrClosed.closed(null)
        }
    }

    override fun poll() : T? {
            val cancelled = this.cancelled
            return when {
                cancelled != null -> throw cancelled
                iterator.hasNext() -> iterator.next()
                else -> null
            }
        }

    override fun cancel(cause: CancellationException?) {
        cancelled = cause ?: CancellationException("$classSimpleName was cancelled")
    }

    override suspend fun receive() =
            poll() ?: throw ClosedReceiveChannelException(coroutines.channels.DEFAULT_CLOSE_MESSAGE)
}
