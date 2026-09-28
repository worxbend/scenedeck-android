package com.scenedeck.android.core.common

/** Simple result wrapper used across repository and client boundaries. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val cause: Throwable) : AppResult<Nothing>
}
