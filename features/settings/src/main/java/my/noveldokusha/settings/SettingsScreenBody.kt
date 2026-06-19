package my.noveldokusha.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.settings.sections.AppUpdates
import my.noveldokusha.settings.sections.LibraryAutoUpdate
import my.noveldokusha.settings.sections.SettingsBackup
import my.noveldokusha.settings.sections.SettingsData
import my.noveldokusha.settings.sections.SettingsGeminiTranslation
import my.noveldokusha.settings.sections.SettingsLanguage
import my.noveldokusha.settings.sections.SettingsNetwork
import my.noveldokusha.settings.sections.SettingsTheme
import my.noveldokusha.settings.sections.SettingsRegexCleanup

@Composable
internal fun SettingsScreenBody(
    state: SettingsScreenState,
    modifier: Modifier = Modifier,
    onRefreshSizes: () -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onDarkModeSelected: (DarkMode) -> Unit,
    onCleanDatabase: () -> Unit,
    onCleanImageFolder: () -> Unit,
    onCleanChapterCache: () -> Unit,
    onMassAddDelayChange: (Long) -> Unit,
    onDownloadDelayChange: (Long) -> Unit,
    onBackupData: () -> Unit,
    onRestoreData: () -> Unit,
    onCheckForUpdatesManual: () -> Unit,
    onGeminiApiKeyChange: (String) -> Unit,
    onGeminiModelChange: (String) -> Unit,
    onTranslationProviderChange: (String) -> Unit,
    onGooglePaApiKeysChange: (String) -> Unit,
    onOpenAiBaseUrlChange: (String) -> Unit,
    onOpenAiApiKeysChange: (String) -> Unit,
    onOpenAiModelChange: (String) -> Unit,
    onActiveSystemPromptChange: (String) -> Unit,
    onPromptUseEnglishLocaleChange: (Boolean) -> Unit,
    onSavePreset: (name: String, prompt: String) -> Unit,
    onDeletePreset: (name: String) -> Unit,
    onLlmBatchSizeChange: (Int) -> Unit,
    onLlmMaxOutputTokensChange: (Int) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onNavigateToRegexCleanup: () -> Unit,
    // Auto Backup
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    onAutoBackupSelectDirectory: () -> Unit,
    onAutoBackupMaxCountChange: (Int) -> Unit,
    onAutoBackupIntervalMinutesChange: (Long) -> Unit,
    onAutoBackupIncludeImagesChange: (Boolean) -> Unit,
) {
    // Refresh size displays every time the user navigates to this screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshSizes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SettingsLanguage(
            currentLanguage = state.currentLanguage.value,
            onLanguageChange = onLanguageChange
        )
        HorizontalDivider()
        SettingsTheme(
            currentAppTheme = state.currentAppTheme.value,
            currentDarkMode = state.currentDarkMode.value,
            onAppThemeChange = onAppThemeSelected,
            onDarkModeChange = onDarkModeSelected,
        )
        HorizontalDivider()
        SettingsData(
            databaseSize = state.databaseSize.value,
            imagesFolderSize = state.imageFolderSize.value,
            chapterCacheSize = state.chapterCacheSize.value,
            isCleaningDatabase = state.isCleaningDatabase.value,
            isCleaningImages = state.isCleaningImages.value,
            isCleaningChapterCache = state.isCleaningChapterCache.value,
            onCleanDatabase = onCleanDatabase,
            onCleanImageFolder = onCleanImageFolder,
            onCleanChapterCache = onCleanChapterCache,
        )
        HorizontalDivider()
        SettingsNetwork(
            scraperUserAgent = state.scraperUserAgent,
            cloudflareBypassEnabled = state.cloudflareBypassEnabled,
            cloudflareChallengeTimeoutSeconds = state.cloudflareChallengeTimeoutSeconds,
            massAddDelayMs = state.massAddDelayMs,
            onMassAddDelayChange = onMassAddDelayChange,
            downloadDelayMs = state.downloadDelayMs,
            onDownloadDelayChange = onDownloadDelayChange
        )
        HorizontalDivider()
        SettingsBackup(
            onBackupData = onBackupData,
            onRestoreData = onRestoreData,
            autoBackupEnabled = state.autoBackupEnabled.value,
            onAutoBackupEnabledChange = onAutoBackupEnabledChange,
            autoBackupDirectoryUri = state.autoBackupDirectoryUri.value,
            autoBackupDirectoryDisplayName = state.autoBackupDirectoryDisplayName.value,
            onAutoBackupSelectDirectory = onAutoBackupSelectDirectory,
            autoBackupMaxCount = state.autoBackupMaxCount.value,
            onAutoBackupMaxCountChange = onAutoBackupMaxCountChange,
            autoBackupIntervalMinutes = state.autoBackupIntervalMinutes.value,
            onAutoBackupIntervalMinutesChange = onAutoBackupIntervalMinutesChange,
            autoBackupIncludeImages = state.autoBackupIncludeImages.value,
            onAutoBackupIncludeImagesChange = onAutoBackupIncludeImagesChange,
            autoBackupLastTimestamp = state.autoBackupLastTimestamp.value,
        )
        SettingsGeminiTranslation(
                translationProvider            = state.translationProvider.value,
                geminiApiKey                   = state.geminiApiKey.value,
                geminiModel                    = state.geminiModel.value,
                googlePaApiKeys                = state.googlePaApiKeys.value,
                openAiBaseUrl                  = state.openAiBaseUrl.value,
                openAiApiKeys                  = state.openAiApiKeys.value,
                openAiModel                    = state.openAiModel.value,
                activeSystemPrompt             = state.activeSystemPrompt.value,
                promptPresets                  = state.promptPresets.value,
                promptUseEnglishLocale         = state.promptUseEnglishLocale.value,
                onTranslationProviderChange    = onTranslationProviderChange,
                onGeminiApiKeyChange           = onGeminiApiKeyChange,
                onGeminiModelChange            = onGeminiModelChange,
                onGooglePaApiKeysChange        = onGooglePaApiKeysChange,
                onOpenAiBaseUrlChange          = onOpenAiBaseUrlChange,
                onOpenAiApiKeysChange          = onOpenAiApiKeysChange,
                onOpenAiModelChange            = onOpenAiModelChange,
                onActiveSystemPromptChange     = onActiveSystemPromptChange,
                onPromptUseEnglishLocaleChange = onPromptUseEnglishLocaleChange,
                onSavePreset                   = onSavePreset,
                onDeletePreset                 = onDeletePreset,
                llmBatchSize                   = state.llmBatchSize.value,
                llmMaxOutputTokens             = state.llmMaxOutputTokens.value,
                onLlmBatchSizeChange           = onLlmBatchSizeChange,
                onLlmMaxOutputTokensChange     = onLlmMaxOutputTokensChange,
            )
        HorizontalDivider()
        SettingsRegexCleanup(
            onNavigateToRegexCleanup = onNavigateToRegexCleanup
        )
        HorizontalDivider()
        LibraryAutoUpdate(state = state.libraryAutoUpdate)
        HorizontalDivider()
        AppUpdates(
            state = state.updateAppSetting,
            onCheckForUpdatesManual = onCheckForUpdatesManual
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "(°.°)",
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(120.dp))
    }
}


