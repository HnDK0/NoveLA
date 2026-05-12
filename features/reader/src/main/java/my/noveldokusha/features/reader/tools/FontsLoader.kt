package my.noveldokusha.features.reader.tools

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import my.noveldokusha.reader.R

internal class FontsLoader {
    companion object {
        val availableFonts = listOf(
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
            "serif-monospace",
            // Custom fonts
            "inter",
            "lora",
            "merriweather",
            "source-sans-pro"
        )
    }

    private val typeFaceNORMALCache = mutableMapOf<String, Typeface>()
    private val typeFaceBOLDCache = mutableMapOf<String, Typeface>()
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    fun getTypeFaceNORMAL(context: Context, name: String): Typeface = typeFaceNORMALCache.getOrPut(name) {
        getCustomTypeface(context, name) ?: Typeface.create(name, Typeface.NORMAL)
    }

    fun getTypeFaceBOLD(context: Context, name: String): Typeface = typeFaceBOLDCache.getOrPut(name) {
        val base = getCustomTypeface(context, name)
        if (base != null) Typeface.create(base, Typeface.BOLD)
        else Typeface.create(name, Typeface.BOLD)
    }

    fun getFontFamily(context: Context, name: String) = fontFamilyCache.getOrPut(name) {
        FontFamily(getTypeFaceNORMAL(context, name))
    }

    private fun getCustomTypeface(context: Context, name: String): Typeface? {
        val resId = when (name.lowercase()) {
            "inter" -> R.font.inter_regular
            "lora" -> R.font.lora_regular
            "merriweather" -> R.font.merriweather_regular
            "source-sans-pro" -> R.font.source_sans_pro_regular
            else -> null
        }
        return resId?.let { ResourcesCompat.getFont(context, it) }
    }
}