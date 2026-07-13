package my.noveldokusha.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons

import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.core.appPreferences.AppLanguageProvider
import my.noveldokusha.core.appPreferences.NovelPromptData
import my.noveldokusha.settings.sections.AppUpdates
import my.noveldokusha.settings.sections.LibraryAutoUpdate
import my.noveldokusha.settings.sections.SettingsBackup
import my.noveldokusha.settings.sections.SettingsData
import my.noveldokusha.settings.sections.SettingsGeminiTranslation
import my.noveldokusha.settings.sections.SettingsLanguage
import my.noveldokusha.settings.sections.SettingsNovelPromptsDialog
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
    onRequestCleanDatabase: () -> Unit,
    onRequestCleanImageFolder: () -> Unit,
    onRequestCleanChapterCache: () -> Unit,
    onConfirmClean: () -> Unit,
    onDismissClean: () -> Unit,
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
    onDeleteNovelPrompt: (String) -> Unit,
    // Auto Backup
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    onAutoBackupSelectDirectory: () -> Unit,
    onAutoBackupMaxCountChange: (Int) -> Unit,
    onAutoBackupIntervalMinutesChange: (Long) -> Unit,
    onAutoBackupIncludeImagesChange: (Boolean) -> Unit,
    onAutoBackupIncludeSettingsChange: (Boolean) -> Unit,
    onAutoBackupIncludePluginsChange: (Boolean) -> Unit,
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

    val showNovelPromptsDialog = remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        val currentLanguageObj = remember(state.currentLanguage.value) {
            AppLanguageProvider.fromCode(state.currentLanguage.value)
                ?: AppLanguageProvider.supportedLanguages.first()
        }
        SettingsLanguage(
            currentLanguage = currentLanguageObj,
            onLanguageChange = onLanguageChange,
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
            onRequestCleanDatabase = onRequestCleanDatabase,
            onRequestCleanImageFolder = onRequestCleanImageFolder,
            onRequestCleanChapterCache = onRequestCleanChapterCache,
        )
        HorizontalDivider()
        val context = LocalContext.current
        SettingsNetwork(
            context = context,
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
            autoBackupIncludeSettings = state.autoBackupIncludeSettings.value,
            onAutoBackupIncludeSettingsChange = onAutoBackupIncludeSettingsChange,
            autoBackupIncludePlugins = state.autoBackupIncludePlugins.value,
            onAutoBackupIncludePluginsChange = onAutoBackupIncludePluginsChange,
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
                novelPromptCount               = state.translationNovelPrompts.value.size,
                onNovelPromptsClick            = { showNovelPromptsDialog.value = true },
            )
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

    if (showNovelPromptsDialog.value) {
        SettingsNovelPromptsDialog(
            novelPrompts = state.translationNovelPrompts.value,
            onDeleteNovelPrompt = onDeleteNovelPrompt,
            onDismiss = { showNovelPromptsDialog.value = false },
        )
    }

    val confirmationType = state.cleanConfirmationType.value
    if (confirmationType != null) {
        val titleRes = when (confirmationType) {
            CleanConfirmationType.DATABASE -> R.string.clean_database
            CleanConfirmationType.IMAGES_FOLDER -> R.string.clean_images_folder
            CleanConfirmationType.CHAPTER_CACHE -> R.string.clean_chapter_cache
        }
        val textRes = when (confirmationType) {
            CleanConfirmationType.DATABASE -> R.string.clean_database_confirmation
            CleanConfirmationType.IMAGES_FOLDER -> R.string.clean_images_folder_confirmation
            CleanConfirmationType.CHAPTER_CACHE -> R.string.clean_chapter_cache_confirmation
        }
        AlertDialog(
            onDismissRequest = onDismissClean,
            title = {
                Text(
                    text = stringResource(id = titleRes),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(id = textRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = onConfirmClean,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismissClean,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
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
                    currentLanguage = remember { mutableStateOf("en") },
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
                    autoBackupIncludeSettings = remember { derivedStateOf { true } },
                    autoBackupIncludePlugins = remember { derivedStateOf { true } },
                    autoBackupLastTimestamp = remember { derivedStateOf { 0L } },
                    translationNovelPrompts = remember { derivedStateOf { emptyMap<String, NovelPromptData>() } },
                    cleanConfirmationType = remember { mutableStateOf(null) },
                ),
                onRefreshSizes = { },
                onRequestCleanDatabase = { },
                onRequestCleanImageFolder = { },
                onRequestCleanChapterCache = { },
                onConfirmClean = { },
                onDismissClean = { },
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
                    onAutoBackupIncludeSettingsChange = { },
                    onAutoBackupIncludePluginsChange = { },
                    onDeleteNovelPrompt = { },
            )
        }
    }
}