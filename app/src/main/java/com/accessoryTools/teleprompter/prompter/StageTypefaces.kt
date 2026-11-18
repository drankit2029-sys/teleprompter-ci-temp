package com.accessoryTools.teleprompter.prompter

import android.graphics.Typeface
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageWeight

/**
 * One source of truth for the platform typeface used by the Compose stage and
 * the overlay renderer. Wrapping measurements must use the same typeface as
 * the final drawing pass.
 */
object StageTypefaces {
    @Volatile
    private var appContext: android.content.Context? = null

    /** Wire the application context so CUSTOM fonts can be resolved from files. */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    @Volatile
    private var customFontCache: android.graphics.Typeface? = null

    @Volatile
    private var customFontKey: String? = null

    fun typefaceFor(
        font: StageFont,
        weight: StageWeight,
        customFontFile: String? = null,
    ): Typeface {
        if (font == StageFont.CUSTOM) {
            val name = customFontFile ?: return Typeface.create("sans-serif", Typeface.NORMAL)
            val cached = customFontCache
            if (cached != null && customFontKey == key(name, weight)) {
                return cached
            }
            val file = fontFile(name)
            if (file != null && file.isFile) {
                val created = Typeface.createFromFile(file)
                val style = if (weight == StageWeight.BOLD) Typeface.BOLD else Typeface.NORMAL
                customFontCache = created
                customFontKey = key(name, weight)
                return created
            }
            return Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val family =
            when (font) {
                StageFont.SANS -> if (weight == StageWeight.MEDIUM) "sans-serif-medium" else "sans-serif"
                StageFont.SERIF -> "serif"
                StageFont.MONO -> "monospace"
                StageFont.SANS_LIGHT -> "sans-serif-light"
                StageFont.SANS_MEDIUM -> "sans-serif-medium"
                StageFont.SANS_CONDENSED -> "sans-serif-condensed"
                StageFont.SANS_SMALLCAPS -> "sans-serif-smallcaps"
                StageFont.SERIF_MEDIUM -> "serif-medium"
                StageFont.CURSIVE -> "cursive"
                StageFont.CASUAL -> "casual"
                StageFont.CUSTOM -> "sans-serif"
            }
        val style =
            when (weight) {
                StageWeight.BOLD -> Typeface.BOLD

                StageWeight.MEDIUM,
                StageWeight.REGULAR,
                -> Typeface.NORMAL
            }
        return Typeface.create(family, style)
    }

    /** Absolute path of a custom font file, or null if the name is invalid/unsafe. */
    fun fontFile(name: String): java.io.File? {
        val ctx = appContext ?: return null
        val safe = name.replace('\\', '/').substringAfterLast('/')
        if (safe.isBlank()) {
            return null
        }
        return java.io.File(ctx.filesDir, "fonts/" + safe)
    }

    /** Display name for a custom font (strip extension). */
    fun displayName(fileName: String): String =
        fileName
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .trim()
            .ifBlank { fileName }

    /** Copy a picked font stream into app-private storage. Returns the stored file name. */
    fun importFont(
        source: java.io.InputStream,
        displayNameHint: String,
    ): String? {
        val ctx = appContext ?: return null
        val fontsDir = java.io.File(ctx.filesDir, "fonts").apply { mkdirs() }
        val name = sanitizeFontName(displayNameHint)
        val target = java.io.File(fontsDir, name)
        source.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        invalidateCache()
        return target.name
    }

    fun invalidateCache() {
        customFontCache = null
        customFontKey = null
    }

    /** Remove an installed custom font. Returns true when deleted. */
    fun deleteFont(fileName: String): Boolean {
        val deleted = fontFile(fileName)?.delete() == true
        invalidateCache()
        return deleted
    }

    private fun sanitizeFontName(name: String): String {
        val cleaned =
            name
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "custom-font" }
        return if (cleaned.endsWith(".ttf", true) || cleaned.endsWith(".otf", true)) cleaned else "$cleaned.ttf"
    }

    private fun key(
        name: String,
        weight: StageWeight,
    ): String = "$name|$weight"
}
