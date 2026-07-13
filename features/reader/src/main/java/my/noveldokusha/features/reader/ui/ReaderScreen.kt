package my.noveldokusha.features.reader.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.rememberMutableStateOf
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.features.LiveTranslationSettingData
import my.noveldokusha.features.reader.features.TextSynthesis
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldokusha.features.reader.ui.ReaderScreenState.Settings.Type
import my.noveldokusha.reader.R
import my.noveldokusha.text_to_speech.Utterance
import my.noveldokusha.text_to_speech.VoiceData
import my.noveldokusha.features.reader.services.FloatingTtsService
import my.noveldokusha.text_translator.domain.TranslationModelState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    state: ReaderScreenState,
    onSelectableTextChange: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onDarkModeSelected: (DarkMode) -> Unit,
    onAppThemeChanged: (AppTheme) -> Unit,
    onFullScreen: (Boolean) -> Unit,
    onSingleTapToOpenSettingsChange: (Boolean) -> Unit,
    onTextFontChanged: (String) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onParagraphSpacingChanged: (Float) -> Unit,
    onPressBack: () -> Unit,
    onOpenChapterInWeb: () -> Unit,
    readerContent: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    val showReaderInfo by state.showReaderInfo
    val selectedSetting by state.settings.selectedSetting
    val fullScreen by state.settings.fullScreen

    val context = LocalContext.current
    val density = LocalDensity.current
    val windowToken = LocalView.current.windowToken
    val navBarHeightDp = remember {
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) {
            context.resources.getDimensionPixelSize(id)
                .let { px -> with(density) { px.toDp() } }
        } else 0.dp
    }


    // Capture back action when viewing info
    BackHandler(enabled = showReaderInfo) {
        state.showReaderInfo.value = false
    }

    LaunchedEffect(showReaderInfo) {
        if (!showReaderInfo) {
            state.settings.selectedSetting.value = Type.None
        }
    }

    Box {
        Scaffold(
        topBar = {
            val fullScreen by rememberUpdatedState(showReaderInfo)
            AnimatedVisibility(
                visible = showReaderInfo,
                enter = expandVertically(initialHeight = { 0 }, expandFrom = Alignment.Top)
                        + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }, shrinkTowards = Alignment.Top)
                        + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                    modifier = Modifier.animateContentSize(),
                ) {
                    Column(
                        modifier = if (fullScreen) Modifier.displayCutoutPadding() else Modifier
                    ) {
                        val chapterTitle by state.readerInfo.chapterTitle

                        val toggleOrSet = { type: Type ->
                            state.settings.selectedSetting.value = if (selectedSetting == type) Type.None else type
                        }

                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                            ),
                            title = {
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.animateContentSize()
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onPressBack, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
                                }
                            },
                            actions = {
                                if (state.settings.liveTranslation.isAvailable) {
                                    IconButton(onClick = { toggleOrSet(Type.LiveTranslation) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Outlined.Translate, stringResource(R.string.translator), modifier = Modifier.size(20.dp), tint = if (selectedSetting == Type.LiveTranslation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                IconButton(onClick = { toggleOrSet(Type.TextToSpeech) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.RecordVoiceOver, stringResource(R.string.voice_reader), modifier = Modifier.size(20.dp), tint = if (selectedSetting == Type.TextToSpeech) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { toggleOrSet(Type.Style) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Outlined.ColorLens, stringResource(R.string.style), modifier = Modifier.size(20.dp), tint = if (selectedSetting == Type.Style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { toggleOrSet(Type.More) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Build, stringResource(R.string.more), modifier = Modifier.size(20.dp), tint = if (selectedSetting == Type.More) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = onOpenChapterInWeb, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Public, stringResource(R.string.open_in_browser), modifier = Modifier.size(20.dp))
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        content = readerContent,
        bottomBar = {
            AnimatedVisibility(
                visible = showReaderInfo,
                enter = expandVertically(initialHeight = { 0 }) + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }) + fadeOut(),
            ) {
                Column {
                    ReaderScreenBottomBarDialogs(
                        settings = state.settings,
                        onTextFontChanged = onTextFontChanged,
                        onTextSizeChanged = onTextSizeChanged,
                        onLineHeightChanged = onLineHeightChanged,
                        onParagraphSpacingChanged = onParagraphSpacingChanged,
                        onSelectableTextChange = onSelectableTextChange,
                        onDarkModeSelected = onDarkModeSelected,
                        onAppThemeSelected = onAppThemeChanged,
                        onKeepScreenOn = onKeepScreenOn,
                        onFullScreen = onFullScreen,
                        onSingleTapToOpenSettingsChange = onSingleTapToOpenSettingsChange,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BottomAppBar(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .animateContentSize(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                    ) {
                        val chapterCurrentNumber by state.readerInfo.chapterCurrentNumber
                        val chaptersCount by state.readerInfo.chaptersCount
                        val chapterPercentageProgress by state.readerInfo.chapterPercentageProgress
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.chapter_x_over_n,
                                    chapterCurrentNumber,
                                    chaptersCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.progress_x_percentage,
                                    chapterPercentageProgress
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
        )

        var showOverlayPermissionDialog by remember { mutableStateOf(false) }

        val overlayPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(context)) {
                my.noveldokusha.features.reader.services.FloatingTtsService.activityWindowToken =
                    windowToken
                my.noveldokusha.features.reader.services.FloatingTtsService.ttsState.value =
                    state.settings.textToSpeech
                my.noveldokusha.features.reader.services.FloatingTtsService.showOutsideApp.value =
                    state.settings.floatingTts.showOutsideApp.value
                my.noveldokusha.features.reader.services.FloatingTtsService.opacity.value =
                    state.settings.floatingTts.opacity.value
                my.noveldokusha.features.reader.services.FloatingTtsService.start(context)
            }
        }

        if (showOverlayPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayPermissionDialog = false },
                title = { Text(text = stringResource(R.string.tts_floating_overlay_permission_title)) },
                text = {
                    Text(text = stringResource(R.string.tts_floating_overlay_permission_message))
                },
                confirmButton = {
                    Button(onClick = {
                        showOverlayPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    }) {
                        Text(text = stringResource(R.string.tts_floating_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOverlayPermissionDialog = false }) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        LaunchedEffect(
            state.settings.floatingTts.isEnabled.value,
        ) {
            val floatingEnabled = state.settings.floatingTts.isEnabled.value
            if (floatingEnabled) {
                val hasPermission = Settings.canDrawOverlays(context)

                if (hasPermission) {
                    my.noveldokusha.features.reader.services.FloatingTtsService.activityWindowToken =
                        windowToken
                    my.noveldokusha.features.reader.services.FloatingTtsService.ttsState.value =
                        state.settings.textToSpeech
                    my.noveldokusha.features.reader.services.FloatingTtsService.showOutsideApp.value =
                        state.settings.floatingTts.showOutsideApp.value
                    my.noveldokusha.features.reader.services.FloatingTtsService.opacity.value =
                        state.settings.floatingTts.opacity.value
                    my.noveldokusha.features.reader.services.FloatingTtsService.start(context)
                } else {
                    showOverlayPermissionDialog = true
                    state.settings.floatingTts.isEnabled.value = false
                }
            } else {
                my.noveldokusha.features.reader.services.FloatingTtsService.stop(context)
            }
        }

        LaunchedEffect(
            state.settings.floatingTts.showOutsideApp.value,
            state.settings.floatingTts.opacity.value,
        ) {
            my.noveldokusha.features.reader.services.FloatingTtsService.showOutsideApp.value =
                state.settings.floatingTts.showOutsideApp.value
            my.noveldokusha.features.reader.services.FloatingTtsService.opacity.value =
                state.settings.floatingTts.opacity.value
        }

        LaunchedEffect(
            state.settings.floatingTts.showOutsideApp.value,
        ) {
            if (FloatingTtsService.isRunning(context)) {
                FloatingTtsService.activityWindowToken =
                    windowToken
                FloatingTtsService.recreateOverlay()
            }
        }

        val lifecycle = LocalLifecycleOwner.current.lifecycle

        DisposableEffect(
            lifecycle,
            state.settings.selectedSetting.value,
            state.settings.floatingTts.showOutsideApp.value,
        ) {
            val selectedSetting = state.settings.selectedSetting.value
            val showOutsideApp = state.settings.floatingTts.showOutsideApp.value

            val observer = LifecycleEventObserver { _, event ->
                if (!FloatingTtsService.isRunning(context)) return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        FloatingTtsService.setOverlayHidden(selectedSetting != Type.None)
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        FloatingTtsService.setOverlayHidden(!showOutsideApp)
                    }
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)

            if (FloatingTtsService.isRunning(context)) {
                FloatingTtsService.setOverlayHidden(selectedSetting != Type.None)
            }

            onDispose { lifecycle.removeObserver(observer) }
        }
    }
}

@Composable
private fun RowScope.SettingIconItem(
    currentType: Type,
    settingType: Type,
    @StringRes textId: Int,
    icon: ImageVector,
    onClick: (type: Type) -> Unit,
) {
    NavigationBarItem(
        selected = currentType == settingType,
        onClick = { onClick(settingType) },
        icon = { Icon(icon, null) },
        label = { Text(text = stringResource(id = textId)) }
    )
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ViewsPreview(
    @PreviewParameter(PreviewDataProvider::class) data: PreviewDataProvider.Data
) {

    val liveTranslationSettingData = LiveTranslationSettingData(
        isAvailable = true,
        enable = remember { mutableStateOf(true) },
        listOfAvailableModels = remember { mutableStateListOf() },
        source = remember {
            mutableStateOf(
                TranslationModelState(
                    language = "fr",
                    available = true,
                    downloading = false,
                    downloadingFailed = false
                )
            )
        },
        target = remember {
            mutableStateOf(
                TranslationModelState(
                    language = "en",
                    available = true,
                    downloading = false,
                    downloadingFailed = false
                )
            )
        },
        onTargetChange = {},
        onEnable = {},
        onSourceChange = {},
        onDownloadTranslationModel = {}
        , onRedoTranslation = {},
        novelPrompt = remember { mutableStateOf("") },
        onNovelPromptChange = {},
        novelPromptAppendMode = remember { mutableStateOf(false) },
        onNovelPromptAppendModeChange = {},
        currentProvider = remember { mutableStateOf("GOOGLE_PA") },
        onProviderChange = {},
        parallelEnabled = remember { mutableStateOf(false) },
        onParallelEnabledChange = {},
        parallelOrder = remember { mutableStateOf("ORIGINAL_FIRST") },
        onParallelOrderChange = {},
    )

    val textToSpeechSettingData = TextToSpeechSettingData(
        isPlaying = rememberMutableStateOf(false),
        isLoadingChapter = rememberMutableStateOf(false),
        voicePitch = rememberMutableStateOf(1f),
        voiceSpeed = rememberMutableStateOf(1f),
        availableVoices = remember { mutableStateListOf() },
        activeVoice = remember {
            mutableStateOf(
                VoiceData(
                    id = "",
                    language = "",
                    quality = 100,
                    needsInternet = true,
                    enginePackage = "",
                )
            )
        },
        currentActiveItemState = remember {
            mutableStateOf(
                TextSynthesis(
                    playState = Utterance.PlayState.PLAYING,
                    itemPos = ReaderItem.Title(
                        chapterUrl = "",
                        chapterIndex = 0,
                        chapterItemPosition = 1,
                        text = ""
                    )
                )
            )
        },
        isThereActiveItem = rememberMutableStateOf(true),
        setPlaying = {},
        playPreviousItem = {},
        playPreviousChapter = {},
        playNextItem = {},
        playNextChapter = {},
        setVoiceId = {},
        playFirstVisibleItem = {},
        scrollToActiveItem = {},
        setVoiceSpeed = {},
        setVoicePitch = {},
        setCustomSavedVoices = {},
        customSavedVoices = rememberMutableStateOf(value = listOf()),
        chapterWordCount = remember { mutableStateOf(0) },
        remainingWordCount = remember { mutableStateOf(0) },
        estimatedWpm = remember { mutableStateOf(0) },
        estimatedTotalSeconds = remember { mutableStateOf(0) },
        estimatedRemainingSeconds = remember { mutableStateOf(0) },
        currentParagraphText = remember { mutableStateOf("") },
        alternateParagraphText = remember { mutableStateOf("") },
        parallelEnabled = remember { mutableStateOf(false) },
        originalVoiceId = remember { mutableStateOf("") },
        setOriginalVoiceId = {},
    )

    val style = ReaderScreenState.Settings.StyleSettingsData(
        currentDarkMode = remember { mutableStateOf(DarkMode.DARK) },
        currentAppTheme = remember { mutableStateOf(AppTheme.DEFAULT) },
        textFont = remember { mutableStateOf("Arial") },
        textSize = remember { mutableFloatStateOf(20f) },
        lineHeight = remember { mutableFloatStateOf(1.35f) },
        paragraphSpacing = remember { mutableFloatStateOf(8f) },
    )

    InternalTheme {
        Surface(color = Color.Black) {
            ReaderScreen(
                state = ReaderScreenState(
                    showReaderInfo = remember { mutableStateOf(true) },
                    readerInfo = ReaderScreenState.CurrentInfo(
                        chapterTitle = remember { mutableStateOf("Chapter title") },
                        chapterCurrentNumber = remember { mutableIntStateOf(2) },
                        chapterPercentageProgress = remember { mutableFloatStateOf(0.5f) },
                        chaptersCount = remember { mutableIntStateOf(255) },
                        chapterUrl = remember { mutableStateOf("Chapter url") },
                    ),
                    settings = ReaderScreenState.Settings(
                        isTextSelectable = remember { mutableStateOf(false) },
                        keepScreenOn = remember { mutableStateOf(false) },
                        textToSpeech = textToSpeechSettingData,
                        liveTranslation = liveTranslationSettingData,
                        style = style,
                        selectedSetting = remember { mutableStateOf(data.selectedSetting) },
                        fullScreen = remember { mutableStateOf(false) },
                        isSingleTapToOpenSettings = remember { mutableStateOf(false) },
                        floatingTts = ReaderScreenState.Settings.FloatingTtsSettingsData(
                            isEnabled = remember { mutableStateOf(false) },
                            showOutsideApp = remember { mutableStateOf(true) },
                            opacity = remember { mutableFloatStateOf(0.6f) },
                        ),
                    ),
                    showInvalidChapterDialog = remember { mutableStateOf(false) }
                ),
                onTextSizeChanged = {},
                onLineHeightChanged = {},
                onParagraphSpacingChanged = {},
                onTextFontChanged = {},
                onSelectableTextChange = {},
                onDarkModeSelected = {},
                onAppThemeChanged = {},
                onPressBack = {},
                onOpenChapterInWeb = {},
                readerContent = {},
                onKeepScreenOn = {},
                onFullScreen = {},
                onSingleTapToOpenSettingsChange = {},
            )
        }
    }
}


private class PreviewDataProvider : PreviewParameterProvider<PreviewDataProvider.Data> {
    data class Data(
        val selectedSetting: Type
    )

    override val values = sequenceOf(
        Data(selectedSetting = Type.None),
        Data(selectedSetting = Type.LiveTranslation),
        Data(selectedSetting = Type.TextToSpeech),
        Data(selectedSetting = Type.Style),
        Data(selectedSetting = Type.More),
    )
}
