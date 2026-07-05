package my.noveldokusha.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import my.noveldokusha.coreui.theme.LocalAppTheme
import my.noveldokusha.coreui.theme.LocalIsDark
import my.noveldokusha.tooling.backup_create.onBackupCreate
import my.noveldokusha.tooling.backup_restore.onBackupRestore
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRestartApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel()
    viewModel.onRestartApp = onRestartApp

    var currentScreen by remember { mutableStateOf("main") }

    // Auto backup directory picker
    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // Take persistable permission so we can access it later
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.onAutoBackupDirectoryUriChange(uri.toString())
            }
        }
    )

    val appTheme = LocalAppTheme.current
    val isDark = LocalIsDark.current

    androidx.compose.runtime.key(appTheme, isDark) {
        Scaffold(
            topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Text(
                        text = stringResource(id = R.string.title_settings),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            )
        },
        content = { innerPadding ->
            BackHandler(enabled = currentScreen == "regex-cleanup") {
                currentScreen = "main"
            }
            when (currentScreen) {
                "main" -> SettingsScreenBody(
                    state = viewModel.state,
                    onRefreshSizes = viewModel::refreshSizes,
                    onAppThemeSelected = viewModel::onAppThemeChange,
                    onDarkModeSelected = viewModel::onDarkModeChange,
                    onRequestCleanDatabase = viewModel::requestCleanDatabase,
                    onRequestCleanImageFolder = viewModel::requestCleanImageFolder,
                    onRequestCleanChapterCache = viewModel::requestCleanChapterCache,
                    onConfirmClean = viewModel::confirmCleanAction,
                    onDismissClean = viewModel::dismissCleanAction,
                    onMassAddDelayChange = viewModel::onMassAddDelayChange,
                    onDownloadDelayChange = viewModel::onDownloadDelayChange,
                    onBackupData = onBackupCreate(),
                    onRestoreData = onBackupRestore(),
                    onCheckForUpdatesManual = viewModel::onCheckForUpdatesManual,
                    onGeminiApiKeyChange = viewModel::onGeminiApiKeyChange,
                    onGeminiModelChange = viewModel::onGeminiModelChange,
                    onTranslationProviderChange = viewModel::onTranslationProviderChange,
                    onGooglePaApiKeysChange = viewModel::onGooglePaApiKeysChange,
                    onOpenAiBaseUrlChange = viewModel::onOpenAiBaseUrlChange,
                    onOpenAiApiKeysChange = viewModel::onOpenAiApiKeysChange,
                    onOpenAiModelChange = viewModel::onOpenAiModelChange,
                    onActiveSystemPromptChange = viewModel::onActiveSystemPromptChange,
                    onPromptUseEnglishLocaleChange = viewModel::onPromptUseEnglishLocaleChange,
                    onSavePreset = viewModel::onSavePromptPreset,
                    onDeletePreset = viewModel::onDeletePromptPreset,
                    onLlmBatchSizeChange = viewModel::onLlmBatchSizeChange,
                    onLlmMaxOutputTokensChange = viewModel::onLlmMaxOutputTokensChange,
                    onLanguageChange = viewModel::onLanguageChange,
                    onNavigateToRegexCleanup = {
                        currentScreen = "regex-cleanup"
                    },
                    onAutoBackupSelectDirectory = {
                        directoryPicker.launch(null)
                    },
                    onAutoBackupMaxCountChange = viewModel::onAutoBackupMaxCountChange,
                    onAutoBackupIntervalMinutesChange = viewModel::onAutoBackupIntervalMinutesChange,
                    onAutoBackupEnabledChange = viewModel::onAutoBackupEnabledChange,
                    onAutoBackupIncludeImagesChange = viewModel::onAutoBackupIncludeImagesChange,
                    onAutoBackupIncludeSettingsChange = viewModel::onAutoBackupIncludeSettingsChange,
                    onAutoBackupIncludePluginsChange = viewModel::onAutoBackupIncludePluginsChange,
                    onDeleteNovelPrompt = viewModel::onDeleteNovelPrompt,
                    modifier = Modifier.padding(innerPadding),
                )
                "regex-cleanup" -> {
                    val regexCleanupViewModel: RegexCleanupSettingsViewModel = viewModel()
                    RegexCleanupSettingsScreen(
                        viewModel = regexCleanupViewModel,
                        onNavigateBack = {
                            currentScreen = "main"
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    )
    }
}