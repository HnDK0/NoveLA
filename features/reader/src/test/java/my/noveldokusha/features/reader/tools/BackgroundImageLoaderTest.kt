package my.noveldokusha.features.reader.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImageLoaderTest {

    @Test
    fun parsePrefValue_importedValue_returnsFileName() {
        assertEquals("bg.png", BackgroundImageLoader.parsePrefValue("background_file:bg.png"))
    }

    @Test
    fun parsePrefValue_emptyValue_returnsNull() {
        assertNull(BackgroundImageLoader.parsePrefValue(""))
    }

    @Test
    fun parsePrefValue_presetValue_returnsNull() {
        assertNull(BackgroundImageLoader.parsePrefValue("paper"))
    }

    @Test
    fun sanitizeFileName_evilPath_isSafe() {
        val result = BackgroundImageLoader.sanitizeFileName("../../evil/My Pic!.png")
        assertEquals(".evilMyPic.png", result)
        assertFalse(result.contains('/'))
        assertFalse(result.contains(".."))
    }

    @Test
    fun sanitizeFileName_emptyResult_fallsBackToNonEmpty() {
        val result = BackgroundImageLoader.sanitizeFileName("!!!")
        assertTrue(result.isNotEmpty())
        assertTrue(result.startsWith("background_"))
    }

    @Test
    fun isImported_importedValue_true() {
        assertTrue(BackgroundImageLoader.isImported("background_file:bg.png"))
    }

    @Test
    fun isImported_systemValue_false() {
        assertFalse(BackgroundImageLoader.isImported("paper"))
    }

    @Test
    fun isValidImageExtension_jpg_true() {
        assertTrue(BackgroundImageLoader.isValidImageExtension("photo.jpg"))
    }

    @Test
    fun isValidImageExtension_jpeg_true() {
        assertTrue(BackgroundImageLoader.isValidImageExtension("photo.jpeg"))
    }

    @Test
    fun isValidImageExtension_png_true() {
        assertTrue(BackgroundImageLoader.isValidImageExtension("photo.png"))
    }

    @Test
    fun isValidImageExtension_gif_true() {
        assertTrue(BackgroundImageLoader.isValidImageExtension("anim.gif"))
    }

    @Test
    fun isValidImageExtension_webp_true() {
        assertTrue(BackgroundImageLoader.isValidImageExtension("photo.webp"))
    }

    @Test
    fun isValidImageExtension_uppercase_true() {
        assertTrue(BackgroundImageLoader.isValidImageExtension("BG.PNG"))
    }

    @Test
    fun isValidImageExtension_txt_false() {
        assertFalse(BackgroundImageLoader.isValidImageExtension("readme.txt"))
    }

    @Test
    fun isValidImageExtension_exe_false() {
        assertFalse(BackgroundImageLoader.isValidImageExtension("virus.exe"))
    }

    @Test
    fun isValidImageExtension_noExtension_false() {
        assertFalse(BackgroundImageLoader.isValidImageExtension("image"))
    }

    // --- Путь импортированного файла: proof of the resolution decision path ---
    // Модуль не использует Robolectric, поэтому реальная работа с ContentResolver
    // в unit-тесте недоступна. Эти тесты фиксируют чистую логику, которая решает,
    // пойти ли по пути импортированной картинки, и как именно формируется имя файла.

    @Test
    fun importedPath_planInput_parsesToFileName() {
        assertTrue(BackgroundImageLoader.isImported("background_file:wallpaper.png"))
        assertEquals("wallpaper.png", BackgroundImageLoader.parsePrefValue("background_file:wallpaper.png"))
    }

    @Test
    fun importedPath_systemValue_isNotImportedAndParsesToNull() {
        assertFalse(BackgroundImageLoader.isImported("paper"))
        assertNull(BackgroundImageLoader.parsePrefValue("paper"))
    }

    @Test
    fun importedPath_sanitize_evilPathBecomesSafeDiskName() {
        val result = BackgroundImageLoader.sanitizeFileName("../../evil/My Pic!.png")
        assertTrue(result.isNotEmpty())
        assertFalse(result.contains('/'))
        assertFalse(result.contains(".."))
    }

    @Test
    fun importedPath_sanitize_emptyFallsBackToNonEmpty() {
        val result = BackgroundImageLoader.sanitizeFileName("")
        assertTrue(result.isNotEmpty())
        assertTrue(result.startsWith("background_"))
    }
}
