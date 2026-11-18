package com.accessoryTools.teleprompter.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Pure text decoding/conversion helpers used by [ScriptImporter]. They are
 * deliberately free of Android types so they can run in JVM unit tests.
 */
object DocumentText {
    private val windows1252 =
        runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
    private val ignoredRtfDestinations =
        setOf(
            "fonttbl",
            "colortbl",
            "stylesheet",
            "info",
            "pict",
            "object",
            "header",
            "footer",
            "fldinst",
            "listtable",
            "listoverridetable",
            "generator",
            "themedata",
            "colorschememapping",
            "datastore",
            "latentstyles",
            "pgptbl",
            "shpinst",
            "nonshppict",
            "xmlnstbl",
            "rsidtbl",
        )

    fun decode(bytes: ByteArray): String {
        val charset =
            when {
                bytes.startsWith(UTF8_BOM) -> {
                    Charsets.UTF_8
                }

                bytes.startsWith(UTF16_LE_BOM) -> {
                    Charsets.UTF_16LE
                }

                bytes.startsWith(UTF16_BE_BOM) -> {
                    Charsets.UTF_16BE
                }

                isStrictUtf8(bytes) -> {
                    Charsets.UTF_8
                }

                else -> {
                    windows1252
                }
            }
        return bytes.toString(charset).removePrefix("\uFEFF")
    }

    /**
     * Extracts visible text from an RTF byte stream. Formatting is discarded;
     * paragraphs, line breaks, tabs, Unicode escapes, and Windows-1252 hex
     * escapes are preserved. Destinations such as font tables and document
     * metadata are skipped.
     */
    fun extractRtf(bytes: ByteArray): String {
        val source = bytes.toString(Charsets.ISO_8859_1)
        val output = StringBuilder()
        val pendingBytes = ByteArrayOutputStream()
        var index = 0
        var groupDepth = 0
        var skipDepth = -1
        var unicodeSkip = 1
        var fallbackSkip = 0

        fun flushBytes() {
            if (pendingBytes.size() > 0) {
                output.append(pendingBytes.toByteArray().toString(windows1252))
                pendingBytes.reset()
            }
        }

        fun appendControlText(text: String) {
            flushBytes()
            output.append(text)
        }

        while (index < source.length) {
            if (fallbackSkip > 0) {
                if (source[index] == '\\' && index + 1 < source.length) {
                    val escaped = source[index + 1]
                    index +=
                        when {
                            escaped == '\'' -> 4
                            escaped == '\\' || escaped == '{' || escaped == '}' -> 2
                            else -> 2
                        }
                } else {
                    index += 1
                }
                fallbackSkip -= 1
                continue
            }

            val character = source[index]
            when {
                character == '{' -> {
                    groupDepth += 1
                    index += 1
                }

                character == '}' -> {
                    flushBytes()
                    if (skipDepth >= 0 && groupDepth <= skipDepth) {
                        skipDepth = -1
                    }
                    groupDepth = (groupDepth - 1).coerceAtLeast(0)
                    index += 1
                }

                character == '\\' -> {
                    if (index + 1 >= source.length) {
                        index += 1
                        continue
                    }
                    val next = source[index + 1]
                    when {
                        next == '\'' -> {
                            if (index + 3 < source.length) {
                                val value = source.substring(index + 2, index + 4).toIntOrNull(16)
                                if (skipDepth < 0 && value != null) {
                                    pendingBytes.write(value)
                                }
                                index += 4
                            } else {
                                index += 2
                            }
                        }

                        next == '\\' || next == '{' || next == '}' -> {
                            if (skipDepth < 0) {
                                pendingBytes.write(next.code)
                            }
                            index += 2
                        }

                        next == '*' -> {
                            if (skipDepth < 0) {
                                skipDepth = groupDepth
                            }
                            index += 2
                        }

                        next == '\n' || next == '\r' -> {
                            if (skipDepth < 0) {
                                appendControlText("\n")
                            }
                            index += 2
                        }

                        next.isLetter() -> {
                            var wordEnd = index + 1
                            while (wordEnd < source.length && source[wordEnd].isLetter()) {
                                wordEnd += 1
                            }
                            val word = source.substring(index + 1, wordEnd)
                            var parameterEnd = wordEnd
                            if (parameterEnd < source.length && source[parameterEnd] == '-') {
                                parameterEnd += 1
                            }
                            while (parameterEnd < source.length && source[parameterEnd].isDigit()) {
                                parameterEnd += 1
                            }
                            val parameter =
                                source
                                    .substring(wordEnd, parameterEnd)
                                    .takeIf { it.isNotEmpty() && it != "-" }
                                    ?.toIntOrNull()
                            if (word in ignoredRtfDestinations && skipDepth < 0) {
                                skipDepth = groupDepth
                            }
                            if (skipDepth < 0) {
                                when (word) {
                                    "par", "line" -> {
                                        appendControlText("\n")
                                    }

                                    "tab" -> {
                                        appendControlText("\t")
                                    }

                                    "emdash" -> {
                                        appendControlText("\u2014")
                                    }

                                    "endash" -> {
                                        appendControlText("\u2013")
                                    }

                                    "bullet" -> {
                                        appendControlText("\u2022")
                                    }

                                    "lquote" -> {
                                        appendControlText("\u2018")
                                    }

                                    "rquote" -> {
                                        appendControlText("\u2019")
                                    }

                                    "ldblquote" -> {
                                        appendControlText("\u201C")
                                    }

                                    "rdblquote" -> {
                                        appendControlText("\u201D")
                                    }

                                    "u" -> {
                                        if (parameter != null) {
                                            appendControlText((parameter and 0xFFFF).toChar().toString())
                                            fallbackSkip = unicodeSkip.coerceAtLeast(0)
                                        }
                                    }

                                    "uc" -> {
                                        if (parameter != null) {
                                            unicodeSkip = parameter.coerceAtLeast(0)
                                        }
                                    }
                                }
                            }
                            index = parameterEnd
                            if (index < source.length && source[index] == ' ') {
                                index += 1
                            }
                        }

                        else -> {
                            index += 2
                        }
                    }
                }

                character == '\r' || character == '\n' -> {
                    index += 1
                }

                else -> {
                    if (skipDepth < 0) {
                        pendingBytes.write(character.code and 0xFF)
                    }
                    index += 1
                }
            }
        }
        flushBytes()
        return output.toString()
    }

    private fun isStrictUtf8(bytes: ByteArray): Boolean =
        runCatching {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
        }.isSuccess

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) {
            return false
        }
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}
