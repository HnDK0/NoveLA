package my.noveldokusha.features.reader.tools

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.FontFamily
import my.noveldokusha.reader.R

internal class FontsLoader(
    private val context: Context
) {
    companion object {
        val availableFonts = listOf(
            "inter",
            "lora",
            "merriweather",
            "source-sans-pro",
            "casual",
            "cursive",
            "monospace",
            "sans-serif",
            "sans-serif-black",
            "sans-serif-condensed",
            "sans-serif-condensed-light",
            "sans-serif-light",
            "sans-serif-medium",
            "sans-serif-smallcaps",
            "sans-serif-thin",
            "serif",
            "serif-monospace"
        )

    }

    private val typeFaceNORMALCache = mutableMapOf<String, Typeface>()
    private val typeFaceBOLDCache = mutableMapOf<String, Typeface>()
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    private fun customTypeface(name: String): Typeface? = when (name) {
        "inter" -> ResourcesCompat.getFont(context, R.font.inter_regular)
        "lora" -> ResourcesCompat.getFont(context, R.font.lora_regular)
        "merriweather" -> ResourcesCompat.getFont(context, R.font.merriweather_regular)
        "source-sans-pro" -> ResourcesCompat.getFont(context, R.font.source_sans_pro_regular)
        else -> null
    }

    fun getTypeFaceNORMAL(name: String) = typeFaceNORMALCache.getOrPut(name) {
        customTypeface(name) ?: Typeface.create(name, Typeface.NORMAL)
    }

    fun getTypeFaceBOLD(name: String) = typeFaceBOLDCache.getOrPut(name) {
        customTypeface(name)?.let { Typeface.create(it, Typeface.BOLD) }
            ?: Typeface.create(name, Typeface.BOLD)
    }

    fun getFontFamily(name: String) = fontFamilyCache.getOrPut(name) {
        FontFamily(getTypeFaceNORMAL(name))
    }
}
