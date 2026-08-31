package my.noveldokusha.features.reader.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontsLoaderTest {

    @Test
    fun parsePrefValue_importedValue_returnsFileName() {
        assertEquals("NotoSansCJK.ttf", FontsLoader.parsePrefValue("font_file:NotoSansCJK.ttf"))
    }

    @Test
    fun parsePrefValue_systemValue_returnsNull() {
        assertNull(FontsLoader.parsePrefValue("serif"))
    }

    @Test
    fun sanitizeFileName_evilPath_isSafe() {
        val result = FontsLoader.sanitizeFileName("../../evil/My Font!.ttf")
        assertEquals(".evilMyFont.ttf", result)
        assertFalse(result.contains('/'))
        assertFalse(result.contains(".."))
    }

    @Test
    fun sanitizeFileName_emptyResult_fallsBackToNonEmpty() {
        val result = FontsLoader.sanitizeFileName("!!!")
        assertTrue(result.isNotEmpty())
        assertTrue(result.startsWith("font_"))
    }

    @Test
    fun displayName_importedCollidingWithSystem_appendsImportedSuffix() {
        assertEquals("serif (imported)", FontsLoader.displayName("font_file:serif.ttf", setOf("serif")))
    }

    @Test
    fun displayName_importedNoCollision_stripsPrefixAndExtension() {
        assertEquals("NotoSansCJK", FontsLoader.displayName("font_file:NotoSansCJK.ttf", setOf("serif")))
    }

    @Test
    fun displayName_systemValue_returnsAsIs() {
        assertEquals("serif", FontsLoader.displayName("serif", setOf("serif")))
    }

    @Test
    fun isImported_importedValue_true() {
        assertTrue(FontsLoader.isImported("font_file:NotoSansCJK.ttf"))
    }

    @Test
    fun isImported_systemValue_false() {
        assertFalse(FontsLoader.isImported("serif"))
    }

    @Test
    fun isValidFontExtension_ttf_true() {
        assertTrue(FontsLoader.isValidFontExtension("NotoSansCJK.ttf"))
    }

    @Test
    fun isValidFontExtension_otf_true() {
        assertTrue(FontsLoader.isValidFontExtension("NotoSansCJK.otf"))
    }

    @Test
    fun isValidFontExtension_uppercase_true() {
        assertTrue(FontsLoader.isValidFontExtension("NotoSansCJK.TTF"))
    }

    @Test
    fun isValidFontExtension_otherExtension_false() {
        assertFalse(FontsLoader.isValidFontExtension("NotoSansCJK.txt"))
    }

    @Test
    fun isValidFontExtension_noExtension_false() {
        assertFalse(FontsLoader.isValidFontExtension("NotoSansCJK"))
    }

    // --- T7: proof of the imported-file resolution decision path ---
    // Модуль не использует Robolectric (см. build.gradle.kts: только JUnit + mockito +
    // coroutines-test), поэтому реальный Android Typeface из файла в unit-тесте недоступен.
    // Этот тест фиксирует чистую логику, которая решает, пойти ли по пути импортированного
    // файла (getTypeFaceNORMAL/BOLD -> resolveFile -> Typeface.createFromFile), и как именно
    // формируется имя файла на диске. Это pure-JVM-доказательство, а не файловое.

    @Test
    fun importedPath_planInput_parsesToFileName() {
        // План: "font_file:NotoSansCJK.ttf" -> "NotoSansCJK.ttf"
        assertTrue(FontsLoader.isImported("font_file:NotoSansCJK.ttf"))
        assertEquals("NotoSansCJK.ttf", FontsLoader.parsePrefValue("font_file:NotoSansCJK.ttf"))
    }

    @Test
    fun importedPath_systemValue_isNotImportedAndParsesToNull() {
        // План: "serif" -> null (системный шрифт, путь файла не выбирается)
        assertFalse(FontsLoader.isImported("serif"))
        assertNull(FontsLoader.parsePrefValue("serif"))
    }

    @Test
    fun importedPath_sanitize_evilPathBecomesSafeDiskName() {
        // План: sanitize("../../evil/My Font!.ttf") -> безопасное непустое имя без "/" и ".."
        val result = FontsLoader.sanitizeFileName("../../evil/My Font!.ttf")
        assertTrue(result.isNotEmpty())
        assertFalse(result.contains('/'))
        assertFalse(result.contains(".."))
        // Имя файла на диске формируется из sanitize-результата; расширение сохраняется.
        assertTrue(FontsLoader.isValidFontExtension(result))
    }

    @Test
    fun importedPath_sanitize_emptyFallsBackToNonEmpty() {
        // План: sanitize("") -> fallback-имя (непустое), чтобы файл всегда имел имя.
        val result = FontsLoader.sanitizeFileName("")
        assertTrue(result.isNotEmpty())
        assertTrue(result.startsWith("font_"))
    }

    @Test
    fun importedPath_displayName_planInput_stripsPrefixAndExtension() {
        // План: displayName("font_file:NotoSansCJK.ttf", systemFonts) -> "NotoSansCJK"
        assertEquals("NotoSansCJK", FontsLoader.displayName("font_file:NotoSansCJK.ttf", setOf("serif")))
    }

    @Test
    fun importedPath_displayName_systemValue_returnsAsIs() {
        // План: displayName("serif", systemFonts) -> "serif" (системное имя не трогаем)
        assertEquals("serif", FontsLoader.displayName("serif", setOf("serif")))
    }
}