@PreviewThemes
@Composable
private fun Preview() {
    val isDark = isSystemInDarkTheme()
    InternalTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreenBody(
                state = SettingsScreenState(
                    currentAppTheme = remember { mutableStateOf(AppTheme.DEFAULT) },
                    currentDarkMode = remember { mutableStateOf(DarkMode.SYSTEM) },
                    currentLanguage = remember { derivedStateOf { AppLanguage.ENGLISH } },
                    databaseSize = remember { mutableStateOf("1 MB") },
                    imageFolderSize = remember { mutableStateOf("10 MB") },
                    isCleaningDatabase = remember { mutableStateOf(false) },
                    isCleaningImages = remember { mutableStateOf(false) },
                    chapterCacheSize = remember { mutableStateOf("5 MB") },
                    isCleaningChapterCache = remember { mutableStateOf(false) },
                    updateAppSetting = SettingsScreenState.UpdateApp(
                        currentAppVersion = "1.0.0",
                        appUpdateCheckerEnabled = remember { mutableStateOf(true) },
                        showNewVersionDialog = remember { mutableStateOf(null) },
                        checkingForNewVersion = remember { mutableStateOf(true) },
                    ),
                    libraryAutoUpdate = SettingsScreenState.LibraryAutoUpdate(
                        autoUpdateEnabled = remember { mutableStateOf(true) },
                        autoUpdateIntervalHours = remember { mutableIntStateOf(24) },
                    ),
                    massAddDelayMs = remember { derivedStateOf { 2000L } },
                    downloadDelayMs = remember { derivedStateOf { 2000L } },
                    geminiApiKey = remember { derivedStateOf { "" } },
                    geminiModel = remember { derivedStateOf { "" } },
                    translationProvider = remember { mutableStateOf("GOOGLE_PA") },
                    googlePaApiKeys = remember { derivedStateOf { "" } },
                    scraperUserAgent = remember { mutableStateOf("") },
                    cloudflareBypassEnabled = remember { mutableStateOf(true) },
                    cloudflareChallengeTimeoutSeconds = remember { mutableStateOf(120) },
                    openAiBaseUrl = remember { derivedStateOf { "" } },
                    openAiApiKeys = remember { derivedStateOf { "" } },
                    openAiModel = remember { derivedStateOf { "gpt-4o-mini" } },
                    activeSystemPrompt = remember { derivedStateOf { "" } },
                    promptPresets = remember { derivedStateOf { emptyList<Pair<String, String>>() } },
                    promptUseEnglishLocale = remember { derivedStateOf { true } },
                    llmBatchSize = remember { derivedStateOf { 60 } },
                    llmMaxOutputTokens = remember { derivedStateOf { 0 } },
                    autoBackupEnabled = remember { mutableStateOf(false) },
                    autoBackupDirectoryUri = remember { derivedStateOf { "" } },
                    autoBackupDirectoryDisplayName = remember { mutableStateOf("") },
                    autoBackupMaxCount = remember { derivedStateOf { 5 } },
                    autoBackupIntervalMinutes = remember { derivedStateOf { 60L } },
                    autoBackupIncludeImages = remember { derivedStateOf { false } },
                    autoBackupLastTimestamp = remember { derivedStateOf { 0L } },
                ),
                onRefreshSizes = { },
                onCleanDatabase = { },
                onCleanImageFolder = { },
                onCleanChapterCache = { },
                onMassAddDelayChange = { },
                onDownloadDelayChange = { },
                onBackupData = { },
                onRestoreData = { },
                onCheckForUpdatesManual = { },
                onGeminiApiKeyChange = { },
                onGeminiModelChange = { },
                onTranslationProviderChange = { },
                onGooglePaApiKeysChange = { },
                onOpenAiBaseUrlChange = { },
                onOpenAiApiKeysChange = { },
                onOpenAiModelChange = { },
                onActiveSystemPromptChange = { },
                onPromptUseEnglishLocaleChange = { },
                onSavePreset = { _, _ -> },
                onDeletePreset = { },
                onLlmBatchSizeChange = { },
                onLlmMaxOutputTokensChange = { },
                    onLanguageChange = { },
                    onAppThemeSelected = { },
                    onDarkModeSelected = { },
                    onNavigateToRegexCleanup = { },
                    onAutoBackupEnabledChange = { },
                    onAutoBackupSelectDirectory = { },
                    onAutoBackupMaxCountChange = { },
                    onAutoBackupIntervalMinutesChange = { },
                    onAutoBackupIncludeImagesChange = { },
            )
        }
    }
}