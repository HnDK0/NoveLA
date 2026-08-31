package my.noveldokusha.features.reader.ui

import androidx.compose.ui.graphics.Color
import my.noveldokusha.features.reader.tools.BackgroundImageLoader
import my.noveldokusha.reader.R
import java.io.File

/**
 * Пресет фона читалки — процедурный вертикальный градиент (top→bottom),
 * заданный кодом, без растровых ресурсов. Список цветов читается сверху вниз.
 * [textColor] — ARGB-hex (без "#") цвет текста, подобранный под контраст фона.
 */
internal data class ReaderBackgroundPreset(
    val id: String,
    val nameRes: Int,
    val colors: List<Color>,
    val textColor: String,
)

internal val ReaderBackgroundPresets: List<ReaderBackgroundPreset> = listOf(
    ReaderBackgroundPreset(
        id = "paper",
        nameRes = R.string.reader_background_preset_paper,
        colors = listOf(Color(0xFFFEFDFB), Color(0xFFFBF9F4), Color(0xFFF7F4ED)),
        textColor = "FF2C2C34",
    ),
    ReaderBackgroundPreset(
        id = "cream",
        nameRes = R.string.reader_background_preset_cream,
        colors = listOf(Color(0xFFFBF7F0), Color(0xFFF8F2E8), Color(0xFFF3ECE0)),
        textColor = "FF3B2F1E",
    ),
    ReaderBackgroundPreset(
        id = "linen",
        nameRes = R.string.reader_background_preset_linen,
        colors = listOf(Color(0xFFFAF5EF), Color(0xFFF5EEE4), Color(0xFFEFE6D8)),
        textColor = "FF3A3529",
    ),
    ReaderBackgroundPreset(
        id = "mist",
        nameRes = R.string.reader_background_preset_mist,
        colors = listOf(Color(0xFFF4F6F8), Color(0xFFEEF1F4), Color(0xFFE8ECF0)),
        textColor = "FF2A3040",
    ),
    ReaderBackgroundPreset(
        id = "sage",
        nameRes = R.string.reader_background_preset_sage,
        colors = listOf(Color(0xFFF3F7F4), Color(0xFFEEF3EF), Color(0xFFE7EDE8)),
        textColor = "FF1F2E22",
    ),
    ReaderBackgroundPreset(
        id = "twilight",
        nameRes = R.string.reader_background_preset_twilight,
        colors = listOf(Color(0xFF2A2535), Color(0xFF242030), Color(0xFF1E1B28)),
        textColor = "FFD8CCE8",
    ),
    ReaderBackgroundPreset(
        id = "dusk",
        nameRes = R.string.reader_background_preset_dusk,
        colors = listOf(Color(0xFF2D2418), Color(0xFF252018), Color(0xFF1E1A14)),
        textColor = "FFE0D4C0",
    ),
    ReaderBackgroundPreset(
        id = "cocoa",
        nameRes = R.string.reader_background_preset_cocoa,
        colors = listOf(Color(0xFF2B2320), Color(0xFF252018), Color(0xFF1F1B16)),
        textColor = "FFE5D8CC",
    ),
    ReaderBackgroundPreset(
        id = "night",
        nameRes = R.string.reader_background_preset_night,
        colors = listOf(Color(0xFF1A1D24), Color(0xFF171A20), Color(0xFF14161C)),
        textColor = "FFE0E4EC",
    ),
    ReaderBackgroundPreset(
        id = "ocean",
        nameRes = R.string.reader_background_preset_ocean,
        colors = listOf(Color(0xFF0F1A24), Color(0xFF0D1820), Color(0xFF0B161C)),
        textColor = "FFC0D0E0",
    ),
    ReaderBackgroundPreset(
        id = "arctic",
        nameRes = R.string.reader_background_preset_arctic,
        colors = listOf(Color(0xFF1A2030), Color(0xFF182028), Color(0xFF162020)),
        textColor = "FFD0E0E8",
    ),
    ReaderBackgroundPreset(
        id = "forest",
        nameRes = R.string.reader_background_preset_forest,
        colors = listOf(Color(0xFF182018), Color(0xFF161E16), Color(0xFF141C14)),
        textColor = "FFD0E0D0",
    ),
    ReaderBackgroundPreset(
        id = "rose",
        nameRes = R.string.reader_background_preset_rose,
        colors = listOf(Color(0xFF2A1E22), Color(0xFF261C20), Color(0xFF221A1E)),
        textColor = "FFE8D0D8",
    ),
    ReaderBackgroundPreset(
        id = "midnight",
        nameRes = R.string.reader_background_preset_midnight,
        colors = listOf(Color(0xFF0D0D0D), Color(0xFF0A0A0A), Color(0xFF070707)),
        textColor = "FFE0E0E0",
    ),
)

/** Ветка рендера фона читалки: ничего, градиентный пресет или импортированная картинка. */
internal sealed interface BackgroundLayer {
    data object None : BackgroundLayer
    data class Preset(val preset: ReaderBackgroundPreset) : BackgroundLayer
    data class Image(val file: File) : BackgroundLayer
}

/** Чистая функция решения рендер-ветки: пусто -> None, пресет по id -> Preset, иначе файл -> Image. */
internal fun backgroundLayer(
    bg: String,
    presets: List<ReaderBackgroundPreset> = ReaderBackgroundPresets,
    fileResolver: (String) -> File? = { BackgroundImageLoader.resolveFile(it) },
): BackgroundLayer = when {
    bg.isBlank() -> BackgroundLayer.None
    else -> presets.firstOrNull { it.id == bg }?.let { BackgroundLayer.Preset(it) }
        ?: fileResolver(bg)?.let { BackgroundLayer.Image(it) }
        ?: BackgroundLayer.None
}