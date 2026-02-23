package com.example.aiwithlove.util

import android.util.Log

interface ILoggable {
    private fun tag(): String = javaClass.simpleName

    fun logD(message: String) {
        Log.d(tag(), message)
    }

    fun logE(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            Log.e(tag(), message)
        } else {
            Log.e(tag(), message, throwable)
        }
    }
}
