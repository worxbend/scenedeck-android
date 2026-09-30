package com.scenedeck.android.core.common

import kotlinx.coroutines.CancellationException

/** Captures recoverable operation failures without breaking structured cancellation. */
@Suppress(
    "TooGenericExceptionCaught"
) // Result boundary: cancellation propagates; operational failures become values.
inline fun <T> coroutineResult(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
