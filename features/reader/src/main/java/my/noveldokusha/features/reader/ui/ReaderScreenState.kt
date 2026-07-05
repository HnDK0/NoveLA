package my.noveldokusha.features.reader.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.features.LiveTranslationSettingData
import my.noveldokusha.features.reader.features.TextToSpeechSettingData

@Stable
internal data class ReaderScreenState(
    val showReaderInfo: MutableState<Boolean>,
    val readerInfo: CurrentInfo,
    val settings: Settings,
    val showInvalidChapterDialog: MutableState<Boolean>
) {
    @Stable
    data class CurrentInfo(
        val chapterTitle: State<String>,
        val chapterCurrentNumber: State<Int>,
        val chapterPercentageProgress: State<Float>,
        val chaptersCount: State<Int>,
        val chapterUrl: State<String>
    )

    @Stable
    data class Settings(
        val isTextSelectable: State<Boolean>,
        val keepScreenOn: State<Boolean>,
        val fullScreen: State<Boolean>,
        val isSingleTapToOpenSettings: State<Boolean>,
        val textToSpeech: TextToSpeechSettingData,
        val liveTranslation: LiveTranslationSettingData,
        val style: StyleSettingsData,
        val selectedSetting: MutableState<Type>,
    ) {
        @Stable
        data class StyleSettingsData(
            val currentDarkMode: State<DarkMode>,
            val currentAppTheme: State<AppTheme>,
            val textFont: State<String>,
            val textSize: State<Float>,
            val lineHeight: State<Float>,
            val paragraphSpacing: State<Float>,
        )

        @Immutable
        enum class Type {
            None, LiveTranslation, TextToSpeech, Style, More
        }
    }
}