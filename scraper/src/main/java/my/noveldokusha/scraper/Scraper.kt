package my.noveldokusha.scraper

import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.databases.BakaUpdates
import my.noveldokusha.scraper.databases.NovelUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldokusha.scraper.sources.LocalSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Scraper @Inject constructor(
    networkClient: NetworkClient,
    localSource: LocalSource,
    @Suppress("UNUSED_PARAMETER") appPreferences: AppPreferences,
    // Интерфейс вместо LuaSourceLoader — нет зависимости от Android Context
    private val luaSourceProvider: LuaSourceProvider
) {
    val databasesList = setOf(
        NovelUpdates(networkClient),
        BakaUpdates(networkClient)
    )

    val localSourcesList = setOf(localSource)

    private val scraperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _luaSources = MutableStateFlow<Set<SourceInterface>>(emptySet())
    val luaSources: StateFlow<Set<SourceInterface>> = _luaSources.asStateFlow()

    init {
        scraperScope.launch {
            luaSourceProvider.sourcesFlow.collect { sources ->
                _luaSources.value = sources.toSet()
                Timber.d("Lua sources updated: ${sources.size}")
            }
        }
    }

    suspend fun awaitLoaded() = luaSourceProvider.awaitLoaded()

    fun clearCache() = luaSourceProvider.clearCache()

    /** Все источники включая CachedSource заглушки (для UI) */
    val sourcesList: Set<SourceInterface>
        get() = localSourcesList + _luaSources.value

    /** Только загруженные реальные Lua-источники (для data-операций) */
    val loadedSourcesList: Set<SourceInterface>
        get() = localSourcesList + luaSourceProvider.loadedSourcesFlow.value.toSet()

    val sourcesCatalogListFlow: kotlinx.coroutines.flow.Flow<List<SourceInterface.Catalog>> =
        _luaSources.map { lua ->
            (localSourcesList + lua).filterIsInstance<SourceInterface.Catalog>()
        }

    val sourcesLanguagesListFlow: kotlinx.coroutines.flow.Flow<List<my.noveldokusha.core.LanguageCode>> =
        sourcesCatalogListFlow.map { catalogs ->
            catalogs.mapNotNull { it.language }.distinct()
        }

    private fun String.isCompatibleWithBaseUrl(baseUrl: String): Boolean {
        val a = if (endsWith("/")) this else "$this/"
        val b = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return a.startsWith(b)
    }

    fun getCompatibleSource(url: String): SourceInterface? =
        loadedSourcesList.find { url.isCompatibleWithBaseUrl(it.baseUrl) }

    fun getCompatibleSourceCatalog(url: String): SourceInterface.Catalog? =
        loadedSourcesList.filterIsInstance<SourceInterface.Catalog>()
            .find { url.isCompatibleWithBaseUrl(it.baseUrl) }

    fun getCompatibleDatabase(url: String): DatabaseInterface? =
        databasesList.find { url.isCompatibleWithBaseUrl(it.baseUrl) }

    fun isUrlSupported(url: String) = sourcesList.find { url.isCompatibleWithBaseUrl(it.baseUrl) } != null

    fun getSourceId(url: String): String? = sourcesList.find { url.isCompatibleWithBaseUrl(it.baseUrl) }?.id
}
