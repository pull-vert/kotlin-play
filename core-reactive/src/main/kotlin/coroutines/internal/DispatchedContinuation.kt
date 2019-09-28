package coroutines.internal

import coroutines.CoroutineDispatcher
import kotlin.coroutines.Continuation

internal class DispatchedContinuation<T>(
        @JvmField val dispatcher: CoroutineDispatcher,
        @JvmField val continuation: Continuation<T>
) : DispatchedTask<T>(), Continuation<T> by continuation {

    @JvmField // pre-cached value to avoid ctx.fold on every resumption
    internal val countOrElement = threadContextElements(context)

    override val delegate: Continuation<T>
        get() = this

    override fun takeState(): DispatchedState<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
