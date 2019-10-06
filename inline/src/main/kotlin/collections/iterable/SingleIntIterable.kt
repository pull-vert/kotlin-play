package collections

inline class SingleIntIterable(private val value: Int) : Iterable<Int> {
    override fun iterator() : Iterator<Int> = SingleIntIterator(value)
}
