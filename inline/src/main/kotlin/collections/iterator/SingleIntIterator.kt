package collections

class SingleIntIterator(private val value: Int) : Iterator<Int> {
    private var done = false

    override fun hasNext() =
        if (!done) {
            done = true
            true
        } else {
            false
        }

    override fun next() = value
}