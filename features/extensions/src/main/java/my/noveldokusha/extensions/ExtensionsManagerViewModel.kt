package my.noveldokusha.extensions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.core.ExtensionManager
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.ExtensionInfoCached
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.LuaSourceLoader
import my.noveldokusha.scraper.LuaSourceProvider
import my.noveldokusha.data.ScraperRepository
import org.yaml.snakeyaml.Yaml
import timber.log.Timber
import my.noveldokusha.core.getLanguageDisplayName
import javax.inject.Inject

@HiltViewModel
class ExtensionsManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionManager: ExtensionManager,
    private val httpClient: NetworkClient,
    private val appPreferences: AppPreferences,
    private val scraperRepository: ScraperRepository,
    private val luaSourceLoader: LuaSourceLoader,          // ← для скачивания .lua
    private val luaSourceProvider: LuaSourceProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ExtensionsScreenState())
    val state: StateFlow<ExtensionsScreenState> = _state.asStateFlow()

    /** Сериализует импорт/сохранение/сброс локальных Lua-скриптов,
     *  чтобы параллельные операции не гоняли reload() и запись файлов одновременно. */
    private val localMutex = Mutex()

    private var cachedAvailableExtensions: List<ExtensionInfo>? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L

    init {
        _state.update {
            it.copy(
                selectedLanguages = appPreferences.EXTENSIONS_LANGUAGES_FILTER.value,
                repositoryUrl = appPreferences.EXTENSIONS_REPOSITORY_URL.value
            )
        }

        // Реактивно синхронизируем список установленных расширений
        viewModelScope.launch {
            extensionManager.getInstalledExtensionsFlow().collectLatest { extensions ->
                _state.update { it.copy(extensions = extensions) }
                updateAvailableExtensionsStatus()
            }
        }

        // Загружаем кеш и (при необходимости) актуальные данные из сети — на фоновом потоке
        viewModelScope.launch {
            loadCachedExtensions()        // выставляет cachedAvailableExtensions + lastFetchTime
            loadAllAvailableExtensions()
        }
    }

    fun onEvent(event: ExtensionsScreenEvent) = when (event) {
        is ExtensionsScreenEvent.OnExtensionToggle       -> toggleExtension(event.extensionId, event.enabled)
        is ExtensionsScreenEvent.OnExtensionUninstall    -> uninstallExtension(event.extensionId)
        is ExtensionsScreenEvent.OnExtensionConfigure    -> Unit // TODO
        ExtensionsScreenEvent.OnRefresh                  -> refreshAll()
        ExtensionsScreenEvent.OnShowRepositoryDialog     -> _state.update { it.copy(showRepositoryDialog = true) }
        ExtensionsScreenEvent.OnHideRepositoryDialog     -> _state.update { it.copy(showRepositoryDialog = false) }
        is ExtensionsScreenEvent.OnUpdateRepositoryUrl   -> updateRepositoryUrl(event.url)
        is ExtensionsScreenEvent.OnLanguageFilterToggle  -> toggleLanguageFilter(event.languageCode)
        is ExtensionsScreenEvent.OnLanguageFilterClear   -> clearLanguageFilter(event.languageCode)
        ExtensionsScreenEvent.OnBackPressed              -> Unit
        is ExtensionsScreenEvent.OnExtensionInstall      -> installExtension(event.extensionId)
        is ExtensionsScreenEvent.OnExtensionUninstallById -> uninstallExtensionById(event.extensionId)
        is ExtensionsScreenEvent.OnEditLuaClick           -> openLuaEditor(event.extensionId)
        ExtensionsScreenEvent.OnLuaEditorDismiss          -> closeLuaEditor()
        is ExtensionsScreenEvent.OnLuaEditorChange        -> updateLuaEditorText(event.code)
        ExtensionsScreenEvent.OnLuaEditorSave             -> saveLuaEditor()
        is ExtensionsScreenEvent.OnResetLuaClick          -> resetLuaExtension(event.extensionId)
    }

    // ── Загрузка доступных расширений из репозитория ─────────────────────────

    private fun loadAllAvailableExtensions(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedAvailableExtensions != null && now - lastFetchTime < CACHE_DURATION_MS) {
            applyCache()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val repoUrl  = _state.value.repositoryUrl
                val responseBody = withContext(Dispatchers.IO) {
                    val response = if (forceRefresh) {
                        httpClient.getWithHeaders(repoUrl, mapOf("Cache-Control" to "no-cache"))
                    } else {
                        httpClient.get(repoUrl)
                    }
                    response.body.string()
                }
                val yaml     = Yaml()
                @Suppress("UNCHECKED_CAST")
                val repoIndex = withContext(Dispatchers.Default) {
                    yaml.loadAs(responseBody, Map::class.java)
                } as Map<String, Any>

                @Suppress("UNCHECKED_CAST")
                val languages = repoIndex["languages"] as Map<String, Map<String, Any>>
                val allExt = mutableListOf<ExtensionInfo>()

                languages.forEach { (langCode, langInfo) ->
                    try {
                        val langUrl = langInfo["url"] as String
                        val langBody = withContext(Dispatchers.IO) {
                            httpClient.get(langUrl).body.string()
                        }
                        @Suppress("UNCHECKED_CAST")
                        val langData = withContext(Dispatchers.Default) {
                            yaml.loadAs(langBody, Map::class.java)
                        } as Map<String, Any>

                        @Suppress("UNCHECKED_CAST")
                        val sources = langData["sources"] as List<Map<String, Any>>
                        sources.forEach { src ->
                            val id = src["id"] as String
                            val installedVer = getInstalledVersion(id)
                            val remoteVer = src["version"] as? String ?: "1.0.0"
                            Timber.d("Processing extension: $id, installed: $installedVer, remote: $remoteVer")
                            allExt.add(
                                ExtensionInfo(
                                    id               = id,
                                    name             = src["name"] as? String ?: "Unknown",
                                    description      = src.get("description") as? String ?: "",
                                    author           = src.get("author") as? String ?: "",
                                    version          = installedVer ?: remoteVer,
                                    remoteVersion    = remoteVer, // Удаленная версия из YAML
                                    codeUrl          = src["url"] as String, // Поле называется "url" в YAML
                                    iconUrl          = src["icon"] as String,
                                    language         = langCode,
                                    isInstalled      = installedVer != null,
                                    isEnabled        = isEnabled(id),
                                    isUpdateAvailable = isUpdateAvailable(remoteVer, installedVer)
                                )
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load language $langCode")
                    }
                }

                cachedAvailableExtensions = allExt
                lastFetchTime = now

                // Сохраняем в кеш для быстрой загрузки при следующем запуске
                saveCachedExtensions(allExt)

                val langList = allExt.groupBy { it.language }
                    .map { (code, list) ->
                        ExtensionLanguage(code, getLanguageDisplayName(code), list.size)
                    }
                    .sortedBy { it.name }

                _state.update {
                    it.copy(
                        availableExtensions = allExt,
                        availableLanguages  = langList,
                        isLoading           = false,
                        error               = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load extensions repository")
                _state.update { it.copy(isLoading = false, error = context.getString(my.noveldokusha.strings.R.string.ext_failed_to_load, e.message)) }
            }
        }
    }

    private fun applyCache() {
        val cached = cachedAvailableExtensions ?: return
        _state.update { state ->
            state.copy(
                availableExtensions = cached.map { ext ->
                    val installedVer = getInstalledVersion(ext.id)
                    ext.copy(
                        isInstalled       = installedVer != null,
                        isEnabled         = isEnabled(ext.id),
                        version           = installedVer ?: ext.version,
                        isUpdateAvailable = isUpdateAvailable(ext.remoteVersion, installedVer)
                    )
                }
            )
        }
    }

    private fun loadCachedExtensions() {
        val cachedEntries = appPreferences.EXTENSIONS_AVAILABLE_CACHE.value
        if (cachedEntries.isNotEmpty()) {
            val extensions = cachedEntries.map { cached ->
                ExtensionInfo(
                    id = cached.id,
                    name = cached.name,
                    description = cached.description,
                    author = cached.author,
                    version = cached.version,
                    remoteVersion = cached.remoteVersion,
                    codeUrl = cached.codeUrl,
                    iconUrl = cached.iconUrl,
                    language = cached.language,
                    isInstalled = getInstalledVersion(cached.id) != null,
                    isEnabled = isEnabled(cached.id),
                    isUpdateAvailable = isUpdateAvailable(cached.remoteVersion, getInstalledVersion(cached.id))
                )
            }
            val langList = extensions.groupBy { it.language }
                .map { (code, list) ->
                    ExtensionLanguage(code, getLanguageDisplayName(code), list.size)
                }
                .sortedBy { it.name }

            _state.update {
                it.copy(
                    availableExtensions = extensions,
                    availableLanguages = langList
                )
            }
            Timber.d("Loaded ${extensions.size} extensions from cache")
            cachedAvailableExtensions = extensions
            lastFetchTime = System.currentTimeMillis()
        }
    }

    private fun saveCachedExtensions(extensions: List<ExtensionInfo>) {
        val cached = extensions.map { ext ->
            ExtensionInfoCached(
                id = ext.id,
                name = ext.name,
                description = ext.description,
                author = ext.author,
                version = ext.version,
                remoteVersion = ext.remoteVersion,
                codeUrl = ext.codeUrl,
                iconUrl = ext.iconUrl,
                language = ext.language
            )
        }
        appPreferences.EXTENSIONS_AVAILABLE_CACHE.value = cached
        Timber.d("Saved ${cached.size} extensions to cache")
    }

    // ── Установка ─────────────────────────────────────────────────────────────

    /**
     * Процесс установки:
     * 1. Скачать .lua файл на диск через LuaSourceLoader
     * 2. Сохранить запись в БД через ExtensionManager
     *    (codeUrl сохраняется в settings как JSON для последующей загрузки)
     * 3. Scraper перезагрузится реактивно через Flow установленных расширений
     */
    private fun installExtension(extensionId: String) {
        viewModelScope.launch {
            val extInfo = _state.value.availableExtensions.find { it.id == extensionId } ?: return@launch
            setInstalling(extensionId, true)
            try {
                // Шаг 1: Скачать .lua на диск
                val downloaded = luaSourceLoader.downloadAndCacheScript(extensionId, extInfo.codeUrl)
                if (!downloaded) {
                    _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.ext_failed_to_download, extInfo.name)) }
                    return@launch
                }

                // Шаг 2: Записать в БД с НОВОЙ версией
                extensionManager.installExtensionFromInfo(
                    id       = extensionId,
                    name     = extInfo.name,
                    version  = extInfo.remoteVersion, // Используем новую версию!
                    language = extInfo.language,
                    imageUrl = extInfo.iconUrl,
                    codeUrl  = extInfo.codeUrl
                )
                // Шаг 2б: Сохранить codeUrl в settings как YAML,
                // чтобы LuaSourceLoader знал откуда перескачать при следующем запуске
                val settingsYaml = "codeUrl: \"${extInfo.codeUrl}\""
                extensionManager.updateExtensionSettings(extensionId, settingsYaml)

                Timber.d("Installed extension: ${extInfo.name}")
                // Шаг 3: Scraper обновится реактивно через extensionRepository.getInstalledExtensionsFlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to install ${extInfo.name}")
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.ext_failed_to_install, extInfo.name, e.message)) }
                // Если установка не удалась, удаляем скачанный файл
                luaSourceLoader.removeScript(extensionId)
            } finally {
                setInstalling(extensionId, false)
            }
        }
    }

    // ── Удаление ──────────────────────────────────────────────────────────────

    private fun uninstallExtension(extensionId: String) = uninstallExtensionById(extensionId)

    private fun uninstallExtensionById(extensionId: String) {
        viewModelScope.launch {
            try {
                extensionManager.uninstallExtension(extensionId)
                luaSourceLoader.removeScript(extensionId)
                // Scraper обновится реактивно
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to uninstall $extensionId")
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.ext_failed_to_uninstall)) }
            }
        }
    }

    // ── Локальный импорт / редактор Lua ──────────────────────────────────────

    private fun luaField(code: String, key: String, fallback: String = ""): String {
        val regex = Regex("""(?m)^\s*$key\s*=\s*["']([^"']+)["']""")
        return regex.find(code)?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { fallback }
    }

    private suspend fun settingsMap(extensionId: String): Map<String, Any>? {
        val raw = extensionManager.getExtensionSettings(extensionId) ?: return null
        if (raw.isBlank() || raw == "{}") return null
        return try {
            @Suppress("UNCHECKED_CAST")
            Yaml().loadAs(raw, Map::class.java) as? Map<String, Any>
        } catch (e: Exception) {
            Timber.w(e, "Bad settings YAML for $extensionId")
            null
        }
    }

    private suspend fun isLocalExtension(extensionId: String): Boolean =
        settingsMap(extensionId)?.get("sourceType")?.toString() == "local"

    private suspend fun getCodeUrl(extensionId: String): String? =
        settingsMap(extensionId)?.get("codeUrl")?.toString()

    /** Уникальный id локального скрипта: префикс + база + 5 случайных символов.
     *  Случайный суффикс гарантирует, что разные импорты не перезаписывают друг друга,
     *  а сгенерированный id один раз запекается в скрипт (см. [withLocalId]), поэтому
     *  повторный импорт того же файла (в т.ч. отредактированного) идемпотентен. */
    private fun localStorageId(baseId: String): String {
        val safe = baseId.replace(Regex("""[^A-Za-z0-9_]"""), "_")
            .takeIf { it.isNotBlank() } ?: "local_source"
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rand = (1..5).map { alphabet.random() }.joinToString("")
        return "local_${safe}_$rand"
    }

    /** Подставляет в Lua-код поле id = [id]: заменяет существующее или дописывает в начало. */
    private fun withLocalId(code: String, id: String): String {
        val idRegex = Regex("""(?m)^\s*id\s*=\s*["'][^"']*["']""")
        return if (idRegex.containsMatchIn(code)) {
            idRegex.replace(code) { "id = \"$id\"" }
        } else {
            "id = \"$id\"\n$code"
        }
    }

    fun importLuaFromText(fileName: String, code: String) = viewModelScope.launch {
        localMutex.withLock {
            if (code.isBlank()) {
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.lua_empty_error)) }
                return@withLock
            }
            if (luaSourceLoader.validateScript(code, fileName).isFailure) {
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.lua_compile_error)) }
                return@withLock
            }

            val fallbackId = fileName.substringBeforeLast('.').ifBlank { "local_source" }
            val declaredId = luaField(code, "id").trim()
            val baseId = declaredId.ifBlank { fallbackId }
            // id уже в нашем формате (local_...) — переиспользуем как есть (идемпотентный реимпорт).
            // Иначе генерируем уникальный local_<base>_<rand> и запекаем его в скрипт.
            val storageId = if (declaredId.startsWith("local_")) declaredId
                            else localStorageId(baseId)
            val finalCode = if (declaredId.startsWith("local_")) code
                            else withLocalId(code, storageId)

            val name = luaField(finalCode, "name", baseId)
            val version = luaField(finalCode, "version", "1.0.0")
            val language = luaField(finalCode, "language", "en")
            val icon = luaField(finalCode, "icon")

            if (extensionManager.isExtensionInstalled(storageId)) {
                // Идемпотентный реимпорт того же файла — обновляем скрипт без создания дубля.
                luaSourceLoader.saveScript(storageId, finalCode)
                extensionManager.updateExtensionSettings(storageId, "sourceType: local")
                luaSourceProvider.reload()
                return@withLock
            }

            // Защита от двух локальных копий с одинаковым именем.
            val nameClash = _state.value.extensions.find { it.name == name }
            if (nameClash != null && isLocalExtension(nameClash.id)) {
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.lua_duplicate_error, name)) }
                return@withLock
            }

            if (!luaSourceLoader.saveScript(storageId, finalCode)) {
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.lua_import_error)) }
                return@withLock
            }

            extensionManager.installExtensionFromInfo(
                id = storageId,
                name = name,
                version = version,
                language = language,
                imageUrl = icon.ifBlank { null },
                codeUrl = null
            )
            extensionManager.updateExtensionSettings(storageId, "sourceType: local")
            luaSourceProvider.reload()
        }
    }

    fun openLuaEditor(extensionId: String) = viewModelScope.launch {
        val ext = _state.value.extensions.find { it.id == extensionId } ?: return@launch
        val code = luaSourceLoader.readScript(extensionId)
        if (code == null) {
            _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.lua_not_editable)) }
            return@launch
        }
        _state.update {
            it.copy(
                showLuaEditor = true,
                luaEditorExtensionId = extensionId,
                luaEditorTitle = ext.name,
                luaEditorCode = code,
                luaEditorError = null
            )
        }
    }

    fun updateLuaEditorText(code: String) {
        _state.update { it.copy(luaEditorCode = code, luaEditorError = null) }
    }

    fun closeLuaEditor() {
        _state.update {
            it.copy(
                showLuaEditor = false,
                luaEditorExtensionId = null,
                luaEditorTitle = "",
                luaEditorCode = "",
                luaEditorError = null
            )
        }
    }

    fun saveLuaEditor() = viewModelScope.launch {
        val id = _state.value.luaEditorExtensionId ?: return@launch
        val code = _state.value.luaEditorCode
        if (luaSourceLoader.validateScript(code, "$id.lua").isFailure) {
            _state.update { it.copy(luaEditorError = context.getString(my.noveldokusha.strings.R.string.lua_compile_error)) }
            return@launch
        }
        if (!luaSourceLoader.saveScript(id, code)) {
            _state.update { it.copy(luaEditorError = context.getString(my.noveldokusha.strings.R.string.lua_save_error)) }
            return@launch
        }

        val newName = luaField(code, "name", _state.value.luaEditorTitle)
            .takeIf { it.isNotBlank() } ?: _state.value.luaEditorTitle
        val newVersion = luaField(code, "version", "1.0.0")
        val newLanguage = luaField(code, "language", "en")
        val newIcon = luaField(code, "icon")
        val oldCodeUrl = getCodeUrl(id)

        extensionManager.installExtensionFromInfo(
            id = id,
            name = newName,
            version = newVersion,
            language = newLanguage,
            imageUrl = newIcon.ifBlank { null },
            codeUrl = oldCodeUrl
        )
        luaSourceProvider.reload()
        closeLuaEditor()
    }

    fun resetLuaExtension(extensionId: String) = viewModelScope.launch {
        localMutex.withLock {
            val codeUrl = getCodeUrl(extensionId)
            if (codeUrl.isNullOrBlank()) {
                luaSourceLoader.removeScript(extensionId)
                if (isLocalExtension(extensionId)) {
                    extensionManager.uninstallExtension(extensionId)
                }
                luaSourceProvider.reload()
                return@withLock
            }

            luaSourceLoader.removeScript(extensionId)
            if (!luaSourceLoader.downloadAndCacheScript(extensionId, codeUrl)) {
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.lua_restore_error)) }
                return@withLock
            }
            luaSourceProvider.reload()
        }
    }

    // ── Вкл/выкл ─────────────────────────────────────────────────────────────

    private fun toggleExtension(extensionId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) extensionManager.enableExtension(extensionId)
                else         extensionManager.disableExtension(extensionId)
                // Scraper обновится реактивно
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle extension $extensionId")
                _state.update { it.copy(error = context.getString(my.noveldokusha.strings.R.string.ext_failed_to_toggle)) }
            }
        }
    }

    // ── Фильтры / репозиторий ────────────────────────────────────────────────

    private fun toggleLanguageFilter(code: String) {
        _state.update { state ->
            val updated = if (code in state.selectedLanguages) state.selectedLanguages - code
            else state.selectedLanguages + code
            state.copy(selectedLanguages = updated)
        }
        appPreferences.EXTENSIONS_LANGUAGES_FILTER.value = _state.value.selectedLanguages
    }

    private fun clearLanguageFilter(code: String?) {
        _state.update { it.copy(selectedLanguages = if (code == null) emptySet() else it.selectedLanguages - code) }
        appPreferences.EXTENSIONS_LANGUAGES_FILTER.value = _state.value.selectedLanguages
    }

    private fun refreshAll() = loadAllAvailableExtensions(forceRefresh = true)

    private fun updateRepositoryUrl(url: String) {
        viewModelScope.launch {
            appPreferences.EXTENSIONS_REPOSITORY_URL.value = url
            _state.update { it.copy(repositoryUrl = url, showRepositoryDialog = false) }
            refreshAll()
        }
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    private fun updateAvailableExtensionsStatus() {
        _state.update { state ->
            state.copy(
                availableExtensions = state.availableExtensions.map { ext ->
                    val installedVer = getInstalledVersion(ext.id)
                ext.copy(
                    isInstalled       = installedVer != null,
                    isEnabled         = isEnabled(ext.id),
                    version           = installedVer ?: ext.version,
                    isUpdateAvailable = isUpdateAvailable(ext.remoteVersion, installedVer)
                )
                }
            )
        }
    }

    private fun setInstalling(id: String, installing: Boolean) {
        _state.update { state ->
            state.copy(availableExtensions = state.availableExtensions.map {
                if (it.id == id) it.copy(isInstalling = installing) else it
            })
        }
    }

    private fun getInstalledVersion(id: String) =
        _state.value.extensions.find { it.id == id }?.version

    private fun isEnabled(id: String) =
        _state.value.extensions.find { it.id == id }?.enabled ?: false

    private fun isUpdateAvailable(available: String, installed: String?): Boolean {
        if (installed == null) return false
        return try {
            val a = available.split(".").map { it.toIntOrNull() ?: 0 }
            val b = installed.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val av = a.getOrElse(i) { 0 }
                val bv = b.getOrElse(i) { 0 }
                if (av > bv) return true
                if (av < bv) return false
            }
            false
        } catch (e: Exception) { false }
    }

}
