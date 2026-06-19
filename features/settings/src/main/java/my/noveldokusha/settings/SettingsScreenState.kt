package my.noveldokusha.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import my.noveldokusha.core.domain.RemoteAppVersion
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.core.appPreferences.AppLanguage

data class SettingsScreenState(
    val databaseSize: MutableState<String>,
    val imageFolderSize: MutableState<String>,
    val isCleaningDatabase: State<Boolean>,
    val isCleaningImages: State<Boolean>,
    val currentAppTheme: State<AppTheme>,
    val currentDarkMode: State<DarkMode>,
    val currentLanguage: State<AppLanguage>,
    val updateAppSetting: UpdateApp,
    val libraryAutoUpdate: LibraryAutoUpdate,
    val massAddDelayMs: State<Long>,
    val downloadDelayMs: State<Long>,
    val geminiApiKey: State<String>,
    val geminiModel: State<String>,
    val translationProvider: State<String>,
    val googlePaApiKeys: State<String>,
    val scraperUserAgent: MutableState<String>,
    val cloudflareBypassEnabled: MutableState<Boolean>,
    val cloudflareChallengeTimeoutSeconds: MutableState<Int>,
    // OpenAI-compatible
    val openAiBaseUrl: State<String>,
    val openAiApiKeys: State<String>,
    val openAiModel: State<String>,
    // Unified prompt manager (Gemini + OpenAI)
    val activeSystemPrompt: State<String>,
    val promptPresets: State<List<Pair<String, String>>>,
    val promptUseEnglishLocale: State<Boolean>,
    // LLM batch / token settings (Gemini + OpenAI only)
    val llmBatchSize: State<Int>,
    val llmMaxOutputTokens: State<Int>,
    // Auto Backup
    val autoBackupEnabled: MutableState<Boolean>,
    val autoBackupDirectoryUri: State<String>,
    val autoBackupDirectoryDisplayName: State<String>,
    val autoBackupMaxCount: State<Int>,
    val autoBackupIntervalMinutes: State<Long>,
    val autoBackupIncludeImages: State<Boolean>,
    val autoBackupLastTimestamp: State<Long>,
    // Chapter cache
    val chapterCacheSize: MutableState<String>,
    val isCleaningChapterCache: State<Boolean>,
) {
    data class UpdateApp(
        val currentAppVersion: String,
        val appUpdateCheckerEnabled: MutableState<Boolean>,
        val showNewVersionDialog: MutableState<RemoteAppVersion?>,
        val checkingForNewVersion: MutableState<Boolean>,
    )

    data class LibraryAutoUpdate(
        val autoUpdateEnabled: MutableState<Boolean>,
        val autoUpdateIntervalHours: MutableState<Int>,
    )
}