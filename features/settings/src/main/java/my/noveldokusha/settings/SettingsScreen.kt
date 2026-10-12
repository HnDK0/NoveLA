package my.noveldokusha.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import my.noveldokusha.coreui.theme.LocalAppTheme
import my.noveldokusha.coreui.theme.LocalIsDark
import my.noveldokusha.tooling.backup_create.onBackupCreate
import my.noveldokusha.tooling.backup_restore.onBackupRestore
import my.noveldokusha.navigation.NavigationRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigationRoutes: NavigationRoutes,
    onRestartApp: (() -> Unit)? = null,
    onOpenHomepageFeeds: (() -> Unit)? = null,
    onOpenSearchPriority: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel()
    viewModel.onRestartApp = onRestartApp

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
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    // Redesign appbar: real NoveLA logo + title (no filler copy)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = my.noveldokusha.coreui.R.drawable.ic_logo),
                            contentDescription = "NoveLA",
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(id = R.string.title_settings),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            )
        },
        content = { innerPadding ->
            SettingsScreenBody(
                state = viewModel.state,
                onRefreshSizes = viewModel::refreshSizes,
                onAppThemeSelected = viewModel::onAppThemeChange,
                onDarkModeSelected = viewModel::onDarkModeChange,
                onOpenHomepageFeeds = onOpenHomepageFeeds,
                onOpenSearchPriority = onOpenSearchPriority,
                onRequestCleanDatabase = viewModel::requestCleanDatabase,
                onRequestCleanImageFolder = viewModel::requestCleanImageFolder,
                onRequestCleanNovelCache = viewModel::requestCleanNovelCache,
                onRequestCleanMangaCache = viewModel::requestCleanMangaCache,
                onRequestCleanOfflineVideo = viewModel::requestCleanOfflineVideo,
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
                onTranslationGlobalModeChange = viewModel::onTranslationGlobalModeChange,
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
                    context.startActivity(navigationRoutes.regexRules(context, null))
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
                onExportLogs = viewModel::exportLogs,
                modifier = Modifier.padding(innerPadding),
            )
        }
    )
    }
}