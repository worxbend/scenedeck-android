package com.scenedeck.android.core.data

import com.scenedeck.android.core.common.coroutineResult

/** Best-effort reads may fail, but cancellation must always stop their caller. */
internal inline fun <T> requestResult(block: () -> T): Result<T> = coroutineResult(block)
