package com.accessoryTools.teleprompter.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordWrapTest {
    private val measure: (CharSequence) -> Float = { it.length * 10f }

    @Test
    fun wrapsAtWordBoundaryInsteadOfSplittingWord() {
        val wrapped = WordWrap.wrap("picture perfect", maxWidth = 100f, measure = measure)
        assertEquals("picture\nperfect", wrapped)
        assertFalse(wrapped.contains("pict\nure"))
    }

    @Test
    fun keepsWordsTogetherWhenTheyFit() {
        assertEquals(
            "picture perfect",
            WordWrap.wrap("picture perfect", maxWidth = 150f, measure = measure),
        )
    }

    @Test
    fun onlyBreaksAWordWhenItCannotFitAlone() {
        val wrapped = WordWrap.wrap("teleprompter", maxWidth = 40f, measure = measure)
        assertEquals("tele\nprom\npter", wrapped)
        assertTrue(wrapped.lines().all { it.length <= 4 })
    }

    @Test
    fun prefersHyphenBreaksInsideAnOverlongCompound() {
        assertEquals(
            "Picture-\nin-picture",
            WordWrap.wrap("Picture-in-picture", maxWidth = 100f, measure = measure),
        )
        assertEquals(
            "Picture-\nin-\npicture",
            WordWrap.wrap("Picture-in-picture", maxWidth = 80f, measure = measure),
        )
    }

    @Test
    fun longWordCanShareLineWithNextWordWhenSpaceRemains() {
        val wrapped = WordWrap.wrap("abcdefg hi", maxWidth = 100f, measure = measure)
        assertEquals("abcdefg hi", wrapped)
    }

    @Test
    fun preservesParagraphBreaksAndEmptyLines() {
        val wrapped =
            WordWrap.wrap(
                "first line\n\nsecond line",
                maxWidth = 200f,
                measure = measure,
            )
        assertEquals("first line\n\nsecond line", wrapped)
    }

    @Test
    fun exactFitDoesNotWrap() {
        assertEquals("abcde", WordWrap.wrap("abcde", maxWidth = 50f, measure = measure))
    }

    @Test
    fun collapsesIncidentalWhitespaceButKeepsWords() {
        assertEquals(
            "one two three",
            WordWrap.wrap("  one   two\tthree  ", maxWidth = 200f, measure = measure),
        )
    }

    @Test
    fun wrapsVeryNarrowWidthsAtWordBoundaries() {
        assertEquals(
            "ab\ncd",
            WordWrap.wrap("ab cd", maxWidth = 25f, measure = measure),
        )
    }

    @Test
    fun doesNotTearSurrogatePairs() {
        val wrapped = WordWrap.wrap("😀😀", maxWidth = 20f, measure = measure)
        val pieces = wrapped.lines()
        assertEquals(2, pieces.size)
        assertEquals("😀", pieces[0])
        assertEquals("😀", pieces[1])
        assertTrue(pieces.all { it.isNotEmpty() })
    }

    @Test
    fun returnsOriginalForInvalidWidth() {
        assertEquals("picture perfect", WordWrap.wrap("picture perfect", maxWidth = 0f, measure = measure))
        assertEquals("picture perfect", WordWrap.wrap("picture perfect", maxWidth = Float.NaN, measure = measure))
    }

    @Test
    fun everyWrappedLineFitsUnlessItIsOneOversizedWord() {
        val text =
            "The quick brown fox jumps over the lazy dog while the teleprompter " +
                "keeps every ordinary word intact across line boundaries."
        val wrapped = WordWrap.wrap(text, maxWidth = 210f, measure = measure)
        wrapped.lines().forEach { line ->
            if (!line.contains(' ')) {
                assertTrue("single word too wide: $line", measure(line) <= 210f || line.length <= 21)
            } else {
                assertTrue("line too wide: $line", measure(line) <= 210f)
            }
        }
    }
}
