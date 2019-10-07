package collections

inline class MultiIntIterable(private val value: IntArray) : Iterable<Int> {
    override fun iterator() = value.iterator()
}