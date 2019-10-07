package collections

import kotlin.test.*

class SingleIntCollectionTest {

    @Test
    fun verifySingleIntCollection() {
        val initialValue = 42
        val singleIntCollection = SingleIntCollection(initialValue)
        val values = mutableListOf<Int>()
        for (value in singleIntCollection) {
            values.add(value)
        }
        assertEquals(1, values.size)
        assertEquals(initialValue, values[0])
        assertEquals(1, singleIntCollection.size)
        assertFalse(singleIntCollection.isEmpty())
        assertFalse(singleIntCollection.contains(3))
        assertTrue(singleIntCollection.contains(initialValue))
        assertTrue(singleIntCollection.containsAll(setOf()))
        assertTrue(singleIntCollection.containsAll(setOf(initialValue)))
        assertTrue(singleIntCollection.containsAll(listOf(initialValue, initialValue)))
        assertFalse(singleIntCollection.containsAll(listOf(initialValue, 3)))
    }
}
