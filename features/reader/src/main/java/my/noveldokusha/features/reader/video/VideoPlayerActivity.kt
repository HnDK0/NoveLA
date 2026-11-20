package my.noveldokusha.features.reader.video

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State as ComposeState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import my.noveldokusha.core.utils.formatDuration
import my.noveldokusha.coreui.AppThemeProvider
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.features.reader.video.VideoPlayerViewModel.State
import my.noveldokusha.network.MediaHttpClient
import my.noveldokusha.reader.R
import my.noveldokusha.scraper.domain.VideoSource
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

// Таймаут автоскрытия панели берётся из настроек (VIDEO_CONTROLLER_AUTO_HIDE_MS,
// дефолт 5с); 0 — никогда, панель гаснет только тапом (setControllerHideOnTouch).

// Высота градиентной шапки (название серии + строка действий) в dp: она
// рисуется Compose-слоем поверх видео и тачи не потребляет, поэтому зону
// исключения жестов расширяем на неё явно.
private const val HEADER_HEIGHT_DP = 72

/**
 * Эмулятор (goldfish/ranchu/generic): декодер `c2.goldfish.h264.decoder`
 * покрывает HLS розовыми полосами (ExoPlayer #7903, media #2118) — на таких
 * устройствах принудительно включаем программный декодер, см.
 * [createRenderersFactory]. lazy: unit-тесты (JVM) не трогают android.os.Build
 * при загрузке файла.
 */
private val isEmulatorDevice: Boolean by lazy {
    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.PRODUCT.contains("goldfish", ignoreCase = true)
}

/**
 * Рендереры плеера: fallback на запасной декодер вместо фатальной ошибки, а на
 * эмуляторе декодеры goldfish* (розовые полосы на HLS, ExoPlayer #7903 /
 * media #2118) отфильтровываются — остаётся программный `c2.android.*`.
 * Если фильтр оставит пусто, берём полный список: без декодеров воспроизведение
 * не начнётся вовсе.
 *
 * [preferSoftwareDecoder] — после падения hw-декодера (MediaTek: `c2.mtk.hevc.decoder`
 * отклоняет 10-бит HEVC, err 0xe) аппаратные декодеры ставятся в конец списка,
 * иначе ExoPlayer снова выберет их же; два условия (эмулятор + программный)
 * работают в одной секции селектора, а не в двух взаимоисключающих.
 */
private fun createRenderersFactory(
    context: Context,
    preferSoftwareDecoder: Boolean = false,
): DefaultRenderersFactory =
    DefaultRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        if (isEmulatorDevice || preferSoftwareDecoder) {
            setMediaCodecSelector { mimeType, secure, tunneling ->
                val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                // Отсев goldfish*: без него эмулятор рисует розовые полосы на HLS.
                val filtered = if (isEmulatorDevice) {
                    all.filterNot { it.name.contains("goldfish", ignoreCase = true) }
                } else all
                // c2.android.* наверх, vendor (c2.mtk.*) — в конец; сортировка
                // безвредна и для прочих mime: списки декодеров там короткие.
                val sorted = if (preferSoftwareDecoder) {
                    filtered.sortedByDescending { it.name.startsWith("c2.android.") }
                } else filtered
                if (sorted.isEmpty()) all else sorted
            }
        }
    }

/**
 * HUD жеста: иконка + значение по центру экрана поверх видео.
 * Живёт только пока диспетчер шлёт [GestureHud]; null — гасит оверлей.
 */
