package com.accessoryTools.teleprompter.data

import kotlin.math.roundToInt

data class Script(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean = false,
    val stage: StageSettings? = null,
    val lastPosition: Float = 0f,
    val lastOpenedAt: Long = 0L,
    val words: Int = wordCount(content),
    val characters: Int = content.length,
)

fun wordCount(text: String): Int {
    val trimmed = text.trim()
    return if (trimmed.isEmpty()) {
        0
    } else {
        trimmed.split(WhitespaceRegex).size
    }
}

fun readingSeconds(
    words: Int,
    wpm: Float,
): Int = ((words / wpm.coerceAtLeast(1f)) * 60f).roundToInt().coerceAtLeast(1)

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return when {
        minutes >= 60 -> {
            val hours = minutes / 60
            val rest = minutes % 60
            "${hours}h ${rest}m"
        }

        minutes > 0 -> {
            "${minutes}m ${remainder}s"
        }

        else -> {
            "${remainder}s"
        }
    }
}

fun titleFromContent(
    text: String,
    fallback: String,
): String {
    val firstLine =
        text
            .lineSequence()
            .map { it.trim().trimStart('#').trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    return firstLine.take(60).ifBlank { fallback }
}

fun normalizeScriptText(text: String): String =
    text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .removePrefix("\uFEFF")
        .trim()

private val WhitespaceRegex = Regex("\\s+")
