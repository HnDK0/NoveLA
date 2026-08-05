package my.noveldokusha.scraper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.noveldokusha.core.ExtensionManager
import my.noveldokusha.core.atomicWrite
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LuaSourceProviderImpl @Inject constructor(
    private val luaSourceLoader: LuaSourceLoader,
    private val extensionRepository: ExtensionManager,
    @ApplicationContext private val context: Context,
) : LuaSourceProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sourcesFlow = MutableStateFlow<List<SourceInterface>>(emptyList())
    override val sourcesFlow: Flow<List<SourceInterface>> = _sourcesFlow.asStateFlow()

    private val _loadedSourcesFlow = MutableStateFlow<List<SourceInterface>>(emptyList())
    override val loadedSourcesFlow: StateFlow<List<SourceInterface>> = _loadedSourcesFlow.asStateFlow()

    private val loadedSignal = CompletableDeferred<Unit>()

    // Сериализует reloadInternal: конкурирующие вызовы не должны гонять
    // сигнатуру и загрузку одновременно.
    private val reloadMutex = Mutex()

    // Сигнатура последней успешной загрузки. Если при повторном reloadInternal
    // (debounce-эмит из getInstalledExtensionsFlow) ничего не изменилось — пропускаем
    // полную перезагрузку Lua-источников. Это убирает второй проход компиляции на старте.
    private var lastSignature: String? = null

    init {
        scope.launch {
            val cached = loadCache()
            if (cached.isNotEmpty()) {
                _sourcesFlow.value = cached
            }
            reloadInternal(force = true)
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            extensionRepository.getInstalledExtensionsFlow().debounce(500).collect {
                reloadInternal()
            }
        }
    }

    override suspend fun awaitLoaded() = loadedSignal.await()

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
                    iconUrl = it.iconUrl,
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
            atomicWrite(cacheFile(), json.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Timber.e(e, "LuaSourceProvider: failed to save cache")
        }
    }

    override suspend fun reload() {
        reloadInternal(force = true)
    }

    private suspend fun reloadInternal(force: Boolean = false) {
        reloadMutex.withLock {
            val signature = computeSignature()
            if (!force && signature == lastSignature) {
                Timber.d("LuaSourceProvider: signature unchanged, skipping reload")
                return
            }
            // Ожидаемое число источников — для детекции частичной загрузки.
            // null = не удалось прочитать БД, считаем загрузку полной.
            val expectedCount = runCatching { extensionRepository.getEnabledExtensions().size }.getOrNull()

            try {
                luaSourceLoader.loadAllSources()
                    .onSuccess { sources ->
                        _sourcesFlow.value = sources
                        _loadedSourcesFlow.value = sources
                        saveCache(sources)
                        // Пост-загрузочная сигнатура: скачанные при первой загрузке .lua
                        // меняют mtime — если сохранить до-загрузочную, debounce-эмит
                        // сделает лишний полный проход. И только при полной загрузке:
                        // частичный успех (сеть упала при скачивании части расширений)
                        // оставляет сигнатуру устаревшей, и следующий эмит ретраит
                        // недостающие источники.
                        val complete = expectedCount == null || sources.size >= expectedCount
                        if (sources.isNotEmpty() && complete) {
                            lastSignature = computeSignature()
                        } else if (!complete) {
                            Timber.w(
                                "LuaSourceProvider: partial load (${sources.size}/$expectedCount), keeping stale signature for retry"
                            )
                        }
                        if (!loadedSignal.isCompleted) loadedSignal.complete(Unit)
                        Timber.d("LuaSourceProvider: loaded ${sources.size} sources")
                    }
                    .onFailure { err ->
                        if (!loadedSignal.isCompleted) loadedSignal.complete(Unit)
                        Timber.e(err, "LuaSourceProvider: reload failed")
                    }
            } catch (e: Exception) {
                if (!loadedSignal.isCompleted) loadedSignal.complete(Unit)
                Timber.e(e, "LuaSourceProvider: exception during reload")
            }
        }
    }

    /**
     * Хэш состояния, от которого зависит загрузка Lua-источников:
     * включённые расширения (id/version), настройки (codeUrl/icon) и файлы .lua на диске.
     * Вычисляется без компиляции — только чтение БД и атрибутов файлов.
     */
    private suspend fun computeSignature(): String {
        val enabled = try {
            extensionRepository.getEnabledExtensions()
        } catch (e: Exception) {
            Timber.e(e, "computeSignature: getEnabledExtensions failed")
            // Уникальная строка: сбой чтения БД не должен совпадать с lastSignature,
            // иначе релоад молча пропустится именно в момент, когда уместен ретрай.
            return "signature-error-" + System.nanoTime()
        }
        val parts = enabled
            .sortedBy { it.id }
            .map { ext ->
                val settings = try {
                    extensionRepository.getExtensionSettings(ext.id)
                } catch (e: Exception) {
                    Timber.e(e, "computeSignature: settings failed for ${ext.id}")
                    null
                }
                val file = luaSourceLoader.scriptFile(ext.id)
                "${ext.id}:${ext.version}:${ext.enabled}:${file.lastModified()}:${file.length()}:${settings?.hashCode() ?: 0}"
            }
        return parts.joinToString("|")
    }
}

private fun SourceInterface.toCacheEntry(): SourceCacheEntry =
    SourceCacheEntry(
        id = id,
        name = name ?: "",
        nameStrId = nameStrId.toString(),
        baseUrl = baseUrl,
        language = (this as? SourceInterface.Catalog)?.language?.iso639_1,
        iconUrl = (this as? SourceInterface.Catalog)?.iconUrl,
    )
