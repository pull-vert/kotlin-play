package collections.iterable

import collections.SingleIntIterable
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

class SingleIntIterableTest {

    @Test
    fun verifySingleIntIterable() {
        val initialValue = 42
        val singleIntIterable = SingleIntIterable(initialValue)
        val values = mutableListOf<Int>()
        for (value in singleIntIterable) {
            values.add(value)
        }
        assertIterableEquals(setOf(initialValue), values)
    }
}