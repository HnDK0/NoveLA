package my.noveldokusha.scraper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет валидацию id расширения: id используется как имя файла в
 * lua_extensions, поэтому разделители пути и `..` должны отклоняться,
 * иначе скрипт может выйти за пределы каталога (path traversal).
 */
class ExtensionIdValidationTest {

    @Test
    fun `regular ids are accepted`() {
        assertTrue(isValidExtensionId("jaomix"))
        assertTrue(isValidExtensionId("ranobelib"))
        assertTrue(isValidExtensionId("local_source_abc12"))
        assertTrue(isValidExtensionId("novel-1.0"))
    }

    @Test
    fun `blank id is rejected`() {
        assertFalse(isValidExtensionId(""))
        assertFalse(isValidExtensionId("   "))
    }

    @Test
    fun `path separators are rejected`() {
        assertFalse(isValidExtensionId("local_../../evil"))
        assertFalse(isValidExtensionId("a/b"))
        assertFalse(isValidExtensionId("a\\b"))
    }

    @Test
    fun `parent directory traversal is rejected`() {
        assertFalse(isValidExtensionId(".."))
        assertFalse(isValidExtensionId("a/../b"))
        assertFalse(isValidExtensionId("a..b"))
    }
}
