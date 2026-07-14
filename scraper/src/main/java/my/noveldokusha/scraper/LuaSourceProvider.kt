package my.noveldokusha.scraper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Интерфейс провайдера Lua источников.
 * Позволяет модулю scraper не зависеть от Android Context напрямую.
 * Реализация находится в модуле app/data где есть доступ к Context.
 */
interface LuaSourceProvider {
    /** Flow установленных и включённых Lua источников (включая CachedSource заглушки для UI) */
    val sourcesFlow: Flow<List<SourceInterface>>

    /** Только загруженные реальные Lua источники (без CachedSource заглушек) */
    val loadedSourcesFlow: StateFlow<List<SourceInterface>>

    /** Ждёт завершения загрузки реальных Lua скриптов. Возвращает мгновенно если уже загружены. */
    suspend fun awaitLoaded()

    /** Перезагрузить установленные Lua-источники с диска и настроек репозитория. */
    suspend fun reload()

    /** Очистить кэш скомпилированных скриптов */
    fun clearCache()
}