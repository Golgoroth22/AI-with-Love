package com.example.aiwithlove.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ErrorLogRepository(private val context: Context) {
    private val file = File(context.filesDir, "generated_errors.csv")
    private val HEADER = "timestamp,level,service,message,code"

    val rowCount: Int
        get() = if (!file.exists()) 0
                else file.readLines().count { it.isNotBlank() } - 1

    fun ensureInitialized() {
        if (!file.exists()) file.writeText(HEADER + "\n")
    }

    fun append(level: String, service: String, message: String, code: String) {
        ensureInitialized()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file.appendText("$ts,$level,$service,\"$message\",$code\n")
    }

    fun clear() {
        file.writeText(HEADER + "\n")
    }

    fun readContent(): String = if (file.exists()) file.readText() else HEADER
}
