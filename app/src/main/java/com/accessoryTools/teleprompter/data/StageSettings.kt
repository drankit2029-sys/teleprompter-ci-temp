package com.accessoryTools.teleprompter.data

enum class StageFont {
    SANS,
    SERIF,
    MONO,
    SANS_LIGHT,
    SANS_MEDIUM,
    SANS_CONDENSED,
    SANS_SMALLCAPS,
    SERIF_MEDIUM,
    CURSIVE,
    CASUAL,
    CUSTOM,
}

enum class StageWeight {
    REGULAR,
    MEDIUM,
    BOLD,
}

enum class StageAlignment {
    CENTER,
    LEFT,
    RIGHT,
}

enum class StageTextColor {
    WARM_WHITE,
    WHITE,
    AMBER,
    MINT,
    INK,
    CUSTOM,
}

enum class StageBackground {
    BLACK,
    CHARCOAL,
    NAVY,
    FOREST,
    PAPER,
    CUSTOM,
}

val StageTextColor.argb: Int
    get() =
        when (this) {
            StageTextColor.WARM_WHITE -> 0xFFF7F4EC.toInt()
            StageTextColor.WHITE -> 0xFFFFFFFF.toInt()
            StageTextColor.AMBER -> 0xFFFFD166.toInt()
            StageTextColor.MINT -> 0xFFB8F2E6.toInt()
            StageTextColor.INK -> 0xFF101216.toInt()
            StageTextColor.CUSTOM -> 0xFFFFFFFF.toInt()
        }

val StageBackground.argb: Int
    get() =
        when (this) {
            StageBackground.BLACK -> 0xFF000000.toInt()
            StageBackground.CHARCOAL -> 0xFF14171A.toInt()
            StageBackground.NAVY -> 0xFF0B1626.toInt()
            StageBackground.FOREST -> 0xFF0C1A12.toInt()
            StageBackground.PAPER -> 0xFFF4EFE4.toInt()
            StageBackground.CUSTOM -> 0xFF000000.toInt()
        }

/**
 * Per-script prompter settings. Stored inside each `.tpscript` file so a script
 * keeps its look when it moves between devices through backup/restore.
 */
data class StageSettings(
    val speedWpm: Float = 140f,
    val fontSizeSp: Float = 46f,
    val focusFraction: Float = 0.3f,
    val edgeAlpha: Float = 0.28f,
    val marginDp: Float = 28f,
    val lineSpacing: Float = 1.34f,
    val font: StageFont = StageFont.SANS,
    val weight: StageWeight = StageWeight.MEDIUM,
    val alignment: StageAlignment = StageAlignment.CENTER,
    val textColor: StageTextColor = StageTextColor.WARM_WHITE,
    val background: StageBackground = StageBackground.BLACK,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val countdownSeconds: Int = 3,
    val loop: Boolean = false,
    val keepAwake: Boolean = true,
    val panelTranslucent: Boolean = false,
    val customTextArgb: Int = 0xFFF7F4EC.toInt(),
    val customBackgroundArgb: Int = 0xFF000000.toInt(),
    val customFontFile: String? = null,
) {
    fun sanitized(): StageSettings =
        copy(
            speedWpm = finiteOrDefault(speedWpm, MIN_WPM, MAX_WPM, 140f),
            fontSizeSp = finiteOrDefault(fontSizeSp, MIN_FONT_SP, MAX_FONT_SP, 46f),
            focusFraction = finiteOrDefault(focusFraction, MIN_FOCUS, MAX_FOCUS, 0.3f),
            edgeAlpha = finiteOrDefault(edgeAlpha, 0f, 1f, 0.28f),
            marginDp = finiteOrDefault(marginDp, MIN_MARGIN_DP, MAX_MARGIN_DP, 28f),
            lineSpacing = finiteOrDefault(lineSpacing, MIN_LINE_SPACING, MAX_LINE_SPACING, 1.34f),
            countdownSeconds = countdownSeconds.coerceIn(0, 5),
        )

    companion object {
        private fun finiteOrDefault(
            value: Float,
            min: Float,
            max: Float,
            default: Float,
        ): Float = if (value.isFinite()) value.coerceIn(min, max) else default

        const val MIN_WPM = 40f
        const val MAX_WPM = 260f
        const val MIN_FONT_SP = 18f
        const val MAX_FONT_SP = 100f
        const val MIN_FOCUS = 0.2f
        const val MAX_FOCUS = 0.8f
        const val MIN_MARGIN_DP = 8f
        const val MAX_MARGIN_DP = 80f
        const val MIN_LINE_SPACING = 1f
        const val MAX_LINE_SPACING = 2f
    }
}

private fun luminance(color: Int): Float {
    val r = (color shr 16 and 0xFF) / 255f
    val g = (color shr 8 and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Rough contrast heuristic for a text-color preset over a background preset.
 * Used to hide preset combinations that are effectively unreadable (e.g.
 * BLACK + INK or PAPER + WARM_WHITE/AMBER/MINT). Custom colours are always
 * allowed at the user's own risk.
 */
fun isReadableTextColorOn(
    fg: StageTextColor,
    bg: StageBackground,
): Boolean {
    if (fg == StageTextColor.CUSTOM) return true
    return kotlin.math.abs(luminance(fg.argb) - luminance(bg.argb)) >= 0.35f
}
