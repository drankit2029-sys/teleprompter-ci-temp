package com.accessoryTools.teleprompter.data

enum class StageFont {
    SANS,
    SERIF,
    MONO,
}

enum class StageWeight {
    REGULAR,
    MEDIUM,
    BOLD,
}

enum class StageAlignment {
    CENTER,
    LEFT,
}

enum class StageTextColor {
    WARM_WHITE,
    WHITE,
    AMBER,
    MINT,
    INK,
}

enum class StageBackground {
    BLACK,
    CHARCOAL,
    NAVY,
    FOREST,
    PAPER,
}

val StageTextColor.argb: Int
    get() =
        when (this) {
            StageTextColor.WARM_WHITE -> 0xFFF7F4EC.toInt()
            StageTextColor.WHITE -> 0xFFFFFFFF.toInt()
            StageTextColor.AMBER -> 0xFFFFD166.toInt()
            StageTextColor.MINT -> 0xFFB8F2E6.toInt()
            StageTextColor.INK -> 0xFF101216.toInt()
        }

val StageBackground.argb: Int
    get() =
        when (this) {
            StageBackground.BLACK -> 0xFF000000.toInt()
            StageBackground.CHARCOAL -> 0xFF14171A.toInt()
            StageBackground.NAVY -> 0xFF0B1626.toInt()
            StageBackground.FOREST -> 0xFF0C1A12.toInt()
            StageBackground.PAPER -> 0xFFF4EFE4.toInt()
        }

/**
 * Per-script prompter settings. Stored inside each `.tpscript` file so a script
 * keeps its look when it moves between devices through backup/restore.
 */
data class StageSettings(
    val speedWpm: Float = 140f,
    val fontSizeSp: Float = 46f,
    val focusFraction: Float = 0.5f,
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
) {
    fun sanitized(): StageSettings =
        copy(
            speedWpm = speedWpm.coerceIn(MIN_WPM, MAX_WPM),
            fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SP, MAX_FONT_SP),
            focusFraction = focusFraction.coerceIn(MIN_FOCUS, MAX_FOCUS),
            edgeAlpha = edgeAlpha.coerceIn(0f, 1f),
            marginDp = marginDp.coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP),
            lineSpacing = lineSpacing.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
            countdownSeconds = countdownSeconds.coerceIn(0, 5),
        )

    companion object {
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
