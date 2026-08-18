package my.noveldokusha.scraper

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.mockito.kotlin.mock

/**
 * Проверяет чтение глобальной метки content_type из Lua-скрипта плагина.
 *
 * Метка — свойство источника (не книги): читается из глобальной переменной
 * скрипта и попадает в SourceMetadata (extractMetadata) и в BookResult
 * (МОСТ в convertLuaTableToBookResult). Невалидные/отсутствующие значения
 * нормализуются в "" (= новелла).
 */
class LuaSourceAdapterContentTypeTest {

    private fun adapter(script: String): LuaSourceAdapter {
        val globals = createLuaSandboxGlobals()
        val result = globals.load(script).call()
        // Как в LuaSourceLoader.loadScript: скрипт без return-таблицы — это сами globals.
        val luaScript = if (result.istable()) result else globals
        return LuaSourceAdapter(
            context = mock(),
            luaScript = luaScript,
            luaEngine = mock(),
            fileName = null
        )
    }

    @Test
    fun `content_type manga maps to manga in metadata`() {
        assertEquals("manga", adapter("content_type = 'manga'").extractMetadata().contentType)
    }

    @Test
    fun `content_type novel maps to novel in metadata`() {
        assertEquals("novel", adapter("content_type = 'novel'").extractMetadata().contentType)
    }

    @Test
    fun `missing content_type maps to empty in metadata`() {
        assertEquals("", adapter("-- no content_type").extractMetadata().contentType)
    }

    @Test
    fun `invalid content_type maps to empty in metadata`() {
        assertEquals("", adapter("content_type = 'comic'").extractMetadata().contentType)
    }

    @Test
    fun `bridge book result gets content_type from global script variable`() {
        val a = adapter("content_type = 'manga'")
        val bookTable = LuaTable().also {
            it.set("title", "Test Book")
            it.set("url", "http://example.com/book")
        }
        assertEquals("manga", a.convertLuaTableToBookResult(bookTable).contentType)
    }

    @Test
    fun `bridge book result defaults to empty when content_type missing`() {
        val a = adapter("-- no content_type")
        val bookTable = LuaTable().also {
            it.set("title", "Test Book")
            it.set("url", "http://example.com/book")
        }
        assertEquals("", a.convertLuaTableToBookResult(bookTable).contentType)
    }
}