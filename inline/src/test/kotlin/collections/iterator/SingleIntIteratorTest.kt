package collections

import kotlin.test.*

class SingleIntIteratorTest {

    @Test
    fun verifySingleIntIterator() {
        val initialValue = 42
        val singleIntIterator = SingleIntIterator(initialValue)
        assertTrue(singleIntIterator.hasNext())
        assertEquals(initialValue, singleIntIterator.next())
        assertFalse(singleIntIterator.hasNext())
    }
}
