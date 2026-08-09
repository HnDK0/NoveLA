package my.noveldokusha.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetLanguageResolverTest {

    @Test
    fun explicitTargetWins() {
        assertEquals("de", TargetLanguageResolver.resolve("de", "fr"))
    }

    @Test
    fun emptyPrefFallsBackToDeviceLanguage() {
        assertEquals("fr", TargetLanguageResolver.resolve("", "fr"))
        assertEquals("fr", TargetLanguageResolver.resolve(null, "fr"))
    }

    @Test
    fun unsupportedDeviceLanguageFallsBackToEn() {
        assertEquals("en", TargetLanguageResolver.resolve(null, "zh"))
    }

    @Test
    fun autoPrefIsIgnored() {
        assertEquals("ko", TargetLanguageResolver.resolve("auto", "ko"))
    }
}
