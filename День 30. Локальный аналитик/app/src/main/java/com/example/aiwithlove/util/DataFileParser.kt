package com.example.aiwithlove.util

import android.content.Context
import android.net.Uri
import com.example.aiwithlove.data.model.DataFile
import com.example.aiwithlove.data.model.DataFileType

object DataFileParser {

    private const val MAX_ROWS = 200
    private const val MAX_CHARS = 8000

    fun parse(context: Context, uri: Uri): DataFile {
        val name = resolveFileName(context, uri)
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Cannot open file")
        return when {
            name.endsWith(".csv", ignoreCase = true) -> parseCsvText(name, raw)
            name.endsWith(".json", ignoreCase = true) -> parseJsonText(name, raw)
            else -> parseTextFile(name, raw)
        }
    }

    internal fun parseCsvText(name: String, raw: String): DataFile {
        val lines = raw.lines()
        val nonEmpty = lines.filter { it.isNotBlank() }
        val header = nonEmpty.firstOrNull() ?: ""
        val dataRows = nonEmpty.drop(1)
        val totalRows = dataRows.size
        val truncated = totalRows > MAX_ROWS
        val kept = dataRows.take(MAX_ROWS)
        val content = (listOf(header) + kept).joinToString("\n")
        return DataFile(
            name = name,
            type = DataFileType.CSV,
            rawContent = content,
            isTruncated = truncated,
            rowCount = kept.size,
            totalRowCount = totalRows
        )
    }

    private fun parseJsonText(name: String, raw: String): DataFile {
        val truncated = raw.length > MAX_CHARS
        val content = if (truncated) raw.take(MAX_CHARS) else raw
        val lineCount = content.lines().size
        return DataFile(
            name = name,
            type = DataFileType.JSON,
            rawContent = content,
            isTruncated = truncated,
            rowCount = lineCount,
            totalRowCount = raw.lines().size
        )
    }

    private fun parseTextFile(name: String, raw: String): DataFile {
        val truncated = raw.length > MAX_CHARS
        val content = if (truncated) raw.take(MAX_CHARS) else raw
        val lineCount = content.lines().size
        return DataFile(
            name = name,
            type = DataFileType.TEXT,
            rawContent = content,
            isTruncated = truncated,
            rowCount = lineCount,
            totalRowCount = raw.lines().size
        )
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = it.getString(idx)
                }
            }
        }
        return result ?: uri.lastPathSegment ?: "file"
    }
}
