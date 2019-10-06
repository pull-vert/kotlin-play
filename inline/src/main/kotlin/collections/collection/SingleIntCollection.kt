package collections

inline class SingleIntCollection(private val value: Int) : Collection<Int> {
    override val size: Int
        get() = 1

    override fun contains(element: Int) = value == element

    override fun containsAll(elements: Collection<Int>) = elements.size == 1 && elements.contains(value)

    override fun isEmpty() = false

    override fun iterator() : Iterator<Int> = SingleIntIterator(value)
}
