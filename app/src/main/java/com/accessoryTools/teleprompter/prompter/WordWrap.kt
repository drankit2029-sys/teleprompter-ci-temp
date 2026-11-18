package com.accessoryTools.teleprompter.prompter

/**
 * Explicit, word-safe line wrapping.
 *
 * Android/Compose line breakers may split an ordinary word when the remaining
 * space on a line is too small. The prompter must never do that: a word may
 * only be broken when that single word is wider than the whole line.
 *
 * Wrapping is done by inserting real newline characters, so the result renders
 * identically in Compose and in the overlay's [android.text.StaticLayout].
 * Text measurement is injected, which keeps the algorithm platform-free and
 * unit-testable.
 */
object WordWrap {
    /**
     * Wraps [text] so that no line exceeds [maxWidth] except a single word (or
     * code point) that cannot fit by itself. Existing newlines are preserved;
     * each paragraph is wrapped independently.
     */
    fun wrap(
        text: String,
        maxWidth: Float,
        measure: (CharSequence) -> Float,
    ): String {
        if (text.isEmpty() || maxWidth <= 0f || !maxWidth.isFinite()) {
            return text
        }
        val output = StringBuilder(text.length + text.length / 8)
        val paragraphs = text.split('\n')
        paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) {
                output.append('\n')
            }
            wrapParagraph(paragraph, maxWidth, measure, output)
        }
        return output.toString()
    }

    private fun wrapParagraph(
        paragraph: String,
        maxWidth: Float,
        measure: (CharSequence) -> Float,
        output: StringBuilder,
    ) {
        val words = paragraph.trim().split(WHITESPACE)
        if (words.isEmpty() || words.all { it.isEmpty() }) {
            return
        }

        var lineStart = output.length
        var lineHasWord = false

        fun startNewLine() {
            output.append('\n')
            lineStart = output.length
            lineHasWord = false
        }

        words.forEach { word ->
            if (word.isEmpty()) {
                return@forEach
            }
            val wordWidth = measure(word)
            if (wordWidth > maxWidth) {
                if (lineHasWord) {
                    startNewLine()
                }
                val pieces = splitLongWord(word, maxWidth, measure)
                pieces.forEachIndexed { pieceIndex, piece ->
                    if (pieceIndex > 0) {
                        startNewLine()
                    }
                    output.append(piece)
                    lineHasWord = true
                    lineStart = output.length - piece.length
                }
                return@forEach
            }

            if (!lineHasWord) {
                lineStart = output.length
                output.append(word)
                lineHasWord = true
                return@forEach
            }

            val candidate =
                buildString(output.length - lineStart + word.length + 1) {
                    append(output, lineStart, output.length)
                    append(' ')
                    append(word)
                }
            if (measure(candidate) <= maxWidth) {
                output.append(' ').append(word)
            } else {
                startNewLine()
                output.append(word)
                lineHasWord = true
            }
        }
    }

    /**
     * Splits a single word that is wider than [maxWidth]. Existing hyphens are
     * preferred break points; otherwise the split is chosen at code-point
     * boundaries so surrogate pairs are not torn apart. A single code point
     * wider than the line is still emitted as its own line.
     */
    internal fun splitLongWord(
        word: String,
        maxWidth: Float,
        measure: (CharSequence) -> Float,
    ): List<String> {
        if (word.isEmpty()) {
            return emptyList()
        }
        val pieces = mutableListOf<String>()
        var lineStart = 0
        while (lineStart < word.length) {
            var fitEnd = lineStart
            var hyphenBreak = -1
            var index = lineStart
            while (index < word.length) {
                val next = word.offsetByCodePoints(index, 1)
                if (measure(word.substring(lineStart, next)) > maxWidth) {
                    break
                }
                fitEnd = next
                if (index != lineStart && isBreakAfterHyphen(word[index])) {
                    hyphenBreak = next
                }
                index = next
            }
            if (fitEnd >= word.length) {
                pieces += word.substring(lineStart)
                break
            }
            val breakAt =
                when {
                    // Prefer breaking after a hyphen that still fits.
                    hyphenBreak > lineStart -> hyphenBreak

                    fitEnd > lineStart -> fitEnd

                    // A single code point is wider than the line: keep it whole.
                    else -> word.offsetByCodePoints(lineStart, 1)
                }
            pieces += word.substring(lineStart, breakAt)
            lineStart = breakAt
        }
        return pieces.ifEmpty { listOf(word) }
    }

    private fun isBreakAfterHyphen(character: Char): Boolean =
        character == '-' || character == '\u2010' || character == '\u2013' || character == '\u2014'

    private val WHITESPACE = Regex("\\s+")
}
