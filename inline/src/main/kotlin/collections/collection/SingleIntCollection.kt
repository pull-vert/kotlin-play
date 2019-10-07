package collections

inline class SingleIntCollection(private val value: Int) : Collection<Int> {
    override val size: Int
        get() = 1

    override fun contains(element: Int) = value == element

    override fun containsAll(elements: Collection<Int>): Boolean {
        for (element in elements) {
            if (element != value) return false
        }
        return true
    }

    override fun isEmpty() = false

    override fun iterator() = SingleIntIterator(value)
}
