package collections

import java.util.*

inline class MultiIntIterable(private val values: IntArray) : Iterable<Int> {
    override fun iterator() = values.iterator()

    /**
     * TODO make a better implementation
     */
    override fun spliterator(): Spliterator<Int> {
        return Spliterators.spliteratorUnknownSize<Int>(iterator(), 0)
    }
}