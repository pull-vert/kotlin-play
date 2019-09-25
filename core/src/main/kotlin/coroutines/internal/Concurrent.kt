package coroutines.internal

import java.util.*

internal fun <E> identitySet(expectedSize: Int): MutableSet<E> = Collections.newSetFromMap(IdentityHashMap(expectedSize))
