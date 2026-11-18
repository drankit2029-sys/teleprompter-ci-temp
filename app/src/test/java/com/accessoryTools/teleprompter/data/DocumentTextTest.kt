package com.accessoryTools.teleprompter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTextTest {
    @Test
    fun decodePrefersUtf8WithoutBom() {
        assertEquals("héllo", DocumentText.decode("héllo".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decodeHonoursUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Hello".toByteArray()
        assertEquals("Hello", DocumentText.decode(bytes))
    }

    @Test
    fun decodeHonoursUtf16Boms() {
        val little =
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hi".toByteArray(Charsets.UTF_16LE)
        val big =
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "Hi".toByteArray(Charsets.UTF_16BE)
        assertEquals("Hi", DocumentText.decode(little))
        assertEquals("Hi", DocumentText.decode(big))
    }

    @Test
    fun decodeFallsBackToWindows1252() {
        val bytes = byteArrayOf(0x93.toByte(), 'H'.code.toByte(), 'i'.code.toByte(), 0x94.toByte())
        assertEquals("\u201CHi\u201D", DocumentText.decode(bytes))
    }

    @Test
    fun rtfKeepsParagraphsTabsAndLiteralEscapes() {
        val rtf = """{\rtf1\ansi{\fonttbl{\f0 Arial;}}\f0 Hello\par World\tab End}"""
        assertEquals("Hello\nWorld\tEnd", DocumentText.extractRtf(rtf.toByteArray()))
    }

    @Test
    fun rtfDecodesUnicodeAndSkipsFallbackCharacter() {
        val rtf = """{\rtf1\ansi Caf\u233?\par Next}"""
        assertEquals("Café\nNext", DocumentText.extractRtf(rtf.toByteArray()))
    }

    @Test
    fun rtfRespectsUnicodeFallbackCount() {
        val rtf = """{\rtf1\ansi \uc0\u8212 X}"""
        assertEquals("\u2014X", DocumentText.extractRtf(rtf.toByteArray()))
    }

    @Test
    fun rtfDecodesWindows1252HexEscapes() {
        val rtf = """{\rtf1\ansi \'93quoted\'94}"""
        assertEquals("\u201Cquoted\u201D", DocumentText.extractRtf(rtf.toByteArray()))
    }

    @Test
    fun rtfSkipsMetadataAndFormattingDestinations() {
        val rtf = """{\rtf1{\info{\title Hidden}}{\colortbl;\red255\green0\blue0;}Visible\par}"""
        assertEquals("Visible\n", DocumentText.extractRtf(rtf.toByteArray()))
    }

    @Test
    fun rtfHandlesEscapedBracesAndBackslashes() {
        val rtf = """{\rtf1 Literal \{brace\} and \\slash}"""
        assertEquals("Literal {brace} and \\slash", DocumentText.extractRtf(rtf.toByteArray()))
    }
}
