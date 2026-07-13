package my.noveldokusha.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.data.AppRemoteRepository
import my.noveldokusha.data.AppRepository
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.isCoverValid
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.tooling.application_workers.AppWorkersInteractions
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import my.noveldokusha.settings.BuildConfig
import my.noveldokusha.debug.MemoryDiagnostics
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val appScope: AppCoroutineScope,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
    private val appFileResolver: AppFileResolver,
    private val appRemoteRepository: AppRemoteRepository,
    private val toasty: Toasty,
    private val appWorkersInteractions: AppWorkersInteractions,
) : ViewModel() {

    var onRestartApp: (() -> Unit)? = null

    var isCleaningDatabase = mutableStateOf(false)
    var isCleaningImages = mutableStateOf(false)
    var isCleaningChapterCache = mutableStateOf(false)

    private val cloudflareBypassEnabled by appPreferences.CLOUDFLARE_BYPASS_ENABLED.state(viewModelScope)

    private val appThemePref = appPreferences.APP_THEME.state(viewModelScope)
    private val darkModePref = appPreferences.THEME_DARK_MODE.state(viewModelScope)

    val state = SettingsScreenState(
        databaseSize = mutableStateOf(""),
        imageFolderSize = mutableStateOf(""),
        isCleaningDatabase = isCleaningDatabase,
        isCleaningImages = isCleaningImages,
        currentLanguage = appPreferences.APP_LANGUAGE_CODE.state(viewModelScope),
        currentAppTheme = derivedStateOf {
            try { AppTheme.valueOf(appThemePref.value) }
            catch (_: Exception) { AppTheme.DEFAULT }
        },
        currentDarkMode = derivedStateOf {
            try { DarkMode.valueOf(darkModePref.value) }
            catch (_: Exception) { DarkMode.SYSTEM }
        },
        updateAppSetting = SettingsScreenState.UpdateApp(
            currentAppVersion = appRemoteRepository.getCurrentAppVersion().toString(),
            showNewVersionDialog = mutableStateOf(null),
            appUpdateCheckerEnabled = appPreferences.GLOBAL_APP_UPDATER_CHECKER_ENABLED.state(
                viewModelScope
            ),
            checkingForNewVersion = mutableStateOf(false)
        ),
        libraryAutoUpdate = SettingsScreenState.LibraryAutoUpdate(
            autoUpdateEnabled = appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED.state(
                viewModelScope
            ),
            autoUpdateIntervalHours = appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS.state(
                viewModelScope
            )
        ),
        massAddDelayMs = appPreferences.MASS_ADD_DELAY_MS.state(viewModelScope),
        downloadDelayMs = appPreferences.DOWNLOAD_DELAY_MS.state(viewModelScope),
        geminiApiKey = appPreferences.TRANSLATION_GEMINI_API_KEY.state(viewModelScope),
        geminiModel = appPreferences.TRANSLATION_GEMINI_MODEL.state(viewModelScope),
        translationProvider = appPreferences.TRANSLATION_PROVIDER.state(viewModelScope),
        googlePaApiKeys = appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.state(viewModelScope),
        scraperUserAgent = appPreferences.SCRAPER_USER_AGENT.state(viewModelScope),
        cloudflareBypassEnabled = appPreferences.CLOUDFLARE_BYPASS_ENABLED.state(viewModelScope),
        cloudflareChallengeTimeoutSeconds = appPreferences.CLOUDFLARE_CHALLENGE_TIMEOUT_SECONDS.state(viewModelScope),
        openAiBaseUrl          = appPreferences.TRANSLATION_OPENAI_BASE_URL.state(viewModelScope),
        openAiApiKeys          = appPreferences.TRANSLATION_OPENAI_API_KEYS.state(viewModelScope),
        openAiModel            = appPreferences.TRANSLATION_OPENAI_MODEL.state(viewModelScope),
        activeSystemPrompt     = appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.state(viewModelScope),
        promptPresets          = appPreferences.TRANSLATION_PROMPT_PRESETS.state(viewModelScope),
        promptUseEnglishLocale = appPreferences.TRANSLATION_PROMPT_USE_ENGLISH_LOCALE.state(viewModelScope),
        llmBatchSize           = appPreferences.TRANSLATION_BATCH_SIZE.state(viewModelScope),
        llmMaxOutputTokens     = appPreferences.TRANSLATION_MAX_OUTPUT_TOKENS.state(viewModelScope),
        translationNovelPrompts = appPreferences.TRANSLATION_NOVEL_PROMPTS.state(viewModelScope),
        autoBackupEnabled = appPreferences.BACKUP_AUTO_ENABLED.state(viewModelScope),
        autoBackupDirectoryUri = appPreferences.BACKUP_AUTO_DIRECTORY_URI.state(viewModelScope),
        autoBackupDirectoryDisplayName = mutableStateOf(
            resolveDirectoryName(appPreferences.BACKUP_AUTO_DIRECTORY_URI.value)
        ),
        autoBackupMaxCount = appPreferences.BACKUP_AUTO_MAX_COUNT.state(viewModelScope),
        autoBackupIntervalMinutes = appPreferences.BACKUP_AUTO_INTERVAL_MINUTES.state(viewModelScope),
        autoBackupIncludeImages = appPreferences.BACKUP_AUTO_INCLUDE_IMAGES.state(viewModelScope),
        autoBackupIncludeSettings = appPreferences.BACKUP_AUTO_INCLUDE_SETTINGS.state(viewModelScope),
        autoBackupIncludePlugins = appPreferences.BACKUP_AUTO_INCLUDE_PLUGINS.state(viewModelScope),
        autoBackupLastTimestamp = appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.state(viewModelScope),
        chapterCacheSize = mutableStateOf("…"),
        isCleaningChapterCache = isCleaningChapterCache,
        cleanConfirmationType = mutableStateOf(null),
    )

    init {
        updateDatabaseSize()
        updateImagesFolderSize()
        updateChapterCacheSize()
        viewModelScope.launch {
            appRepository.eventDataRestored.collect {
                updateDatabaseSize()
                updateImagesFolderSize()
            }
        }

        // Show notification when Cloudflare bypass setting changes
        viewModelScope.launch {
            var previousValue = cloudflareBypassEnabled
            appPreferences.CLOUDFLARE_BYPASS_ENABLED.flow().collect { newValue ->
                if (newValue != previousValue) {
                    toasty.show(R.string.cloudflare_bypass_restart_required)
                    previousValue = newValue
                }
            }
        }

    }

    fun requestCleanDatabase() {
        state.cleanConfirmationType.value = CleanConfirmationType.DATABASE
    }

    private fun cleanDatabase() = appScope.launch(Dispatchers.IO) {
        if (isCleaningDatabase.value) return@launch

        try {
            isCleaningDatabase.value = true
            toasty.show(R.string.cleaning_database)

            appRepository.settings.clearNonLibraryData()
            appRepository.vacuum()
            updateDatabaseSizeAndWait()
            kotlinx.coroutines.delay(500)

            toasty.show(R.string.database_cleaned_successfully)

        } catch (e: Exception) {
            toasty.show(R.string.database_clean_failed)
            Timber.e(e)
        } finally {
            isCleaningDatabase.value = false
        }
    }

    private suspend fun updateDatabaseSizeAndWait() {
        val size = appRepository.getDatabaseSizeBytes()
        withContext(Dispatchers.Main) {
            state.databaseSize.value = Formatter.formatFileSize(appPreferences.context, size)
        }
    }

    fun requestCleanImageFolder() {
        state.cleanConfirmationType.value = CleanConfirmationType.IMAGES_FOLDER
    }

    private fun cleanImagesFolder() = appScope.launch(Dispatchers.IO) {
        if (isCleaningImages.value) return@launch

        try {
            isCleaningImages.value = true
            toasty.show(R.string.cleaning_images_folder)

            val libraryBooks = appRepository.libraryBooks.getAllInLibrary()
            val libraryFolderNames = libraryBooks.asSequence()
                .map { appFileResolver.getLocalBookFolderName(it.url) }
                .toSet()

            val booksFolder = appRepository.settings.folderBooks

            // Self-heal: delete corrupt/non-image cover files so they are re-downloaded
            // on the next library update / cover backfill instead of staying broken forever.
            booksFolder.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && it.name in libraryFolderNames }
                ?.forEach { folder ->
                    val cover = File(folder, AppFileResolver.COVER_PATH_RELATIVE_TO_BOOK)
                    if (cover.exists() && !isCoverValid(cover)) {
                        Timber.d("cleanImagesFolder: deleting corrupt cover in ${folder.name}")
                        cover.delete()
                    }
                }

            val foldersToDelete = booksFolder.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && it.exists() }
                ?.filter { it.name !in libraryFolderNames }
                ?.toList() ?: emptyList()

            var deletedCount = 0
            foldersToDelete.forEach { folder ->
                try {
                    folder.deleteRecursively()
                    deletedCount++
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }

            context.cacheDir.resolve("image_cache").deleteRecursively()
            withContext(Dispatchers.Main) {
                coil.Coil.imageLoader(context).memoryCache?.clear()
            }

            updateImagesFolderSizeAndWait()
            kotlinx.coroutines.delay(500)

            if (deletedCount > 0) {
                toasty.show(context.getString(R.string.images_folder_cleaned, deletedCount))
            } else {
                toasty.show(R.string.images_folder_already_clean)
            }

        } catch (e: Exception) {
            toasty.show(R.string.images_folder_clean_failed)
            Timber.e(e)
        } finally {
            isCleaningImages.value = false
        }
    }

    fun onAppThemeChange(appTheme: AppTheme) {
        appPreferences.APP_THEME.value = appTheme.name
    }

    fun onDarkModeChange(darkMode: DarkMode) {
        appPreferences.THEME_DARK_MODE.value = darkMode.name
    }

    fun onLanguageChange(language: AppLanguage) {
        appPreferences.APP_LANGUAGE_CODE.value = language.code
        toasty.show(context.getString(R.string.language_changed_to, language.getDisplayName()))
        onRestartApp?.invoke()
    }

    fun onGooglePaApiKeysChange(keys: String) {
        appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value = keys
        appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value = ""
        appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value = 0L
    }

    fun onGeminiApiKeyChange(apiKey: String) {
        appPreferences.TRANSLATION_GEMINI_API_KEY.value = apiKey
    }

    fun onGeminiModelChange(model: String) {
        appPreferences.TRANSLATION_GEMINI_MODEL.value = model
    }

    fun onOpenAiBaseUrlChange(url: String) {
        appPreferences.TRANSLATION_OPENAI_BASE_URL.value = url
    }

    fun onOpenAiApiKeysChange(keys: String) {
        appPreferences.TRANSLATION_OPENAI_API_KEYS.value = keys
    }

    fun onOpenAiModelChange(model: String) {
        appPreferences.TRANSLATION_OPENAI_MODEL.value = model
    }

    fun onActiveSystemPromptChange(prompt: String) {
        appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value = prompt
    }

    fun onPromptUseEnglishLocaleChange(useEnglish: Boolean) {
        appPreferences.TRANSLATION_PROMPT_USE_ENGLISH_LOCALE.value = useEnglish
    }

    fun onLlmBatchSizeChange(size: Int) {
        appPreferences.TRANSLATION_BATCH_SIZE.value = size.coerceAtLeast(1)
    }

    fun onLlmMaxOutputTokensChange(tokens: Int) {
        appPreferences.TRANSLATION_MAX_OUTPUT_TOKENS.value = tokens.coerceAtLeast(0)
    }

    fun onSavePromptPreset(name: String, prompt: String) {
        val current = appPreferences.TRANSLATION_PROMPT_PRESETS.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.first == name }
        if (existingIndex != -1) {
            current[existingIndex] = name to prompt
        } else {
            current.add(name to prompt)
        }
        appPreferences.TRANSLATION_PROMPT_PRESETS.value = current
    }

    fun onDeletePromptPreset(name: String) {
        val current = appPreferences.TRANSLATION_PROMPT_PRESETS.value.toMutableList()
        current.removeAll { it.first == name }
        appPreferences.TRANSLATION_PROMPT_PRESETS.value = current
    }

    fun onPreferOnlineTranslationChange(prefer: Boolean) {
        appPreferences.TRANSLATION_PREFER_ONLINE.value = prefer
    }
    fun onTranslationProviderChange(provider: String) {
        appPreferences.TRANSLATION_PROVIDER.value = provider
    }

    fun onDeleteNovelPrompt(bookUrl: String) {
        val current = appPreferences.TRANSLATION_NOVEL_PROMPTS.value.toMutableMap()
        current.remove(bookUrl)
        appPreferences.TRANSLATION_NOVEL_PROMPTS.value = current
    }

    private fun updateDatabaseSize() = viewModelScope.launch {
        updateDatabaseSizeAndWait()
    }

    private suspend fun updateImagesFolderSizeAndWait() {
        val size = getFolderSizeBytes(appRepository.settings.folderBooks)
        withContext(Dispatchers.Main) {
            state.imageFolderSize.value = Formatter.formatFileSize(appPreferences.context, size)
        }
    }

    private fun updateImagesFolderSize() = viewModelScope.launch {
        updateImagesFolderSizeAndWait()
    }

    private fun updateChapterCacheSize() = viewModelScope.launch {
        val size = appRepository.settings.getChapterCacheSizeBytes()
        withContext(Dispatchers.Main) {
            state.chapterCacheSize.value = Formatter.formatFileSize(appPreferences.context, size)
        }
    }

    fun requestCleanChapterCache() {
        state.cleanConfirmationType.value = CleanConfirmationType.CHAPTER_CACHE
    }

    private fun cleanChapterCache() = appScope.launch(Dispatchers.IO) {
        if (isCleaningChapterCache.value) return@launch

        try {
            isCleaningChapterCache.value = true
            toasty.show(R.string.cleaning_chapter_cache)

            appRepository.settings.clearChapterCache()
            updateChapterCacheSize()
            kotlinx.coroutines.delay(500)

            toasty.show(R.string.chapter_cache_cleaned)
        } catch (e: Exception) {
            toasty.show(R.string.chapter_cache_clean_failed)
            Timber.e(e)
        } finally {
            isCleaningChapterCache.value = false
        }
    }

    fun dumpDebugInfo() = appScope.launch(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            MemoryDiagnostics.logMemoryStats("SettingsViewModel")
            toasty.show("Memory stats logged to logcat")
        }
    }

    fun confirmCleanAction() {
        when (state.cleanConfirmationType.value) {
            CleanConfirmationType.DATABASE -> cleanDatabase()
            CleanConfirmationType.IMAGES_FOLDER -> cleanImagesFolder()
            CleanConfirmationType.CHAPTER_CACHE -> cleanChapterCache()
            null -> return
        }
        state.cleanConfirmationType.value = null
    }

    fun dismissCleanAction() {
        state.cleanConfirmationType.value = null
    }

    fun onCheckForUpdatesManual() {
        viewModelScope.launch {
            state.updateAppSetting.checkingForNewVersion.value = true
            val current = appRemoteRepository.getCurrentAppVersion()
            appRemoteRepository.getLastAppVersion()
                .onSuccess { new ->
                    if (new.version > current) {
                        state.updateAppSetting.showNewVersionDialog.value = new
                    } else {
                        toasty.show(R.string.you_already_have_the_last_version)
                    }
                }.onError {
                    toasty.show(R.string.failed_to_check_last_app_version)
                }
            state.updateAppSetting.checkingForNewVersion.value = false
        }
    }

    /**
     * Refresh all size displays (database, images, chapter cache).
     * Called every time the settings screen becomes visible.
     */
    fun refreshSizes() {
        updateDatabaseSize()
        updateImagesFolderSize()
        updateChapterCacheSize()
    }

    fun onMassAddDelayChange(newDelayMs: Long) {
        appPreferences.MASS_ADD_DELAY_MS.value = newDelayMs
    }

    fun onDownloadDelayChange(newDelayMs: Long) {
        appPreferences.DOWNLOAD_DELAY_MS.value = newDelayMs
    }

    private suspend fun getFolderSizeBytes(file: File): Long = withContext(Dispatchers.IO) {
        fun dirSize(dir: File): Long {
            if (!dir.exists()) return 0L
            var total = 0L
            dir.walk().forEach { if (it.isFile) total += it.length() }
            return total
        }
        val booksSize = dirSize(file)
        val coilCacheSize = dirSize(context.cacheDir.resolve("image_cache"))
        booksSize + coilCacheSize
    }

    // ── Auto Backup Methods ─────────────────────────────────────────────────

    private fun resolveDirectoryName(uriString: String): String {
        if (uriString.isEmpty()) return ""
        return try {
            val treeUri = Uri.parse(uriString)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            // Try to get display name from the tree
            val cursor = context.contentResolver.query(
                docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(0) ?: ""
                } else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun updateDirectoryName(uri: String) {
        val displayName = resolveDirectoryName(uri)
        (state.autoBackupDirectoryDisplayName as? MutableState<String>)?.value = displayName
    }

    fun onAutoBackupEnabledChange(enabled: Boolean) {
        Timber.d( "onAutoBackupEnabledChange: enabled=$enabled")
        if (enabled) {
            val uri = appPreferences.BACKUP_AUTO_DIRECTORY_URI.value
            Timber.d( "onAutoBackupEnabledChange: directoryUri='$uri'")
            if (uri.isEmpty()) {
                toasty.show(R.string.auto_backup_select_directory_first)
                return
            }
            // Check write permission by attempting to create a test file
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val treeUri = Uri.parse(uri)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    val testFileName = ".backup_permission_test_${System.currentTimeMillis()}"
                    val createdUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        docUri,
                        "application/octet-stream",
                        testFileName
                    )
                    if (createdUri != null) {
                        DocumentsContract.deleteDocument(context.contentResolver, createdUri)
                        Timber.d( "onAutoBackupEnabledChange: permission OK, enabling")
                        appPreferences.BACKUP_AUTO_ENABLED.value = true
                        Timber.d( "onAutoBackupEnabledChange: calling runAutoBackupNow")
                        appWorkersInteractions.runAutoBackupNow()
                        withContext(Dispatchers.Main) {
                            toasty.show(R.string.auto_backup_enabled)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            toasty.show(R.string.auto_backup_no_permission)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Permission check failed")
                    withContext(Dispatchers.Main) {
                        toasty.show(R.string.auto_backup_no_permission)
                    }
                }
            }
        } else {
            appPreferences.BACKUP_AUTO_ENABLED.value = false
            appWorkersInteractions.cancelAutoBackup()
            toasty.show(R.string.auto_backup_disabled)
        }
    }

    fun onAutoBackupDirectoryUriChange(uri: String) {
        appPreferences.BACKUP_AUTO_DIRECTORY_URI.value = uri
        updateDirectoryName(uri)
    }

    fun onAutoBackupMaxCountChange(count: Int) {
        appPreferences.BACKUP_AUTO_MAX_COUNT.value = count.coerceIn(1, 50)
    }

    fun onAutoBackupIntervalMinutesChange(minutes: Long) {
        Timber.d( "onAutoBackupIntervalMinutesChange: minutes=$minutes")
        appPreferences.BACKUP_AUTO_INTERVAL_MINUTES.value = minutes.coerceAtLeast(60L)
        if (appPreferences.BACKUP_AUTO_ENABLED.value) {
            Timber.d( "onAutoBackupIntervalMinutesChange: auto backup enabled, scheduling")
            appWorkersInteractions.scheduleAutoBackup(minutes)
            viewModelScope.launch {
                withContext(Dispatchers.Main) {
                    toasty.show(R.string.auto_backup_interval_updated)
                }
            }
        }
    }

    fun onAutoBackupIncludeImagesChange(include: Boolean) {
        appPreferences.BACKUP_AUTO_INCLUDE_IMAGES.value = include
    }

    fun onAutoBackupIncludeSettingsChange(include: Boolean) {
        appPreferences.BACKUP_AUTO_INCLUDE_SETTINGS.value = include
    }

    fun onAutoBackupIncludePluginsChange(include: Boolean) {
        appPreferences.BACKUP_AUTO_INCLUDE_PLUGINS.value = include
    }
}