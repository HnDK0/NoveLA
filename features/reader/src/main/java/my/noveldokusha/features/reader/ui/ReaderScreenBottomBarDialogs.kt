package my.noveldokusha.features.reader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.ui.settingDialogs.MoreSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.StyleSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.TranslatorSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.VoiceReaderSettingDialog

@Composable
internal fun ReaderScreenBottomBarDialogs(
    settings: ReaderScreenState.Settings,
    onTextFontChanged: (String) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onParagraphSpacingChanged: (Float) -> Unit,
    onSelectableTextChange: (Boolean) -> Unit,
    onDarkModeSelected: (DarkMode) -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onFullScreen: (Boolean) -> Unit,
    onSingleTapToOpenSettingsChange: (Boolean) -> Unit,
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
                        state = settings.textToSpeech
                    )
                    ReaderScreenState.Settings.Type.Style -> {
                        StyleSettingDialog(
                            state = settings.style,
                            onDarkModeChange = onDarkModeSelected,
                            onAppThemeChange = onAppThemeSelected,
                            onTextFontChange = onTextFontChanged,
                            onTextSizeChange = onTextSizeChanged,
                            onLineHeightChange = onLineHeightChanged,
                            onParagraphSpacingChange = onParagraphSpacingChanged,
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
                    )
                    ReaderScreenState.Settings.Type.None -> Unit
                }
            }
        }
    }
}