package com.example.aiwithlove.util

import kotlin.coroutines.cancellation.CancellationException

inline fun <R> runAndCatch(block: () -> R): Result<R> =
    try {
        Result.success((block()))
    } catch (error: Throwable) {
        if (error is CancellationException) {
            throw error
        }
        Result.failure(error)
    }
