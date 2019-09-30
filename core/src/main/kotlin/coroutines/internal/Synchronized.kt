/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package coroutines.internal

/**
 * @suppress **This an internal API and should not be used from general code.**
 */
//@InternalCoroutinesApi
public typealias SynchronizedObject = Any

/**
 * @suppress **This an internal API and should not be used from general code.**
 */
//@InternalCoroutinesApi
public inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T =
    kotlin.synchronized(lock, block)