package collections

inline class MultiIntICollection(private val value: IntArray) : Collection<Int> {
    override val size: Int
        get() = value.size

    override fun contains(element: Int) = value.contains(element)

    override fun containsAll(elements: Collection<Int>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun iterator(): Iterator<Int> = value.iterator()
}