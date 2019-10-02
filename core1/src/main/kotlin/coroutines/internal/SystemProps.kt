/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("SystemPropsKt")
@file:JvmMultifileClass

package coroutines.internal

/**
 * Gets the system property indicated by the specified [property name][propertyName],
 * or returns [defaultValue] if there is no property with that key.
 *
 * **Note: this function should be used in JVM tests only, other platforms use the default value.**
 */
internal fun systemProp(
        propertyName: String,
        defaultValue: Boolean
): Boolean = systemProp(propertyName)?.toBoolean() ?: defaultValue

// number of processors at startup for consistent prop initialization
internal val AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors()

internal fun systemProp(
    propertyName: String
): String? =
    try {
        System.getProperty(propertyName)
    } catch (e: SecurityException) {
        null
    }
