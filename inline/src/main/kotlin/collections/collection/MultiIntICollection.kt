package collections

inline class MultiIntICollection(private val value: IntArray) : Collection<Int> {
    override val size: Int
        get() = value.size

    override fun contains(element: Int) = value.contains(element)

    override fun containsAll(elements: Collection<Int>): Boolean {
        for (element in elements) {
            if (!value.contains(element)) return false
        }
        return true
    }

    override fun isEmpty() = false

    override fun iterator() = value.iterator()
}