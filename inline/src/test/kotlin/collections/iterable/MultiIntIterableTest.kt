package collections

import kotlin.test.*

class MultiIntIterableTest {

    @Test
    fun verifyMultiIntIterable() {
        val initialValue = intArrayOf(1, 2)
        val singleIntIterable = MultiIntIterable(initialValue)
        val values = mutableListOf<Int>()
        for (value in singleIntIterable) {
            values.add(value)
        }
        assertEquals(2, values.size)
        assertEquals(1, values[0])
        assertEquals(2, values[1])
    }
}
