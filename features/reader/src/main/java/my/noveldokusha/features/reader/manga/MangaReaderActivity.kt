package my.noveldokusha.features.reader.manga

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.noveldokusha.core.LocaleManager
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.AppThemeProvider
import my.noveldokusha.coreui.theme.LocalIsDark
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.features.reader.manga.ui.MangaReaderSettingsSheet
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.manga.viewer.pager.createPagerViewer
import my.noveldokusha.features.reader.manga.viewer.webtoon.MangaWebtoonViewer
import my.noveldokusha.features.reader.tools.PageImageLoader
import my.noveldokusha.reader.R
import timber.log.Timber
import javax.inject.Inject

/**
 * Манга/манхва-читалка — порт tachiyomisy ReaderActivity (pager + webtoon вьюеры,
 * тап-зоны, настройки, автопрокрутка), текстовый ридер не тронут: сюда ведёт
 * редирект только для глав-картинок (any Page && none Text).
 *
 * Чистый Compose (Bug2): экран edge-to-edge (enableEdgeToEdge из coreui Theme),
 * оверлеи (счётчик, навигатор, панель глав) сидят поверх системных баров через
 * navigationBarsPadding() — без фиксированных отступов, как в старом ридере.
 */
@AndroidEntryPoint
internal class MangaReaderActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        super.attachBaseContext(LocaleManager.createAppLocaleContext(base))
    }

    companion object {
        private const val EXTRA_BOOK_URL = "bookUrl"
        private const val EXTRA_CHAPTER_URL = "chapterUrl"

        /** Инверсия цветов (tachiyomisy inverted colors). */
        private val INVERT_COLORS_MATRIX = floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 255f,
        )

        /**
         * Страховка от «залипшего» спиннера перехода главы: если новая глава
         * не загрузилась (ошибка сети), флаг снимается по таймауту, чтобы
         * не блокировать повторные свайпы навсегда.
         */
        private const val CHAPTER_TRANSITION_TIMEOUT_MS = 8_000L

        fun start(context: Context, bookUrl: String, chapterUrl: String) {
            context.startActivity(
                Intent(context, MangaReaderActivity::class.java)
                    .putExtra(EXTRA_BOOK_URL, bookUrl)
                    .putExtra(EXTRA_CHAPTER_URL, chapterUrl),
            )
        }
    }

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var themeProvider: AppThemeProvider

    @Inject
    lateinit var pageImageLoader: PageImageLoader

    private val viewModel by viewModels<MangaReaderViewModel>()

    /** Android-хост вьюера: AndroidView оборачивает FrameLayout, в который ensureViewer кладёт view. */
    private lateinit var viewerHost: FrameLayout

    private var viewer: Viewer? = null
    private var viewerMode: MangaReadingMode? = null

    /** URL главы, на которую вьюер установлен последним setChapter. */
    private var viewerChapterUrl: String? = null

    /**
     * Индексы глав, чьи страницы уже запрошены для расширения окна вебтуна
     * (in-flight дедуп: повторные onRequestChapter не дублируют загрузку).
     */
    private val requestedWindowChapters = mutableSetOf<Int>()

    /** Тулбар (меню) виден — тап в зону MENU переключает. */
    private val toolbarVisible = mutableStateOf(false)

    /** Шит настроек открыт. */
    private val showSettingsSheet = mutableStateOf(false)

    /** In-reader панель списка глав открыта. */
    private val showChapterList = mutableStateOf(false)

    /** Диалог «невалидная глава» открыт. */
    private val showInvalidChapterDialog = mutableStateOf(false)

    /**
     * Переход между главами пейджера в процессе: спиннер поверх вьюера
     * вместо «моргания» экрана + блок повторных свайпов на границе главы.
     */
    private val chapterTransitionInProgress = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ориентация — только системная: ридер не навязывает requestedOrientation
        // (настройка «системная» по умолчанию). Жёсткий portrait ранее ломал
        // поворот при снятой блокировке автоповорота. onConfigurationChanged
        // (ниже) пересоздаёт вьюер при смене ориентации.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewerHost = FrameLayout(this)

        val bookUrl = intent.getStringExtra(EXTRA_BOOK_URL)
        val chapterUrl = intent.getStringExtra(EXTRA_CHAPTER_URL)
        if (bookUrl == null || chapterUrl == null) {
            Timber.w("MangaReaderStart: missing extras bookUrl=%s chapterUrl=%s", bookUrl, chapterUrl)
            finish()
            return
        }
        Timber.d("MangaReaderStart: bookUrl=%s chapterUrl=%s", bookUrl, chapterUrl)
        viewModel.init(bookUrl, chapterUrl)

        setContent {
            // Общая тема NoveLA (тёмная/светлая/AMOLED из настроек приложения),
            // как у текстового ридера; enableEdgeToEdge — отсюда (Bug2).
            Theme(themeProvider) {
                MangaReaderComposeContent()
            }
        }

        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.saveState()
            finish()
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            viewModel.saveState()
        }
        viewer?.destroy()
        super.onDestroy()
    }

    /** Ориентация при последнем onConfigurationChanged (0 = ещё не было вызова). */
    private var lastConfigurationOrientation = 0

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Манифест (configChanges) спасает Activity от пересоздания при повороте,
        // но вьюер держит размеры под старую ориентацию — страницы не
        // перерастягиваются. Пересоздаём вьюер и переоткрываем текущую главу.
        val orientation = newConfig.orientation
        if (lastConfigurationOrientation != 0 && orientation == lastConfigurationOrientation) return
        lastConfigurationOrientation = orientation
        val ready = viewModel.uiState.value as? MangaReaderUiState.Ready ?: return
        viewer?.destroy()
        viewerHost.removeAllViews()
        viewer = null
        viewerMode = null
        ensureViewer(viewModel.settings.value.readingMode)
        viewerChapterUrl = ready.chapter.url
        viewer?.setChapter(ready.chapter, ready.currentPage)
    }

    // ---------- Вьюер ----------

    private fun ensureViewer(mode: MangaReadingMode) {
        if (viewerMode == mode && viewer != null) return
        viewer?.destroy()
        viewerHost.removeAllViews()
        val newViewer = when (mode) {
            MangaReadingMode.WEBTOON -> MangaWebtoonViewer(this, pageImageLoader, appPreferences)
            else -> createPagerViewer(this, pageImageLoader, appPreferences, lifecycleScope)
        }
        newViewer.onPageChanged = { page -> onViewerPageChanged(page) }
        newViewer.onLastPageReached = { onLastPageReached() }
        newViewer.onFirstPageReached = { onFirstPageReached() }
        newViewer.pageClickListener = { toolbarVisible.value = !toolbarVisible.value }
        if (newViewer is MangaWebtoonViewer) {
            // Мультиглавное окно вебтуна: state-sync при бесшовном пересечении
            // границы глав + запрос загрузки соседей (расширение окна).
            newViewer.onChapterChanged = { chapterUrl, chapterIndex, page, pages ->
                Timber.d(
                    "MangaReaderStart: onChapterChanged url=%s index=%d page=%d pages=%d",
                    chapterUrl, chapterIndex, page, pages.size,
                )
                viewerChapterUrl = chapterUrl
                viewModel.syncChapter(chapterUrl, chapterIndex, page, pages)
            }
            newViewer.onRequestChapter = { chapterIndex -> requestWindowChapter(chapterIndex) }
        }
        viewerHost.addView(
            newViewer.view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        viewer = newViewer
        viewerMode = mode
    }

    private fun onViewerPageChanged(page: Int) {
        val chapterUrl = viewerChapterUrl ?: return
        viewModel.setCurrentPage(page, chapterUrl)
        val ready = viewModel.uiState.value as? MangaReaderUiState.Ready ?: return
        val chapter = ready.chapter
        // Префетч ОКНА следующих страниц (лимит из настройки), а не всех оставшихся:
        // в длинных главах десятки страниц не заваливают очередь последовательных
        // загрузок, а окно успевает прогреться до подхода читателя. 0 = выключен.
        val count = appPreferences.READER_PAGE_PREFETCH_COUNT.value
        if (count > 0 && page + 1 < chapter.pageCount) {
            val end = (page + 1 + count).coerceAtMost(chapter.pageCount)
            pageImageLoader.prefetchPages(chapter.url, chapter.pages.subList(page + 1, end))
        }
        // Последняя страница любой главы — глава прочитана (статус в базе
        // подхватывается списком глав; конец книги отдельно в onLastPageReached).
        if (page == chapter.pageCount - 1) {
            viewModel.markChapterRead(chapter.url)
        }
    }

    private fun onLastPageReached() {
        // Вебтун: край главы обрабатывает окно (onRequestChapter расширяет ленту);
        // moveChapter здесь «выбросил» бы читателя к началу главы. Реальный край
        // книги — moveChapter(1) эмитит EndOfBook → Toast через события.
        if (viewerMode == MangaReadingMode.WEBTOON) {
            if (!viewModel.hasNextChapter()) viewModel.moveChapter(1)
            return
        }
        // Pager: одноглавый, переход через moveChapter. Повторные свайпы во
        // время перехода — no-op: спиннер уже показывает, что глава меняется.
        if (chapterTransitionInProgress.value) return
        val ready = viewModel.uiState.value as? MangaReaderUiState.Ready ?: return
        if (!viewModel.hasNextChapter()) {
            // Конец книги: EndOfBook без спиннера — перехода главы нет.
            viewModel.moveChapter(1)
            return
        }
        chapterTransitionInProgress.value = true
        viewModel.markChapterRead(ready.chapter.url)
        viewModel.moveChapter(1)
    }

    private fun onFirstPageReached() {
        // Вебтун: край главы обрабатывает окно; граница книги — ничего
        // (прыжок через moveChapter сломал бы бесшовный скролл назад).
        if (viewerMode == MangaReadingMode.WEBTOON) return
        // Pager: одноглавый, переход через moveChapter. Повторные свайпы во
        // время перехода — no-op.
        if (chapterTransitionInProgress.value) return
        if (viewModel.hasPrevChapter()) {
            chapterTransitionInProgress.value = true
            viewModel.moveChapter(-1)
        }
    }

    /**
     * Загрузка соседней главы для мультиглавного окна вебтуна: строит
     * [MangaChapter] через buildChapter (без мутации состояния) и возвращает
     * во вьюер как prepend/append. Граница книги (главы нет) — no-op.
     * In-flight дедуп: повторные запросы того же индекса игнорируются.
     */
    private fun requestWindowChapter(chapterIndex: Int) {
        val ready = viewModel.uiState.value as? MangaReaderUiState.Ready ?: return
        val url = ready.chapters.getOrNull(chapterIndex)?.url ?: return
        val webtoon = viewer as? MangaWebtoonViewer ?: return
        // Глава уже резидентна в окне — повторный prepend/append бессмыслен
        // (раньше уходил впустую: buildChapter из кэша + append rejected).
        if (webtoon.hasChapter(url)) return
        if (!requestedWindowChapters.add(chapterIndex)) return
        Timber.d("MangaReaderStart: requestWindowChapter index=%d url=%s", chapterIndex, url)
        lifecycleScope.launch {
            val chapter = viewModel.buildChapter(url)
            if (chapter == null) {
                // Глава не построилась (нет в списке книги / ошибка сети) —
                // снимаем дедуп, чтобы повторный запрос мог попробовать снова.
                requestedWindowChapters.remove(chapterIndex)
                return@launch
            }
            // DiffUtil-мутация окна ЗАПРЕЩЕНА из scroll callback
            // (onScrolled → onSettledPosition → onRequestChapter):
            // с кэшем buildChapter вся цепочка выполняется синхронно
            // В layout-пассе → IllegalStateException «Cannot call this
            // method in a scroll callback». Применяем в следующем кадре.
            val target = viewer as? MangaWebtoonViewer ?: return@launch
            target.view.post {
                // Вьюер откреплён (поворот/пересоздание) или состояние перезагружается —
                // prepend/append не применится, дедуп снимаем, чтобы повторный запрос
                // мог попробовать снова после пересоздания окна.
                if (!target.view.isAttachedToWindow) {
                    requestedWindowChapters.remove(chapterIndex)
                    return@post
                }
                val current = viewModel.uiState.value as? MangaReaderUiState.Ready
                if (current == null) {
                    requestedWindowChapters.remove(chapterIndex)
                    return@post
                }
                when {
                    chapter.index < current.currentChapterIndex -> {
                        Timber.d("MangaReaderStart: prepend index=%d url=%s", chapter.index, chapter.url)
                        target.prependPreviousChapter(chapter)
                    }
                    chapter.index > current.currentChapterIndex -> {
                        Timber.d("MangaReaderStart: append index=%d url=%s", chapter.index, chapter.url)
                        target.appendNextChapter(chapter)
                    }
                    else -> Timber.w("MangaReaderStart: window chapter equals current index=%d", chapter.index)
                }
                // Дедуп снимается ПОСЛЕ фактического prepend/append: если снять
                // раньше (в finally), повторный requestWindowChapter успевает пройти
                // add() до применения append → лишний buildChapter из кэша + append rejected.
                requestedWindowChapters.remove(chapterIndex)
            }
        }
    }

    // ---------- Настройки (применение) ----------

    private fun applySettings(isDark: Boolean) {
        val settings = viewModel.settings.value
        val window = window

        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Ориентация — только системная: манга-ридер не трогает requestedOrientation,
        // уважая системный замок поворота (фикс «залипшей» ориентации).
        // Фон читалки следует теме приложения (night-aware), как остальной UI.
        val bg = if (isDark) 0xFF202125.toInt() else android.graphics.Color.WHITE
        viewerHost.setBackgroundColor(bg)
        window.setBackgroundDrawable(ColorDrawable(bg))

        // Grayscale / inverted — слой с ColorMatrix (как в tachiyomisy).
        val matrix = ColorMatrix()
        var needsFilter = false
        if (settings.grayscale) {
            matrix.setSaturation(0f)
            needsFilter = true
        }
        if (settings.invertedColors) {
            if (needsFilter) {
                matrix.postConcat(ColorMatrix(INVERT_COLORS_MATRIX))
            } else {
                matrix.set(INVERT_COLORS_MATRIX)
            }
            needsFilter = true
        }
        val filterPaint = if (needsFilter) {
            android.graphics.Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        } else {
            null
        }
        viewerHost.setLayerType(
            if (needsFilter) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            filterPaint,
        )

        // Edge-to-edge уже включён enableEdgeToEdge (coreui Theme) — не трогаем
        // decorFits, только показываем/прячем системные бары.
        if (settings.fullscreen) setupFullScreenMode() else setupNormalScreenMode()
    }

    private fun setupFullScreenMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.displayCutout())
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupNormalScreenMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.displayCutout())
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun showSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------- Ввод ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewer?.handleKeyEvent(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (viewer?.handleGenericMotionEvent(event) == true) return true
        return super.onGenericMotionEvent(event)
    }

    // ---------- Compose UI ----------

    @Composable
    private fun MangaReaderComposeContent() {
        val settings by viewModel.settings.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        val ready = uiState as? MangaReaderUiState.Ready
        val mode = settings.readingMode

        // Одноразовые события: конец книги (Toast) и невалидная глава (диалог).
        // replay=1 гарантирует доставку InvalidChapter, эмиченного ещё в init.
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    MangaReaderEvent.EndOfBook -> Toast.makeText(
                        this@MangaReaderActivity,
                        R.string.manga_reader_end_of_book,
                        Toast.LENGTH_SHORT,
                    ).show()
                    MangaReaderEvent.InvalidChapter -> showInvalidChapterDialog.value = true
                }
            }
        }

        // Смена режима чтения пересоздаёт вьюер (ensureViewer) и продолжает с текущей
        // страницы. Смена книги тоже должна пересоздать вьюер — ключ включает bookUrl.
        LaunchedEffect(
            ready?.chapterLoadId,
            settings.readingMode,
            ready?.bookUrl,
        ) {
            val loaded = uiState as? MangaReaderUiState.Ready ?: return@LaunchedEffect
            val currentMode = viewModel.settings.value.readingMode
            ensureViewer(currentMode)
            // Новая глава стартует с её startPage; смена режима чтения (та же глава
            // уже во вьюере) продолжает с текущей страницы.
            val previousViewerChapterUrl = viewerChapterUrl
            val startPage = if (loaded.chapter.url == viewerChapterUrl) loaded.currentPage else loaded.chapter.startPage
            viewerChapterUrl = loaded.chapter.url
            Timber.d(
                "MangaReaderStart: setChapter url=%s index=%d startPage=%d previousViewerChapterUrl=%s",
                loaded.chapter.url, loaded.chapter.index, startPage, previousViewerChapterUrl,
            )
            // Новое окно вебтуна: старые in-flight запросы соседей недействительны.
            requestedWindowChapters.clear()
            viewer?.setChapter(loaded.chapter, startPage)
            // Переход главы завершён: новая глава реально установлена во вьюер.
            chapterTransitionInProgress.value = false
        }

        // Страховка от «залипшего» спиннера: если переход не завершился
        // (ошибка сети), флаг снимается по таймауту — читатель не блокируется.
        LaunchedEffect(chapterTransitionInProgress.value) {
            if (chapterTransitionInProgress.value) {
                delay(CHAPTER_TRANSITION_TIMEOUT_MS)
                chapterTransitionInProgress.value = false
            }
        }

        val isDark = LocalIsDark.current
        LaunchedEffect(settings, isDark) {
            applySettings(isDark = isDark)
        }

        // Пользовательская яркость (tachiyomisy custom brightness):
        // >0 — screenBrightness, <0 — почти минимум + чёрный оверлей, 0 — системная.
        LaunchedEffect(settings.customBrightness, settings.customBrightnessValue) {
            val brightness = when {
                !settings.customBrightness || settings.customBrightnessValue == 0 ->
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                settings.customBrightnessValue > 0 -> settings.customBrightnessValue / 100f
                else -> 0.01f
            }
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }

        LaunchedEffect(toolbarVisible.value) {
            if (viewModel.settings.value.fullscreen) {
                if (toolbarVisible.value) showSystemBars() else hideSystemBars()
            }
        }

        // Закрытие шита/панели: transient-бары могли скрыться, пока шит был
        // открыт, — вернуть их, если тулбар всё ещё виден (тап по scrim
        // закрывает шит, но сам по себе бары не показывает).
        LaunchedEffect(showSettingsSheet.value, showChapterList.value) {
            if (!showSettingsSheet.value && !showChapterList.value &&
                toolbarVisible.value && viewModel.settings.value.fullscreen
            ) {
                showSystemBars()
            }
        }

        Box(Modifier.fillMaxSize()) {
            // Android-хост вьюера: FrameLayout, в который ensureViewer кладёт view.
            AndroidView(
                factory = { viewerHost },
                modifier = Modifier.fillMaxSize(),
            )

            // Оверлей перехода между главами: спиннер вместо «моргания» экрана
            // (setChapter делает мгновенный scrollToPosition-сброс). Повторные
            // свайпы на границе блокирует guard в onLastPageReached/onFirstPageReached.
            if (chapterTransitionInProgress.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // Оверлей поверх вьюера (tachiyomisy ReaderContentOverlay):
            // сначала затемнение custom brightness, затем цветовой фильтр.
            if (settings.colorFilterEnabled || (settings.customBrightness && settings.customBrightnessValue < 0)) {
                val dim = if (settings.customBrightness && settings.customBrightnessValue < 0) {
                    (-settings.customBrightnessValue) / 100f
                } else {
                    0f
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            if (dim > 0f) {
                                drawRect(Color.Black.copy(alpha = dim))
                            }
                            if (settings.colorFilterEnabled) {
                                drawRect(
                                    color = Color(settings.colorFilterValue),
                                    blendMode = blendModeFor(settings.colorFilterMode),
                                )
                            }
                        },
                )
            }

            // Индикатор страницы (когда тулбар скрыт; при открытом тулбаре его
            // заменяет нижний навигатор с номерами страниц). Пока открыт модальный
            // слой (настройки/список глав) — счётчик прячем. Bug2: поверх
            // жестовой навигации через navigationBarsPadding, без фиксированных 24.dp.
            if (settings.showPageNumber && ready != null && !toolbarVisible.value &&
                !showSettingsSheet.value && !showChapterList.value
            ) {
                Text(
                    text = stringResource(
                        R.string.manga_reader_page_of,
                        ready.currentPage + 1,
                        ready.chapter.pageCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Верх: тулбар (назад + названия) и под ним раскрывающаяся панель
            // автопрокрутки (SY: ReaderTopBar + ExhUtils под ней).
            AnimatedVisibility(
                visible = toolbarVisible.value,
                enter = expandVertically(initialHeight = { 0 }) + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    MangaReaderToolbar(ready)
                    if (mode == MangaReadingMode.WEBTOON) {
                        AutoScrollPanel()
                    }
                }
            }

            // Низ: горизонтальный навигатор по главе — линия-слайдер страниц
            // с кнопками prev/next (SY: ChapterNavigator). Bug2: navigationBarsPadding.
            // При открытом шите/панели навигатор прячется (как индикатор страницы):
            // иначе он висит под scrim и «сползает» при transient-показе системных баров.
            AnimatedVisibility(
                visible = toolbarVisible.value && ready != null &&
                    !showSettingsSheet.value && !showChapterList.value,
                enter = expandVertically(initialHeight = { 0 }) + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MangaChapterNavigator(
                    ready = ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    onJumpToPage = { viewer?.moveToPage(it) },
                )
            }

            // Загрузка главы (только стартовая: при смене глав VM держит последнее
            // успешное состояние) / ошибка.
            when (uiState) {
                is MangaReaderUiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.manga_reader_chapter_loading),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
                is MangaReaderUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.manga_reader_chapter_failed),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.retryChapter() }) {
                            Text(stringResource(R.string.manga_reader_retry))
                        }
                    }
                }
                is MangaReaderUiState.Ready -> Unit
            }
        }

        if (showSettingsSheet.value) {
            MangaReaderSettingsSheet(
                settings = viewModel.settings.value,
                actions = viewModel.actions,
                onClose = { showSettingsSheet.value = false },
            )
        }

        if (showChapterList.value && ready != null) {
            MangaReaderChapterListPanel(
                chapters = ready.chapters,
                currentIndex = ready.currentChapterIndex,
                onChapterClick = { url ->
                    showChapterList.value = false
                    viewModel.loadChapter(url, restorePosition = true)
                },
                onClose = { showChapterList.value = false },
            )
        }

        if (showInvalidChapterDialog.value) {
            InvalidChapterDialog(onClose = {
                showInvalidChapterDialog.value = false
                finish()
            })
        }
    }

    /** Диалог «главу нельзя открыть в манга-ридере» — возврат в список глав. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun InvalidChapterDialog(onClose: () -> Unit) {
        BasicAlertDialog(onDismissRequest = onClose) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.manga_reader_invalid_chapter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MangaReaderToolbar(ready: MangaReaderUiState.Ready?) {
        val bookTitle = ready?.bookTitle
        val chapterTitle = ready?.chapter?.title
        // Состояние автопрокрутки для кнопки тулбара: filled/outlined иконка
        // и видимость панели скорости живут на одном префе.
        val autoscrollOn by appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.flow()
            .collectAsState(initial = appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value)

        // TopAppBar как в новел-ридере (ReaderScreen.kt): surfaceContainer@90%,
        // иконка 20.dp в IconButton 36.dp, titleMedium, HorizontalDivider под ним.
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                ),
                title = {
                    Column {
                        if (!bookTitle.isNullOrEmpty()) {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = chapterTitle ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.saveState()
                            finish()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showChapterList.value = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription = stringResource(R.string.manga_reader_chapter_list),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            showSettingsSheet.value = true
                            showChapterList.value = false
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.manga_reader_settings),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { openInBrowser() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = stringResource(R.string.manga_reader_open_in_browser),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Автопрокрутка: включена = заполненная иконка (Pause),
                    // выключена = контурная (PlayArrow). Тогл префа — панель
                    // скорости под тулбаром появляется/исчезает сама.
                    IconButton(
                        onClick = {
                            appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value = !autoscrollOn
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (autoscrollOn) Icons.Filled.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(R.string.manga_reader_auto_scroll),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            HorizontalDivider()
        }
    }

    /**
     * Панель автопрокрутки под тулбаром: строка скорости (минус/текст/плюс).
     * Видна ТОЛЬКО при включённой автопрокрутке — вкл/выкл теперь кнопкой
     * тулбара (filled/outlined), Switch и шеврон убраны.
     */
    @Composable
    private fun AutoScrollPanel(modifier: Modifier = Modifier) {
        val autoscrollOn by appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.flow()
            .collectAsState(initial = appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value)
        val speed by appPreferences.MANGA_READER_AUTOSCROLL_SPEED.flow()
            .collectAsState(initial = appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value)
        // Выключено — панель не нужна: управление вернулось в тулбар.
        if (!autoscrollOn) return

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f))
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value =
                        (speed - 10).coerceAtLeast(10)
                },
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = stringResource(R.string.manga_autoscroll_speed_value, speed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value =
                        (speed + 10).coerceAtMost(200)
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    /**
     * Горизонтальный навигатор по главе (порт tachiyomisy ChapterNavigator):
     * кнопки предыдущей/следующей главы и линия-слайдер по страницам главы.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MangaChapterNavigator(
        ready: MangaReaderUiState.Ready?,
        modifier: Modifier = Modifier,
        onJumpToPage: (Int) -> Unit,
    ) {
        val chapter = ready?.chapter ?: return
        val total = chapter.pageCount
        val current = ready.currentPage + 1
        val canPrev = viewModel.hasPrevChapter()
        val canNext = viewModel.hasNextChapter()

        val state = remember(total) {
            SliderState(
                value = current.toFloat(),
                steps = (total - 2).coerceAtLeast(0),
                valueRange = 1f..total.coerceAtLeast(1).toFloat(),
            )
        }
        state.value = current.toFloat()
        state.onValueChange = { value -> onJumpToPage(value.toInt() - 1) }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    chapterTransitionInProgress.value = true
                    viewModel.moveChapter(-1)
                },
                enabled = canPrev,
                // Фон как у полосы прогресса (полупрозрачный surfaceContainer,
                // скругление 12.dp) — вместо прежнего чёрного круга.
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.manga_reader_prev_chapter),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (total > 1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.manga_reader_page_of, current, total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        text = total.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(
                onClick = {
                    chapterTransitionInProgress.value = true
                    viewModel.moveChapter(1)
                },
                enabled = canNext,
                // Фон как у полосы прогресса (см. SkipPrevious).
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.manga_reader_next_chapter),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    /** Открыть текущую главу в браузере (URL источника, не текст). */
    private fun openInBrowser() {
        val ready = viewModel.uiState.value as? MangaReaderUiState.Ready ?: return
        val url = ready.chapter.url
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    /**
     * In-reader список глав (вместо отдельного ChaptersActivity): диалог в стиле
     * MangaReaderSettingsSheet, текущая глава подсвечена, тап — переход на главу.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MangaReaderChapterListPanel(
        chapters: List<Chapter>,
        currentIndex: Int,
        onChapterClick: (String) -> Unit,
        onClose: () -> Unit,
    ) {
        BasicAlertDialog(onDismissRequest = onClose) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 700.dp)
                    .fillMaxHeight(0.8f)
                    .navigationBarsPadding(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 4.dp, top = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.manga_reader_chapter_list),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                    if (chapters.isEmpty()) {
                        Text(
                            text = stringResource(R.string.manga_reader_chapter_list_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(chapters, key = { it.url }) { chapter ->
                                val isCurrent =
                                    chapters[currentIndex.coerceIn(0, chapters.size - 1)].url == chapter.url
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChapterClick(chapter.url) }
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isCurrent) {
                                        Text(
                                            text = stringResource(R.string.manga_reader_chapter_list_current),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun blendModeFor(mode: Int): BlendMode = when (mode) {
        1 -> BlendMode.Multiply
        2 -> BlendMode.Screen
        3 -> BlendMode.Overlay
        4 -> BlendMode.Lighten
        5 -> BlendMode.Darken
        else -> BlendMode.SrcOver
    }
}
