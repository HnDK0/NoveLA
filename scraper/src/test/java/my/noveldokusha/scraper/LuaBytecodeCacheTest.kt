package my.noveldokusha.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет байткод-раундтрип LuaJ через реальные internal-функции
 * LuaEngine: compileLuaBytecode -> loadLuaBytecodeChunk. Это точный путь
 * .lbc-кэша без Android-зависимостей.
 */
class LuaBytecodeCacheTest {

    private val sampleScript = """
        function getSearchPage() return "http://example.com/search" end
        function getChapterContent() return { html = "<p>hi</p>" } end
        return { getSearchPage = getSearchPage, getChapterContent = getChapterContent }
    """.trimIndent()

    @Test
    fun `bytecode roundtrip returns same table as text compile`() {
        val globals = createLuaSandboxGlobals()
        val bytes = compileLuaBytecode(globals, sampleScript, "script")

        // Скомпилированный chunk непустой и содержит бинарную сигнатуру
        assertTrue("chunk должен быть непустым", bytes.size > 16)

        val result = loadLuaBytecodeChunk(globals, bytes, "script").call()
        assertTrue("скрипт должен вернуть таблицу", result.istable())
        assertEquals(
            "http://example.com/search",
            result.checktable().get("getSearchPage").call().tojstring()
        )
    }

    @Test
    fun `bytecode from one sandbox loads in a fresh sandbox (cold start)`() {
        // Холодный старт = новый процесс = новые Globals, байткод читается с диска
        val bytes = compileLuaBytecode(createLuaSandboxGlobals(), sampleScript, "script")

        val result = loadLuaBytecodeChunk(createLuaSandboxGlobals(), bytes, "script").call()
        assertTrue(result.istable())
        assertEquals(
            "<p>hi</p>",
            result.checktable().get("getChapterContent").call().get("html").tojstring()
        )
    }

    @Test
    fun `corrupt bytecode fails to load (cache delete + recompile path)`() {
        val globals = createLuaSandboxGlobals()
        val bytes = compileLuaBytecode(globals, sampleScript, "script")
        // Ломаем сигнатуру бинарного chunk (0x1b "Lua" ...) — LuaJ падает на ней
        // при undump, а LuaEngine.loadScriptChunk реагирует удалением файла и
        // перекомпиляцией из текста.
        bytes[0] = 0x00

        assertThrows(org.luaj.vm2.LuaError::class.java) {
            loadLuaBytecodeChunk(globals, bytes, "script")
        }
    }
}
