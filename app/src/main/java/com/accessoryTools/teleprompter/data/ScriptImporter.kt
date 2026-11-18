package com.accessoryTools.teleprompter.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ImportedDocument(
    val title: String,
    val content: String,
)

class ScriptImportException(
    message: String,
) : Exception(message)

object ScriptImporter {
    suspend fun import(
        context: Context,
        uri: Uri,
    ): ImportedDocument =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = displayName(context, uri)
            val mimeType = resolver.getType(uri)
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val bytes =
                resolver.openInputStream(uri)?.use { stream ->
                    stream.readLimited(MAX_IMPORT_BYTES)
                } ?: throw ScriptImportException("Could not open the selected file.")

            val rawText =
                when {
                    extension == "docx" || mimeType == DOCX_MIME -> {
                        extractWordDocument(bytes)
                    }

                    extension == "odt" || mimeType == ODT_MIME -> {
                        extractOpenDocument(bytes)
                    }

                    extension == "doc" -> {
                        throw ScriptImportException(
                            "Legacy .doc files are not supported. Save the file as .docx or .txt.",
                        )
                    }

                    extension == "pdf" || mimeType == "application/pdf" -> {
                        throw ScriptImportException(
                            "PDF import is not available yet. Export the script as .txt or .docx.",
                        )
                    }

                    extension == "html" || extension == "htm" || mimeType == "text/html" -> {
                        extractHtml(DocumentText.decode(bytes))
                    }

                    extension == "rtf" || mimeType == "application/rtf" -> {
                        DocumentText.extractRtf(bytes)
                    }

                    else -> {
                        DocumentText.decode(bytes)
                    }
                }

            val content = normalizeScriptText(rawText)
            if (content.isBlank()) {
                throw ScriptImportException("The selected file does not contain readable text.")
            }
            ImportedDocument(
                title = titleFromName(displayName, extension),
                content = content,
            )
        }

    fun fromPlainText(
        text: String,
        fallbackTitle: String,
    ): ImportedDocument {
        val content = normalizeScriptText(text)
        if (content.isBlank()) {
            throw ScriptImportException("There was no text to import.")
        }
        return ImportedDocument(
            title = titleFromContent(content, fallbackTitle),
            content = content,
        )
    }

    private fun displayName(
        context: Context,
        uri: Uri,
    ): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: fallback
    }

    private fun titleFromName(
        displayName: String,
        extension: String,
    ): String {
        val withoutExtension =
            if (extension.isNotEmpty() && displayName.endsWith(".$extension", ignoreCase = true)) {
                displayName.dropLast(extension.length + 1)
            } else {
                displayName
            }
        return withoutExtension.trim().take(120).ifBlank { "Imported script" }
    }

    private fun extractWordDocument(bytes: ByteArray): String {
        val xml = zipEntry(bytes, "word/document.xml")
        return extractXmlText(xml, paragraphTags = setOf("p"))
    }

    private fun extractOpenDocument(bytes: ByteArray): String {
        val xml = zipEntry(bytes, "content.xml")
        return extractXmlText(xml, paragraphTags = setOf("p", "h"))
    }

    private fun extractXmlText(
        xml: ByteArray,
        paragraphTags: Set<String>,
    ): String {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), null)
        val output = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "t" -> output.append(parser.nextText())
                        "tab" -> output.append('\t')
                        "br", "cr", "line-break" -> output.append('\n')
                        "noBreakHyphen" -> output.append('-')
                        "softHyphen" -> Unit
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (paragraphTags.contains(parser.name)) {
                        output.append('\n')
                    }
                }
            }
            event = parser.next()
        }
        return output.toString()
    }

    private fun extractHtml(html: String): String =
        Html
            .fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")

    private fun zipEntry(
        bytes: ByteArray,
        entryName: String,
    ): ByteArray {
        var entries = 0
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += 1
                if (entries > MAX_ZIP_ENTRIES) {
                    throw ScriptImportException("The document has too many internal parts.")
                }
                if (!entry.isDirectory && entry.name == entryName) {
                    val part = zip.readLimited(MAX_DOCUMENT_XML_BYTES)
                    total += part.size
                    if (total > MAX_DOCUMENT_XML_BYTES) {
                        throw ScriptImportException("The document is too large to import.")
                    }
                    return part
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw ScriptImportException("The document is missing its readable text part.")
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) {
                break
            }
            total += read
            if (total > limit) {
                throw ScriptImportException("The selected file is too large to import.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
    private const val MAX_DOCUMENT_XML_BYTES = 20 * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 5_000
    private const val DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val ODT_MIME = "application/vnd.oasis.opendocument.text"
}
