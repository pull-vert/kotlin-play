package collections

import kotlin.jvm.internal.iterator

inline class MultiIntIterable(private val value: IntArray) : Iterable<Int> {
    override fun iterator() = iterator(value)
}