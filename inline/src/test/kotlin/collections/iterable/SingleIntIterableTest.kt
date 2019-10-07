package collections

import kotlin.test.*

class SingleIntIterableTest {

    @Test
    fun verifySingleIntIterable() {
        val initialValue = 42
        val singleIntIterable = SingleIntIterable(initialValue)
        val values = mutableListOf<Int>()
        for (value in singleIntIterable) {
            values.add(value)
        }
        assertEquals(1, values.size)
        assertEquals(initialValue, values[0])
    }
}
