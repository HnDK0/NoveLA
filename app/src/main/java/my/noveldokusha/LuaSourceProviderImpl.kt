package my.noveldokusha.scraper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import my.noveldokusha.core.ExtensionRepositoryInterface
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LuaSourceProviderImpl @Inject constructor(
    private val luaSourceLoader: LuaSourceLoader,
    private val extensionRepository: ExtensionRepositoryInterface,
    @ApplicationContext private val context: Context,
) : LuaSourceProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sourcesFlow = MutableStateFlow<List<SourceInterface>>(emptyList())
    override val sourcesFlow: Flow<List<SourceInterface>> = _sourcesFlow.asStateFlow()

    init {
        scope.launch {
            val cached = loadCache()
            if (cached.isEmpty()) {
                // Первый запуск — грузим, UI подождёт
                reload()
            } else {
                // Используем кэш мгновенно — без немедленного reload()
                _sourcesFlow.value = cached
            }
            // Подписываемся на изменения установленных плагинов
            // debounce(500) — при пакетной установке/удалении перезагрузка одна
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            extensionRepository.getInstalledExtensionsFlow().debounce(500).collect {
                reload()
            }
        }
    }

    override fun clearCache() {
        luaSourceLoader.clearCache()
        cacheFile().delete()
    }

    private fun cacheFile(): File = File(context.filesDir, "source_cache.json")

    private fun loadCache(): List<SourceInterface> {
        return try {
            val file = cacheFile()
            if (!file.exists()) return emptyList()
            val json = file.readText()
            val type = object : TypeToken<List<SourceCacheEntry>>() {}.type
            val entries: List<SourceCacheEntry> = Gson().fromJson(json, type)
            entries.map {
                CachedSource(
                    id = it.id,
                    name = it.name,
                    nameStrId = it.nameStrId.toIntOrNull() ?: 0,
                    baseUrl = it.baseUrl,
                    language = it.language?.let { lang -> my.noveldokusha.core.LanguageCode.entries.find { it.iso639_1 == lang } },
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "LuaSourceProvider: failed to load cache")
            emptyList()
        }
    }

    private fun saveCache(sources: List<SourceInterface>) {
        try {
            val entries = sources.map { it.toCacheEntry() }
            val json = Gson().toJson(entries)
            cacheFile().writeText(json)
        } catch (e: Exception) {
            Timber.e(e, "LuaSourceProvider: failed to save cache")
        }
    }

    private suspend fun reload() {
        try {
            luaSourceLoader.loadAllSources()
                .onSuccess { sources ->
                    _sourcesFlow.value = sources
                    saveCache(sources)
                    Timber.d("LuaSourceProvider: loaded ${sources.size} sources")
                }
                .onFailure { err ->
                    Timber.e(err, "LuaSourceProvider: reload failed")
                }
        } catch (e: Exception) {
            Timber.e(e, "LuaSourceProvider: exception during reload")
        }
    }
}

private fun SourceInterface.toCacheEntry(): SourceCacheEntry =
    SourceCacheEntry(
        id = id,
        name = name ?: "",
        nameStrId = nameStrId.toString(),
        baseUrl = baseUrl,
        language = (this as? SourceInterface.Catalog)?.language?.iso639_1,
    )
