package com.scenedeck.android.core.obs

internal inline fun <reified T> assertIsInstance(value: Any?): T {
    if (value !is T) {
        val actual = value?.let { it::class.qualifiedName }
        throw AssertionError("Expected ${T::class.qualifiedName} but was $actual: $value")
    }
    return value
}

internal inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
    try {
        block()
    } catch (t: Throwable) {
        if (t is T) return t
        throw AssertionError("Expected ${T::class.qualifiedName} but caught $t", t)
    }
    throw AssertionError("Expected ${T::class.qualifiedName} but nothing was thrown")
}
