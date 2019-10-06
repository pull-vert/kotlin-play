package collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SingleIntIterableTest {

    @Test
    fun verifySingleIntIterable() {
        val initialValue = 42
        val singleIntIterable = SingleIntCollection(initialValue)
        val values = mutableListOf<Int>()
        for (value in singleIntIterable) {
            values.add(value)
        }
        assertIterableEquals(setOf(initialValue), values)
    }
}
