package collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MultiIntIterableTest {

    @Test
    fun verifyMultiIntIterable() {
        val initialValue = intArrayOf(1, 2)
        val singleIntIterable = MultiIntIterable(initialValue)
        val values = mutableListOf<Int>()
        for (value in singleIntIterable) {
            values.add(value)
        }
        assertIterableEquals(initialValue.asIterable(), values)
    }
}