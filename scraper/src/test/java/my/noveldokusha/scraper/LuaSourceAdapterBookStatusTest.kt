package my.noveldokusha.scraper

import android.content.Context
import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Проверяет чтение статуса книги и даты последнего обновления из Lua-скрипта плагина.
 *
 * Обе функции опциональны: скрипт без getBookStatus/getBookLastUpdate даёт
 * Response.Success(null) (guard на isnil), строка/число конвертируются через
 * tojstring, nil и невалидные значения — в null.
 */
class LuaSourceAdapterBookStatusTest {

    private fun adapter(script: String): LuaSourceAdapter {
        val globals = createLuaSandboxGlobals()
        val result = globals.load(script).call()
        // Как в LuaSourceLoader.loadScript: скрипт без return-таблицы — это сами globals.
        val luaScript = if (result.istable()) result else globals
        // withSourceContext пишет в luaEngine.currentSourceId (ThreadLocal) — мок должен вернуть реальный.
        val luaEngine = mock<LuaEngine>()
        whenever(luaEngine.currentSourceId).thenReturn(ThreadLocal())
        return LuaSourceAdapter(
            context = mock(),
            luaScript = luaScript,
            luaEngine = luaEngine,
            fileName = null
        )
    }

    @Test
    fun `script without getBookStatus and getBookLastUpdate returns null for both`() = runBlocking {
        val a = adapter("-- no status functions")
        assertEquals(Response.Success<String?>(null), a.getBookStatus("http://example.com/book"))
        assertEquals(Response.Success<String?>(null), a.getBookLastUpdate("http://example.com/book"))
    }

    @Test
    fun `getBookStatus returning string maps to that string`() = runBlocking {
        val a = adapter("function getBookStatus(url) return 'Ongoing' end")
        assertEquals(Response.Success("Ongoing"), a.getBookStatus("http://example.com/book"))
    }

    @Test
    fun `getBookStatus returning nil maps to null`() = runBlocking {
        val a = adapter("function getBookStatus(url) return nil end")
        assertEquals(Response.Success<String?>(null), a.getBookStatus("http://example.com/book"))
    }

    @Test
    fun `getBookLastUpdate returning string maps to that string`() = runBlocking {
        val a = adapter("function getBookLastUpdate(url) return '2024-01-01' end")
        assertEquals(Response.Success("2024-01-01"), a.getBookLastUpdate("http://example.com/book"))
    }

    @Test
    fun `numeric return value maps to string via tojstring`() = runBlocking {
        val a = adapter("function getBookStatus(url) return 2024 end")
        assertEquals(Response.Success("2024"), a.getBookStatus("http://example.com/book"))
    }
}