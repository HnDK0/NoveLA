package my.noveldokusha.features.reader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.ui.settingDialogs.MoreSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.StyleSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.TranslatorSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.VoiceReaderSettingDialog
import my.noveldokusha.settings.RegexCleanupSettingsScreen
import my.noveldokusha.settings.RegexCleanupSettingsViewModel

@Composable
internal fun ReaderScreenBottomBarDialogs(
    settings: ReaderScreenState.Settings,
    regexCleanupViewModel: RegexCleanupSettingsViewModel?,
    onTextFontChanged: (String) -> Unit,
    onTextColorChanged: (String) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onParagraphSpacingChanged: (Float) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onSelectableTextChange: (Boolean) -> Unit,
    onDarkModeSelected: (DarkMode) -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onFullScreen: (Boolean) -> Unit,
    onSingleTapToOpenSettingsChange: (Boolean) -> Unit,
    onTtsHighlightEnabledChange: (Boolean) -> Unit,
    onTtsHighlightColorChange: (String) -> Unit,
    onManualHighlightEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(horizontal = 24.dp)) {
            AnimatedContent(targetState = settings.selectedSetting.value, label = "") { target ->
                when (target) {
                    ReaderScreenState.Settings.Type.LiveTranslation -> TranslatorSettingDialog(
                        state = settings.liveTranslation
                    )
                    ReaderScreenState.Settings.Type.TextToSpeech -> VoiceReaderSettingDialog(
                        state = settings.textToSpeech,
                        floatingTtsState = settings.floatingTts,
                        parallelEnabled = settings.liveTranslation.parallelEnabled,
                    )
                    ReaderScreenState.Settings.Type.Style -> {
                        StyleSettingDialog(
                            state = settings.style,
                            onDarkModeChange = onDarkModeSelected,
                            onAppThemeChange = onAppThemeSelected,
                            onTextFontChange = onTextFontChanged,
                            onTextColorChanged = onTextColorChanged,
                            onTextSizeChange = onTextSizeChanged,
                            onLineHeightChange = onLineHeightChanged,
                            onParagraphSpacingChange = onParagraphSpacingChanged,
                            onLetterSpacingChange = onLetterSpacingChanged,
                        )
                    }
                    ReaderScreenState.Settings.Type.More -> MoreSettingDialog(
                        allowTextSelection = settings.isTextSelectable.value,
                        onAllowTextSelectionChange = onSelectableTextChange,
                        keepScreenOn = settings.keepScreenOn.value,
                        onKeepScreenOn = onKeepScreenOn,
                        fullScreen = settings.fullScreen.value,
                        onFullScreen = onFullScreen,
                        singleTapToOpenSettings = settings.isSingleTapToOpenSettings.value,
                        onSingleTapToOpenSettingsChange = onSingleTapToOpenSettingsChange,
                        ttsHighlightEnabled = settings.ttsHighlight.isEnabled.value,
                        onTtsHighlightEnabledChange = onTtsHighlightEnabledChange,
                        ttsHighlightColor = settings.ttsHighlight.highlightColor.value,
                        onTtsHighlightColorChange = onTtsHighlightColorChange,
                        manualHighlightEnabled = settings.manualHighlightEnabled.value,
                        onManualHighlightEnabledChange = onManualHighlightEnabledChange,
                    )
                    ReaderScreenState.Settings.Type.None -> Unit
                    ReaderScreenState.Settings.Type.RegexRules -> {
                        val viewModel = regexCleanupViewModel
                        if (viewModel != null) {
                            val panelHeight =
                                (LocalConfiguration.current.screenHeightDp * 0.6f).roundToInt().dp
                            RegexCleanupSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    settings.selectedSetting.value =
                                        ReaderScreenState.Settings.Type.None
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(panelHeight),
                                applyStatusBarPadding = false,
                                compactHeader = true,
                            )
                        }
                    }
                }
            }
        }
    }
}