@Composable
private fun GestureHudOverlay(state: ComposeState<GestureHud?>) {
    val hud = state.value
    // Последнее значение для плавного exit: когда диспетчер шлёт null,
    // HUD гаснет, но содержимое должно остаться до конца fade-анимации.
    var lastHud by remember { mutableStateOf<GestureHud?>(null) }
    if (hud != null) lastHud = hud
    AnimatedVisibility(
        visible = hud != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val shown = lastHud
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                modifier = Modifier.padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (shown) {
                        // Позиция/длительность, а не дельта: во время drag-а
                        // дельта прыгала бы относительно движущейся базы.
                        is GestureHud.Seek -> {
                            Icon(Icons.Rounded.AccessTime, contentDescription = null)
                            Text(
                                text = formatDuration((shown.positionMs / 1000).coerceAtLeast(0).toInt()) + "/" +
                                    formatDuration((shown.durationMs / 1000).coerceAtLeast(0).toInt()),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        is GestureHud.Brightness -> {
                            Icon(Icons.Rounded.Brightness6, contentDescription = null)
                            Text(
                                text = "${(shown.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        is GestureHud.Volume -> {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                            Text(
                                text = "${(shown.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        is GestureHud.Speed -> {
                            Icon(Icons.Rounded.Speed, contentDescription = null)
                            Text(
                                // Целая скорость — без дробной части, как в панели
                                // («2x»), иначе «1.5x».
                                text = if (shown.factor % 1f == 0f) {
                                    "${shown.factor.toInt()}x"
                                } else "${shown.factor}x",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}

@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {

    private val viewModel: VideoPlayerViewModel by viewModels()

    @Inject
    @MediaHttpClient
    lateinit var mediaClient: OkHttpClient

    @Inject
    lateinit var videoDownloadManager: VideoDownloadManager

    @Inject
    lateinit var themeProvider: AppThemeProvider

    // Текущий плеер из PlayerSurface — источник для persistPosition().
    private var player: ExoPlayer? = null

    // D7 (минимальный уровень): сессия поверх текущего плеера (без сервиса —
    // уведомление/фон вынесены в отдельный таск, см. рапорт Task 10).
    private var mediaSession: MediaSession? = null

    // Текущая ориентация: нужна Compose (инсеты, иконка полного экрана) и
    // отсечке эха колбэка fullscreen-кнопки. Меняется в onCreate и
    // onConfigurationChanged — configChanges в манифесте, activity живёт.
    private var portrait by mutableStateOf(true)

    // C1: экран не спит только при реальном воспроизведении. Гейт — состояние
    // плеера (playWhenReady && STATE_READY): пауза/ENDED/ошибка/загрузка →
    // снятие флага. Паттерн add/clearFlags — как в MangaReaderActivity.applySettings.
    // Блокировка экрана из меню шестерёнки лежит поверх гейта: пока включена,
    // флаг не снимается ни при паузе, ни вне Ready.
    private var keepScreenOnLocked by mutableStateOf(false)

    private fun setKeepScreenOn(enabled: Boolean) {
        val active = enabled || keepScreenOnLocked
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (active) window.addFlags(flag) else window.clearFlags(flag)
    }

    private fun updateKeepScreenOn() {
        val playing = player?.let { it.playWhenReady && it.playbackState == Player.STATE_READY } == true
        setKeepScreenOn(playing)
    }

    /**
     * Портрет — обычный режим с барами; ландшафт — иммерсив: бары спрятаны
     * до свайпа от края, экран принадлежит видео, контроллы меди3 и верхняя
     * строка сами управляют показом. Паттерн — MangaReaderActivity.
     */
    private fun applyOrientationChrome(isPortrait: Boolean) {
        portrait = isPortrait
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isPortrait) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateKeepScreenOn()
        override fun onPlaybackStateChanged(playbackState: Int) = updateKeepScreenOn()
    }

    companion object {
        fun start(context: Context, bookUrl: String, chapterUrl: String) {
            context.startActivity(
                Intent(context, VideoPlayerActivity::class.java)
                    .putExtra("bookUrl", bookUrl)
                    .putExtra("chapterUrl", chapterUrl)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // C1: FLAG_KEEP_SCREEN_ON больше не ставится здесь безусловно —
        // флаг гейтится состоянием плеера (см. updateKeepScreenOn()).
        viewModel.resolve()
        applyOrientationChrome(
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        )
        setContent {
            // Общая тема NoveLA (светлая/тёмная/AMOLED + edge-to-edge),
            // как у MangaReaderActivity/ChaptersActivity.
            Theme(themeProvider) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Вне Ready плеера нет (loading/ошибка резолва живут вне
                // Player.Listener) — снимаем флаг явно.
                if (state !is State.Ready) {
                    LaunchedEffect(state) { setKeepScreenOn(false) }
                }
                when (val s = state) {
                    // Своё окно/фон: окно активности наследует AppTheme.Light из
                    // манифеста (следует системному uiMode), а Compose-тема — из
                    // настройки приложения. Без явного фона текст мог оказаться
                    // неразличимым (тёмный на тёмном / светлый на светлом).
                    is State.Loading -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.video_loading_sources))
                    }

                    is State.Error -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            when (s) {
                                is State.Error.Message -> s.text
                                is State.Error.Localized -> stringResource(s.messageRes)
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.resolve() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }

                    is State.Ready -> {
                        // Начальный выбор — сохранённый per-book вариант (подпись
                        // quality); без совпадения играет первый, но это не выбор,
                        // а заглушка до явного выбора в окне ниже.
                        val savedQuality = remember(s.videos) { viewModel.savedVideoVariantQuality() }
                        var selected by remember(s.videos) {
                            mutableStateOf(
                                s.videos.firstOrNull { it.quality == savedQuality } ?: s.videos.first()
                            )
                        }
                        val video = selected
                        // Автопоказ окна — только когда вариантов >1 и сохранённого
                        // выбора нет: единственный вариант выбора не требует, а без
                        // сохранённого показываем окно вместо молчаливого выбора.
                        var showVariantDialog by remember(s.videos) {
                            mutableStateOf(
                                s.videos.size > 1 && s.videos.none { it.quality == savedQuality }
                            )
                        }
                        // Кеш probe-статусов вариантов — только в памяти, ключ —
                        // полный URL (подпись в нём меняет ключ сама).
                        // OK/UNKNOWN не перепроверяем (probe дорогой),
                        // DEAD пробим заново при каждом открытии окна.
                        val probeStatuses = remember { mutableStateMapOf<String, VideoVariantProbe.Status>() }
                        // Пробы в полёте: не плодить дубли (прогрев и автопоказ
                        // окна стартуют в одном кадре) и не давать позднему
                        // UNKNOWN затереть DEAD от быстрой пробы.
                        val probeInFlight = remember { mutableSetOf<String>() }
                        // Проба одного URL в scope текущего эффекта: статус
                        // пишется сразу по готовности, не дожидаясь остальных.
                        fun CoroutineScope.probeAll(videos: List<VideoSource>) {
                            videos.forEach video@{ video ->
                                if (!probeInFlight.add(video.url)) return@video
                                launch {
                                    try {
                                        val startedAt = System.currentTimeMillis()
                                        val status = VideoVariantProbe.probe(
                                            url = video.url,
                                            headers = video.headers,
                                            client = mediaClient,
                                        )
                                        // Диагностика на устройстве: без строки результата
                                        // не отличить «бейдж не появился» от «проба
                                        // не кончилась / вернула UNKNOWN».
                                        Timber.d(
                                            "VideoVariantProbe: ${video.quality} → $status " +
                                                "(${System.currentTimeMillis() - startedAt}ms)",
                                        )
                                        // Поздний UNKNOWN не затирает уже
                                        // известный статус дубликата.
                                        if (status != VideoVariantProbe.Status.UNKNOWN ||
                                            probeStatuses[video.url] == null
                                        ) {
                                            probeStatuses[video.url] = status
                                        }
                                    } finally {
                                        probeInFlight.remove(video.url)
                                    }
                                }
                            }
                        }
                        // Прогрев кеша — сразу при получении источников, без
                        // ожидания открытия окна (ключи — URL, равенство по
                        // содержимому). Открытый/закрытый диалог эти пробы не
                        // отменяет: эффект висит на s.videos, а не на окне.
                        LaunchedEffect(s.videos.map { it.url }) {
                            probeAll(s.videos.filter { probeStatuses[it.url] == null })
                        }
                        // Открытие окна — дозапуск: отсутствующие плюс DEAD
                        // (DEAD перепроверяем при каждом открытии, как договорились).
                        LaunchedEffect(showVariantDialog) {
                            if (!showVariantDialog) return@LaunchedEffect
                            val pending = s.videos.filter { video ->
                                val cached = probeStatuses[video.url]
                                cached == null || cached == VideoVariantProbe.Status.DEAD
                            }
                            if (pending.isEmpty()) return@LaunchedEffect
                            probeAll(pending)
                        }
                        // Разрешения внутри одного исходника (HLS 240p…720p):
                        // кнопка «Разрешение» нужна, только когда выбирать есть
                        // из чего (одно разрешение — выбор бессмыслен).
                        var videoFormatCount by remember(video.url) { mutableStateOf(0) }
                        // Меню шестерёнки: подменяет popup меди3 (там только
                        // «Скорость» и «Аудио», позиции 0/1) — см. PlayerSurface.
                        var showPlayerMenu by remember(video.url) { mutableStateOf(false) }
                        // Ошибка воспроизведения: null — плеер играет. При
                        // декодер-крэше первый раз флаг уходит в
                        // forceSoftwareDecoder (пересоздание плеера), и только
                        // повторный сбой показывает экран ошибки.
                        var playerError by remember(video.url) { mutableStateOf<PlaybackException?>(null) }
                        // Программный декодер вместо отказавшего hw — меняет
                        // key(video.url) и пересоздаёт AndroidView с новым плеером.
                        var forceSoftwareDecoder by remember(video.url) { mutableStateOf(false) }
                        // Ручной повтор: инкремент в key() тоже пересобирает плеер.
                        var retryToken by remember(video.url) { mutableStateOf(0) }
                        // Снимок настроек жестов: читается один раз при входе
                        // в Ready, пишется через viewModel.saveGestureSettings.
                        var gestureSettings by remember { mutableStateOf(viewModel.gestureSettings()) }
                        // HUD жеста: обновляется из PlayerGestureDispatcher,
                        // null — оверлей погашен. State-объект (не delegate):
                        // значение читает только оверлей, Ready от HUD не
                        // перекомпозуется (обновления идут на каждом кадре drag'а).
                        val gestureHud = remember(video.url) { mutableStateOf<GestureHud?>(null) }
                        // Скорость вне gestureSettings: панель читает её при
                        // открытии (плеер — не Compose-состояние) и пишет по чипу.
                        var playbackSpeed by remember(video.url) { mutableStateOf(1f) }
                        LaunchedEffect(showPlayerMenu) {
                            if (showPlayerMenu) playbackSpeed = player?.playbackParameters?.speed ?: 1f
                        }
                        if (showVariantDialog) {
                            VariantDialog(
                                videos = s.videos,
                                selected = video,
                                // Бейджи «10-бит не откроется» / «Недоступен».
                                statuses = probeStatuses,
                                onSelect = {
                                    // Смена варианта пересоздаёт плеер (key(video.url)):
                                    // фиксируем позицию и переносим её в стартовую,
                                    // иначе новый плеер продолжил бы с начала эпизода.
                                    persistPosition()
                                    player?.currentPosition?.let(viewModel::updateStartPositionMs)
                                    selected = it
                                    showVariantDialog = false
                                    viewModel.saveVideoVariantQuality(it.quality)
                                },
                                // Закрытие окна выбор не меняет и не сохраняет —
                                // при отсутствии сохранённого спросим в следующий раз.
                                onDismissRequest = { showVariantDialog = false },
                            )
                        }
                        // Подпись текущего варианта для тулбара: пустой quality
                        // никогда не показываем как URL.
                        val activeLabel = if (video.quality.isBlank()) {
                            stringResource(R.string.video_variant_fallback, s.videos.indexOf(video) + 1)
                        } else video.quality
                        // Верхняя строка и панель меди3 видимы вместе: тап по
                        // видео показывает обе, скрытие — через таймаут меди3.
                        var controlsVisible by remember(video.url) { mutableStateOf(true) }
                        Box(Modifier.fillMaxSize()) {
                            // key(): смена варианта/переключение на программный
                            // декодер/повтор пересоздаёт AndroidView → новый плеер.
                            key(video.url, forceSoftwareDecoder, retryToken) {
                                PlayerSurface(
                                    video = video,
                                    startPositionMs = viewModel.startPositionMs,
                                    mediaClient = mediaClient,
                                    videoDownloadManager = videoDownloadManager,
                                    // ⏮/⏭ в панели управления → соседняя серия;
                                    // null — кнопка гасится на границе списка.
                                    onSwitchChapter = { persistAndSwitch(it) },
                                    previousChapterUrl = viewModel.prevChapterUrl(),
                                    nextChapterUrl = viewModel.nextChapterUrl(),
                                    isPortrait = portrait,
                                    // Снимок настроек жестов: читается один раз
                                    // на рекомпоз, пишется через viewModel.
                                    gestureSettings = gestureSettings,
                                    activity = this@VideoPlayerActivity,
                                    gestureConfigProvider = { viewModel.gestureConfigForWidth(it) },
                                    onGestureHud = { gestureHud.value = it },
                                    onFullscreenButtonClick = ::onFullscreenButtonClick,
                                    // Шестерёнка → общее меню настроек (см. ниже).
                                    onSettingsClick = { showPlayerMenu = true },
                                    onControlsVisibilityChanged = { controlsVisible = it },
                                    onVideoFormatCountChanged = { videoFormatCount = it },
                                    onPlayerCreated = { created ->
                                        player = created
                                        // C1: слушатель гейта экрана; текущее
                                        // состояние применяем сразу (при входе
                                        // с paused-плеером флаг не «залипнет»).
                                        created.addListener(playerListener)
                                        updateKeepScreenOn()
                                        // «onCreate, после создания player» из брифа:
                                        // плеер создаётся лениво в Ready, при смене
                                        // качества пересобираем сессию.
                                        mediaSession?.release()
                                        mediaSession = MediaSession.Builder(this@VideoPlayerActivity, created)
                                            // Куда вернуться со lockscreen/ассистента —
                                            // на саму activity (та же пара book/chapter).
                                            .setSessionActivity(
                                                PendingIntent.getActivity(
                                                    this@VideoPlayerActivity,
                                                    0,
                                                    this@VideoPlayerActivity.intent,
                                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                                )
                                            )
                                            // Дефолт media3 1.11.1 гонит на плеер только
                                            // 79 (HEADSETHOOK) и 85 (PLAY_PAUSE, в т.ч.
                                            // double-tap → следующая серия); одиночные
                                            // PLAY(126)/PAUSE(127)/NEXT(87)/PREVIOUS(88)
                                            // он не обрабатывает — добираем здесь.
                                            // false → media3 выполняет своё дефолтное
                                            // действие; ACTION_MEDIA_BUTTON и KeyEvent
                                            // он отфильтровывает до колбэка сам.
                                            .setCallback(object : MediaSession.Callback {
                                                override fun onMediaButtonEvent(
                                                    session: MediaSession,
                                                    controllerInfo: MediaSession.ControllerInfo,
                                                    intent: Intent,
                                                ): Boolean {
                                                    val key = IntentCompat.getParcelableExtra(
                                                        intent,
                                                        Intent.EXTRA_KEY_EVENT,
                                                        KeyEvent::class.java
                                                    ) ?: return false
                                                    if (key.action != KeyEvent.ACTION_DOWN) return false
                                                    return when (key.keyCode) {
                                                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                                            created.play()
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                                            created.pause()
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                                            viewModel.nextChapterUrl()?.let { persistAndSwitch(it) }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                                            viewModel.prevChapterUrl()?.let { persistAndSwitch(it) }
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                }
                                            })
                                            .build()
                                    },
                                    onPlayerReleased = { released ->
                                        released.removeListener(playerListener)
                                        if (player === released) {
                                            player = null
                                            mediaSession?.release()
                                            mediaSession = null
                                        }
                                        updateKeepScreenOn()
                                    },
                                    onPlayerEnded = {
                                        persistAndSwitch(viewModel.nextChapterUrl())
                                    },
                                    preferSoftwareDecoder = forceSoftwareDecoder,
                                    // Первый декодер-крэш молча уводит в
                                    // программный декодер; прочие ошибки → экран ниже.
                                    onPlayerError = { error ->
                                        Timber.d("Player error: code=${error.errorCode}", error)
                                        val isDecoderError =
                                            // В media3 декодер-крэш — ERROR_CODE_DECODING_FAILED
                                            // (имя ERROR_CODE_DECODER_FAILED из ExoPlayer2 здесь нет).
                                            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                                                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                                        if (isDecoderError && !forceSoftwareDecoder) {
                                            // Позицию фиксируем до пересоздания
                                            // плеера — как при смене варианта.
                                            persistPosition()
                                            player?.currentPosition?.let(viewModel::updateStartPositionMs)
                                            forceSoftwareDecoder = true
                                        } else {
                                            playerError = error
                                        }
                                    },
                                )
                            }
                            // Оверлей поверх видео: строка видна ровно тогда, когда
                            // панель меди3, и гаснет вместе с ней (fade, не мигает).
                            AnimatedVisibility(
                                visible = controlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // Скрим от статус-бара вниз: иконки статус-бара
                                        // и подпись читаются поверх яркого видео.
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Black.copy(alpha = 0.7f),
                                                    Color.Transparent,
                                                )
                                            )
                                        )
                                        // Окно edge-to-edge (coreui Theme): без инсета
                                        // строка рисуется ПОД статус-баром — иконки
                                        // статус-бара перекрывают подпись, тапы по
                                        // кнопкам съедает статус-бар. В ландшафте бары
                                        // спрятаны иммерсивом — инсет там не нужен.
                                        .then(if (portrait) Modifier.statusBarsPadding() else Modifier)
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                ) {
                                    // Что смотрим: название текущей главы —
                                    // оно само по себе уже содержит номер.
                                    // Без названия подпись не показываем.
                                    val episodeTitle = viewModel.episodeTitle
                                    if (episodeTitle.isNotBlank()) {
                                        Text(
                                            text = episodeTitle,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Текущий вариант в самой строке: подпись играющего
                                        // потока + открытие окна выбора (несколько вариантов).
                                        // Прежней кнопки «Tune» с описанием «Качество» нет —
                                        // вариант бывает и озвучкой, не только разрешением.
                                        if (s.videos.size > 1) TextButton(
                                            onClick = { showVariantDialog = true },
                                            modifier = Modifier.weight(1f, fill = false),
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                        ) {
                                            Text(
                                                text = activeLabel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                        } else Text(
                                            text = activeLabel,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .padding(horizontal = 8.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.White,
                                        )
                                        // Разрешения самого потока (HLS) — отдельно от
                                        // «Качества» плагина в окне вариантов: это уровни
                                        // одного исходника, не разные исходники. Селектор
                                        // есть и в меню шестерёнки.
                                        if (videoFormatCount > 1) TextButton(
                                            onClick = { showStreamQualityDialog() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                        ) {
                                            Text(stringResource(R.string.video_resolution))
                                        }
                                        TextButton(
                                            onClick = { openInOtherApp(video) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                        ) {
                                            Text(stringResource(R.string.video_open_in_other))
                                        }
                                    }
                                }
                            }
                            // HUD жеста (перемотка/яркость/громкость/скорость):
                            // поверх видео, читает только свой state — Ready
                            // от кадровых обновлений не перекомпозуется.
                            GestureHudOverlay(gestureHud)
                            // Экран ошибки поверх плеера (последний ребёнок Box —
                            // рисуется поверх шапки/панели и гасит их жесты,
                            // иначе тапы уходили бы в мёртвый PlayerView).
                            val playbackError = playerError
                            if (playbackError != null) {
                                // Диапазоны errorCode media3: 2xxx/3xxx — источник (сеть/сервер/парсинг),
                                // 4xxx — декодер. Ниже — причина сбоя для пользователя.
                                val errorCode = playbackError.errorCode
                                val messageRes = when {
                                    errorCode in 2000..3999 -> R.string.video_playback_error_source
                                    errorCode in 4000..4999 -> R.string.video_playback_error_codec
                                    else -> R.string.video_playback_error
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .clickable { }
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // Скрим чёрный в обеих темах, поэтому текст явно
                                    // светлый: в светлой теме onSurface тёмный и на
                                    // чёрном фоне не читается.
                                    Text(stringResource(messageRes), color = Color.White)
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = {
                                        playerError = null
                                        retryToken++
                                    }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                    // Смена варианта — только когда есть из чего
                                    // выбирать (та же проверка, что и в шапке).
                                    if (s.videos.size > 1) {
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = {
                                            playerError = null
                                            showVariantDialog = true
                                        }) {
                                            Text(stringResource(R.string.video_variant_title))
                                        }
                                    }
                                    // Внешний плеер (MX Player) декодирует поток своим декодером —
                                    // показываем кнопку только когда виноваты кодеки устройства.
                                    if (errorCode in 4000..4999) {
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = {
                                            playerError = null
                                            openInOtherApp(video)
                                        }) {
                                            Text(stringResource(R.string.video_open_in_other))
                                        }
                                    }
                                }
                            }
                        }
                        // Панель настроек: композируется безусловно — скрытие и
                        // exit-анимация идут через visible (см. VideoSettingsPanel).
                        // Дорожки читаем здесь: перерисовка по showPlayerMenu
                        // берёт у плеера актуальные (плеер — поле Activity).
                        val textGroups = player?.currentTracks?.groups
                            .orEmpty()
                            .filter { it.type == C.TRACK_TYPE_TEXT }
                        val selectedSubtitle = textGroups.firstNotNullOfOrNull { group ->
                            (0 until group.length)
                                .firstOrNull { group.isTrackSelected(it) }
                                ?.let { group.getTrackFormat(it) }
                        }
                        val audioGroups = player?.currentTracks?.groups
                            .orEmpty()
                            .filter { it.type == C.TRACK_TYPE_AUDIO }
                        val selectedAudio = audioGroups.firstNotNullOfOrNull { group ->
                            (0 until group.length)
                                .firstOrNull { group.isTrackSelected(it) }
                                ?.let { group.getTrackFormat(it) }
                        }
                        val videoGroups = player?.currentTracks?.groups
                            .orEmpty()
                            .filter { it.type == C.TRACK_TYPE_VIDEO }
                        val selectedVideoFormat = videoGroups.firstNotNullOfOrNull { group ->
                            (0 until group.length)
                                .firstOrNull { group.isTrackSelected(it) }
                                ?.let { group.getTrackFormat(it) }
                        }
                        val trackNameProvider = DefaultTrackNameProvider(this@VideoPlayerActivity.resources)
                        // Запись настроек: state + AppPreferences одной операцией.
                        fun updateGesture(
                            transform: (VideoPlayerViewModel.GestureSettings) -> VideoPlayerViewModel.GestureSettings,
                        ) {
                            gestureSettings = transform(gestureSettings)
                            viewModel.saveGestureSettings(gestureSettings)
                        }
                        VideoSettingsPanel(
                            visible = showPlayerMenu,
                            state = VideoSettingsPanelState(
                                playbackSpeed = playbackSpeed,
                                seekStepMs = gestureSettings.seekStepMs,
                                controllerAutoHideMs = gestureSettings.controllerAutoHideMs,
                                variantLabel = activeLabel,
                                // Одно разрешение выбирать не из чего — строка
                                // прячется (null), а не гасится.
                                resolutionLabel = if (videoFormatCount > 1) {
                                    selectedVideoFormat?.let(trackNameProvider::getTrackName)
                                } else null,
                                doubleTapSeek = gestureSettings.doubleTapEnabled,
                                verticalSwipe = gestureSettings.verticalSwipeEnabled,
                                longPressSpeed = gestureSettings.longPressSpeedEnabled,
                                swapScreenHalves = gestureSettings.swappedSides,
                                dragThreshold = gestureSettings.dragThreshold,
                                audioTrackLabel = selectedAudio?.let(trackNameProvider::getTrackName) ?: "",
                                subtitlesLabel = when {
                                    textGroups.isEmpty() -> stringResource(R.string.video_subtitles_none)
                                    selectedSubtitle == null -> stringResource(R.string.video_subtitles_off)
                                    else -> trackNameProvider.getTrackName(selectedSubtitle)
                                },
                                subtitlesEnabled = textGroups.isNotEmpty(),
                                keepScreenOn = keepScreenOnLocked,
                            ),
                            actions = VideoSettingsPanelActions(
                                onPlaybackSpeedChange = { speed ->
                                    playbackSpeed = speed
                                    player?.let { it.playbackParameters = PlaybackParameters(speed) }
                                },
                                onSeekStepChange = { ms ->
                                    updateGesture { it.copy(seekStepMs = ms) }
                                },
                                onControllerAutoHideChange = { ms ->
                                    updateGesture { it.copy(controllerAutoHideMs = ms) }
                                },
                                // Субдиалоги — отдельные окна: панель закрываем,
                                // иначе после их закрытия поверх останется скрим.
                                onVariantClick = {
                                    showPlayerMenu = false
                                    showVariantDialog = true
                                },
                                onResolutionClick = {
                                    showPlayerMenu = false
                                    showStreamQualityDialog()
                                },
                                onDoubleTapSeekChange = { on ->
                                    updateGesture { it.copy(doubleTapEnabled = on) }
                                },
                                onVerticalSwipeChange = { on ->
                                    updateGesture { it.copy(verticalSwipeEnabled = on) }
                                },
                                onLongPressSpeedChange = { on ->
                                    updateGesture { it.copy(longPressSpeedEnabled = on) }
                                },
                                onSwapScreenHalvesChange = { on ->
                                    updateGesture { it.copy(swappedSides = on) }
                                },
                                onDragThresholdChange = { value ->
                                    updateGesture { it.copy(dragThreshold = value) }
                                },
                                onAudioTrackClick = {
                                    showPlayerMenu = false
                                    showAudioTrackDialog()
                                },
                                onSubtitlesClick = {
                                    showPlayerMenu = false
                                    showSubtitlesDialog()
                                },
                                onKeepScreenOnChange = { on ->
                                    keepScreenOnLocked = on
                                    updateKeepScreenOn()
                                },
                                onClose = { showPlayerMenu = false },
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        persistPosition()
        // Фонового воспроизведения нет (без foreground-сервиса) — ставим паузу.
        // FLAG_KEEP_SCREEN_ON снимется сам: updateKeepScreenOn() слушает
        // onPlayWhenReadyChanged (см. playerListener).
        player?.pause()
    }

    // singleTask: повторный старт при живом инстансе (гейт «единый плеер») →
    // новый Intent. Без этого VM читала бы extras первого старта и юзер
    // оставался бы на уже просмотренном эпизоде.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val bookUrl = intent.getStringExtra("bookUrl") ?: return
        val chapterUrl = intent.getStringExtra("chapterUrl") ?: return
        // Позицию старого эпизода фиксируем до пересадки на новый.
        persistPosition()
        viewModel.openFromIntent(bookUrl, chapterUrl)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        persistPosition()
        applyOrientationChrome(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
    }

    override fun onDestroy() {
        // До super.onDestroy(): там composition dispose → PlayerSurface.onRelease
        // освобождает плеер, и читать позицию будет не с чего.
        persistPosition()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun persistPosition() {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: 0L
        val markRead = duration > 0 && (p.currentPosition * 100 >= duration * 95 ||
                p.playbackState == Player.STATE_ENDED)
        viewModel.saveProgress(positionMs = p.currentPosition, durationMs = duration, markRead = markRead)
    }

    /** Позиция текущего эпизода → БД до ухода с него, затем смена эпизода. */
    private fun persistAndSwitch(url: String?) {
        persistPosition()
        viewModel.switchTo(url)
    }

    private fun openInOtherApp(video: VideoSource) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                Uri.parse(video.url),
                // MIME по расширению: жёсткий video/mp4 для mkv/webm
                // часть плееров отклоняет (Intent не резолвится).
                if (video.url.contains(".m3u8")) "application/x-mpegURL" else "video/*"
            )
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.video_no_app_to_play), Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Кнопка полного экрана меди3: true — «во весь экран» (ландшафт),
     * false — назад в портрет. Условия отсекают эхо синхронизации иконки после
     * поворота сенсором (setFullscreenButtonState шлёт тот же колбэк, что и
     * клик) — иначе обычный поворот устройства запирал бы ориентацию.
     */
    private fun onFullscreenButtonClick(enter: Boolean) {
        when {
            enter && portrait ->
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            !enter && !portrait ->
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /** Разрешения внутри текущего исходника (HLS) — стандартный диалог меди3. */
    private fun showStreamQualityDialog() {
        val p = player ?: return
        TrackSelectionDialogBuilder(
            this,
            getString(R.string.video_resolution),
            p,
            C.TRACK_TYPE_VIDEO,
        ).build().show()
    }

    /** Звуковые дорожки — тот же стандартный диалог меди3, что и раньше. */
    private fun showAudioTrackDialog() {
        val p = player ?: return
        TrackSelectionDialogBuilder(
            this,
            getString(R.string.video_audio_track),
            p,
            C.TRACK_TYPE_AUDIO,
        ).build().show()
    }

    /**
     * Сабтитры: список текстовых дорожек + пункт «Off» (гасит текстовой тип
     * целиком — как CC-кнопка меди3). Дорожки появляются здесь только если
     * плагин отдал VideoSource.subtitles (см. SubtitleConfiguration в PlayerSurface).
     */
    private fun showSubtitlesDialog() {
        val p = player ?: return
        TrackSelectionDialogBuilder(
            this,
            getString(R.string.video_subtitles),
            p,
            C.TRACK_TYPE_TEXT,
        )
            .setShowDisableOption(true)
            .setIsDisabled(p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            .build()
            .show()
    }
}

/**
 * Подпись похожа на разрешение (1080p/720p/480p, sd/hd/fhd/uhd, 2k/4k/8k) —
 * вариант попадает в секцию «Качество»; всё остальное (AniLiberty, Амедиа…) —
 * в секцию «Озвучка». Пустая подпись — не разрешение.
 */
internal fun isResolutionLabel(quality: String): Boolean {
    val label = quality.trim().lowercase()
    return label.matches(Regex("""\d{3,4}p""")) || label in setOf("sd", "hd", "fhd", "uhd", "2k", "4k", "8k")
}

/** Строка выбора с отметкой текущего варианта (галочка вместо плоской кнопки). */
@Composable
private fun VariantOptionRow(
    label: String,
    selected: Boolean,
    // Хвостовой бейдж статуса probe (10-бит/недоступен); null — не показываем.
    badge: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        if (badge != null) Text(
            text = badge,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        )
        if (selected) Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Секция окна выбора: заголовок («Качество»/«Озвучка») + её варианты. */
@Composable
private fun VariantSection(
    title: String,
    items: List<Pair<VideoSource, String>>,
    selected: VideoSource,
    statuses: Map<String, VideoVariantProbe.Status>,
    onSelect: (VideoSource) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 2.dp),
        )
        items.forEach { (video, label) ->
            // Бейдж по статусу probe этого URL: один статус на URL — либо один,
            // либо ничего (OK/UNKNOWN бейдж не показывают).
            val badge = when (statuses[video.url]) {
                VideoVariantProbe.Status.DEAD -> stringResource(R.string.video_variant_badge_dead)
                else -> null
            }
            VariantOptionRow(
                label = label,
                selected = video === selected,
                badge = badge,
                onClick = { onSelect(video) },
            )
        }
    }
}

/**
 * Окно выбора варианта потока: разделено на «Качество» (1080p/720p…) и
 * «Озвучка» (AniLiberty, Амедиа…), чтобы всегда было понятно, что выбирается;
 * отсутствующая секция просто не показывается. Тап по варианту применяет и
 * закрывает окно; закрытие по оверлею/кнопке выбор не меняет.
 */
@Composable
private fun VariantDialog(
    videos: List<VideoSource>,
    selected: VideoSource,
    // Статус probe по URL: бейджи в строках вариантов (см. VariantSection).
    statuses: Map<String, VideoVariantProbe.Status>,
    onSelect: (VideoSource) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Подписи: пустой quality → «Вариант N» (сырой URL пользователю не показываем).
    val labeled = videos.mapIndexed { index, video ->
        video to if (video.quality.isBlank()) {
            stringResource(R.string.video_variant_fallback, index + 1)
        } else video.quality
    }
    val qualities = labeled.filter { isResolutionLabel(it.first.quality) }
    val voiceovers = labeled.filterNot { isResolutionLabel(it.first.quality) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.video_variant_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (qualities.isNotEmpty()) VariantSection(
                    title = stringResource(R.string.video_quality),
                    items = qualities,
                    selected = selected,
                    statuses = statuses,
                    onSelect = onSelect,
                )
                if (voiceovers.isNotEmpty()) VariantSection(
                    title = stringResource(R.string.video_voiceover),
                    items = voiceovers,
                    selected = selected,
                    statuses = statuses,
                    onSelect = onSelect,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/**
 * Обёртка плеера для панели управления меди3: по умолчанию ⏮/⏭ зовут
 * seekToPrevious/seekToNext — это перемотка внутри текущего файла, а при
 * одиночном MediaItem меди3 ещё и считает команды недоступными
 * (COMMAND_SEEK_TO_PREVIOUS/NEXT есть только при hasPrev/hasNextMediaItem),
 * поэтому кнопки гаснут и не работают вовсе. Здесь ⏮/⏭ — переход на
 * соседнюю серию, а команды объявляются доступными, только если такая серия есть.
 */
private class ChapterSwitchingPlayer(
    player: Player,
    private val previousChapterUrl: String?,
    private val nextChapterUrl: String?,
    private val onSwitchChapter: (String) -> Unit,
) : ForwardingPlayer(player) {

    override fun seekToPrevious() {
        previousChapterUrl?.let(onSwitchChapter)
    }

    override fun seekToNext() {
        nextChapterUrl?.let(onSwitchChapter)
    }

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_PREVIOUS ->
            previousChapterUrl != null || super.isCommandAvailable(command)
        Player.COMMAND_SEEK_TO_NEXT ->
            nextChapterUrl != null || super.isCommandAvailable(command)
        else -> super.isCommandAvailable(command)
    }
}

@Composable
private fun PlayerSurface(
    video: VideoSource,
    startPositionMs: Long,
    mediaClient: OkHttpClient,
    videoDownloadManager: VideoDownloadManager,
    onSwitchChapter: (String) -> Unit,
    previousChapterUrl: String?,
    nextChapterUrl: String?,
    isPortrait: Boolean,
    gestureSettings: VideoPlayerViewModel.GestureSettings,
    activity: Activity,
    gestureConfigProvider: (Float) -> GestureConfig,
    onGestureHud: (GestureHud?) -> Unit,
    onFullscreenButtonClick: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onVideoFormatCountChanged: (Int) -> Unit,
    onPlayerCreated: (ExoPlayer) -> Unit,
    onPlayerReleased: (ExoPlayer) -> Unit,
    onPlayerEnded: () -> Unit,
    // true — декодеры c2.android.* выбираются первыми (после падения hw).
    preferSoftwareDecoder: Boolean,
    // Ошибка воспроизведения уходит наверх: экран ошибки поверх плеера.
    onPlayerError: (PlaybackException) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            // Окно edge-to-edge (портрет): нижняя панель управления меди3 и
            // жестовая полоса иначе перекрываются (как строка со статус-баром).
            // В ландшафте бары спрятаны иммерсивом — паддинг не нужен,
            // видео идёт в край.
            .then(if (isPortrait) Modifier.navigationBarsPadding() else Modifier),
        factory = { ctx ->
            PlayerView(ctx).apply {
                // Верхняя строка живёт в Compose поверх видео — показываем её
                // тем же сигналом, что и панель: тап по видео / автоскрытие.
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    onControlsVisibilityChanged(visibility == View.VISIBLE)
                })
                // Уведомление меди3 приходит только при смене uxState, а сам
                // PlayerControlView умеет показаться без него (setVisibility
                // внутри show(), когда uxState уже ALL_VISIBLE) — тогда панель
                // есть, а строка «залипает» скрытой. Поэтому перечитываем
                // фактическую видимость панели: layout проходит на каждом
                // переключении её visibility, observer живёт вместе с view.
                viewTreeObserver.addOnGlobalLayoutListener {
                    onControlsVisibilityChanged(isControllerShown())
                }
                setControllerShowTimeoutMs(gestureSettings.controllerAutoHideMs)
                setControllerHideOnTouch(true)
                // Стрелочка полного экрана из коробки меди3: при установленном
                // listener кнопка показывается в нижней панели; клик по ней
                // меняет ориентацию (см. VideoPlayerActivity.onFullscreenButtonClick).
                setFullscreenButtonClickListener(
                    PlayerView.FullscreenButtonClickListener { enter ->
                        onFullscreenButtonClick(enter)
                    }
                )
                // Бриф: setCustomRequestHeaders — в media3 1.11.1 у Factory нет
                // такого метода (проверено javap); семантически тот же эффект —
                // дефолтные заголовки каждого запроса (Referer и пр.).
                // Офлайн (Task 14): общий с загрузчиком кэш — скачанные сегменты
                // читаются из файлов без сети; upstream с заголовками — на промахах.
                val dataSourceFactory = videoDownloadManager.playerDataSourceFactory(
                    OkHttpDataSource.Factory(mediaClient)
                        .setDefaultRequestProperties(video.headers)
                )
                val exoPlayer = ExoPlayer.Builder(ctx, createRenderersFactory(ctx, preferSoftwareDecoder))
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    // Аудио-фокус: handleAudioFocus=true обязателен — без второго
                    // аргумента плеер фокус не запрашивает. Becoming-noisy: вынутые
                    // наушники ставят паузу сами.
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        /* handleAudioFocus = */ true
                    )
                    .setHandleAudioBecomingNoisy(true)
                    .build()
                // Панели управления отдаём обёртку (см. ChapterSwitchingPlayer);
                // сырой ExoPlayer уходит в onPlayerCreated — слушатель/сессия.
                this.player = ChapterSwitchingPlayer(
                    exoPlayer,
                    previousChapterUrl,
                    nextChapterUrl,
                    onSwitchChapter,
                )
                // Шестерёнка: popup меди3 показывает только «Скорость» и
                // «Аудио» (onSettingViewClicked знает позиции 0/1) —
                // подменяем его слушатель на общее меню настроек. post:
                // клик-слушатель ставится после setPlayer, когда контроллер
                // уже собран; без находки остаётся штатный popup меди3.
                post {
                    findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                        ?.setOnClickListener { onSettingsClick() }
                }
                // Субтитры источника: mime по расширению, автовыбор трека (без
                // ручного селектора — v1).
                val subtitleConfigs = video.subtitles.map { s ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                        .setMimeType(
                            if (s.url.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                        )
                        .setLabel(s.label)
                        .setLanguage(s.lang.takeIf { it.isNotBlank() })
                        .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
                        .build()
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(video.url)
                    // Подсказка mime от плагина: без неё меди3 гадает по URL и
                    // адрес вида /?token=… (без .m3u8) уходит в прогрессивный
                    // источник → UnrecognizedInputFormatException.
                    .also { video.mime?.let(it::setMimeType) }
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.seekTo(startPositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                // Жесты живут внутри View: слой Modifier.pointerInput поверх
                // AndroidView гасил бы события самого PlayerView (и наоборот) —
                // см. док PlayerGestureDispatcher.
                setOnTouchListener(
                    PlayerGestureDispatcher(
                        activity = activity,
                        player = exoPlayer,
                        configProvider = { widthPx -> gestureConfigProvider(widthPx) },
                        // Статус-бар (портрет) и градиентная шапка сверху,
                        // контролы меди3 снизу — тачи там не наши.
                        exclusionTopPx = {
                            val inset = ViewCompat.getRootWindowInsets(this)
                                ?.getInsets(WindowInsetsCompat.Type.systemBars())
                                ?.top
                                ?: 0
                            // Шапка рисуется Compose-слоем поверх и тачи не
                            // потребляет — без этой надбавки свайп с её места
                            // начинал бы жест яркости под строкой названия.
                            // Ориентацию читаем из view, а не из параметра
                            // isPortrait: configChanges не пересоздаёт ни view,
                            // ни этот listener — захваченное значение протухло
                            // бы после поворота.
                            val portrait =
                                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                            if (portrait) {
                                inset + (HEADER_HEIGHT_DP * resources.displayMetrics.density).toInt()
                            } else inset
                        },
                        exclusionBottomPx = {
                            findViewById<View>(androidx.media3.ui.R.id.exo_controller)
                                ?.takeIf { it.visibility == View.VISIBLE }
                                ?.height
                                ?: 0
                        },
                        onToggleControls = { toggleController() },
                        onHud = onGestureHud,
                    )
                )
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) onPlayerEnded()
                    }

                    // Без этого колбэка playback-ошибка (падение декодера)
                    // оставляла бы экран висеть без единого сообщения.
                    override fun onPlayerError(error: PlaybackException) {
                        onPlayerError(error)
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        // HLS даёт несколько разрешений в одном исходнике —
                        // считаем видеоформаты для кнопки «Разрешение».
                        onVideoFormatCountChanged(
                            tracks.groups.sumOf { group ->
                                if (group.type == C.TRACK_TYPE_VIDEO) group.length else 0
                            }
                        )
                    }
                })
                onPlayerCreated(exoPlayer)
            }
        },
        update = { view ->
            // Иконка полного экрана синхронизируется с фактической ориентацией
            // (поворот сенсором — без клика). Отсечка эха в самом колбэке:
            // setFullscreenButtonState шлёт его же — иначе поворот запирал
            // бы ориентацию (см. onFullscreenButtonClick).
            view.setFullscreenButtonState(!isPortrait)
            // Смена автоскрытия в панели применяется живьём: factory
            // выполняется один раз, только update видит новый gestureSettings.
            view.setControllerShowTimeoutMs(gestureSettings.controllerAutoHideMs)
        },
        onRelease = { view ->
            // view.player — обёртка ChapterSwitchingPlayer; освобождаем сырой
            // ExoPlayer, отдававшийся в onPlayerCreated.
            val released = (view.player as? ChapterSwitchingPlayer)?.wrappedPlayer as? ExoPlayer
            if (released != null) {
                onPlayerReleased(released)
                released.release()
            }
        },
    )
}

/**
 * Фактическая видимость панели меди3 — сам [android.view.View], а не
 * uxState: [androidx.media3.ui.PlayerView.isControllerFullyVisible] врёт во
 * время анимации и после рассинхрона (см. PlayerControlViewLayoutManager.show).
 * internal: то же имя читает PlayerGestureDispatcher (другой файл пакета).
 */
internal fun PlayerView.isControllerShown(): Boolean =
    findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility == View.VISIBLE
