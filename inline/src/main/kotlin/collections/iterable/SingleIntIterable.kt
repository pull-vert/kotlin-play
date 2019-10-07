package collections

inline class SingleIntIterable(private val value: Int) : Iterable<Int> {
    override fun iterator() = SingleIntIterator(value)
}
