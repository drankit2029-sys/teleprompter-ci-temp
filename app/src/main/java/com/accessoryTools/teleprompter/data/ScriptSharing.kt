package com.accessoryTools.teleprompter.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val SHARE_STREAM_THRESHOLD_BYTES = 200_000

object ScriptSharing {
    suspend fun shareText(
        context: Context,
        script: Script,
    ) {
        val byteSize = script.content.toByteArray(Charsets.UTF_8).size
        if (byteSize > SHARE_STREAM_THRESHOLD_BYTES) {
            // Binder transactions cap at ~1 MB of payload; keep large scripts out of EXTRA_TEXT.
            shareFile(context, script, markdown = false)
            return
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, script.title)
                putExtra(Intent.EXTRA_TEXT, script.content)
            }
        context.startActivity(
            Intent.createChooser(intent, "Share script").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    suspend fun export(
        context: Context,
        uri: Uri,
        title: String,
        content: String,
        markdown: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            val text = if (markdown) markdownText(title, content) else content
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(text)
            } ?: error("Could not open the selected file for writing.")
        }
    }

    fun markdownText(
        title: String,
        content: String,
    ): String = if (content.trimStart().startsWith("# ")) content else "# $title\n\n$content"

    private suspend fun shareFile(
        context: Context,
        script: Script,
        markdown: Boolean,
    ) {
        val extension = if (markdown) "md" else "txt"
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "${safeFileName(script.title)}.$extension")
        withContext(Dispatchers.IO) {
            file.writeText(if (markdown) markdownText(script.title, script.content) else script.content)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = if (markdown) "text/markdown" else "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, script.title)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(script.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(intent, "Share script").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun safeFileName(title: String): String =
        title
            .trim()
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .replace(Regex("\\s+"), " ")
            .take(60)
            .ifBlank { "script" }

    /** Removes stale share/export copies from the cache on app start. */
    fun clearExports(context: Context) {
        runCatching {
            File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
        }
    }
}
