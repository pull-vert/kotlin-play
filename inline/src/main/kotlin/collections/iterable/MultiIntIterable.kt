package collections

inline class MultiIntIterable(private val value: IntArray) : Iterable<Int> {
    override fun iterator(): Iterator<Int> = value.iterator()
}