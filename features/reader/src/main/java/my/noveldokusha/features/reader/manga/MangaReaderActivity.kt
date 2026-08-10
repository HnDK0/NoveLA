package my.noveldokusha.features.reader.manga

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.net.Uri
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.AppThemeProvider
import my.noveldokusha.coreui.theme.LocalIsDark
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.features.reader.manga.ui.MangaReaderSettingsSheet
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.manga.viewer.pager.createPagerViewer
import my.noveldokusha.features.reader.manga.viewer.webtoon.MangaWebtoonViewer
import my.noveldokusha.features.reader.tools.PageImageLoader
import my.noveldokusha.reader.R
import my.noveldokusha.reader.databinding.ActivityMangaReaderBinding
import javax.inject.Inject
import kotlin.math.min

/**
 * Манга/манхва-читалка — порт tachiyomisy ReaderActivity (pager + webtoon
 * вьюеры, тап-зоны, настройки, автопрокрутка), текст-ридер не затронут:
 * сюда ведёт редирект только для глав-картинок (any Page && none Text).
 */
@AndroidEntryPoint
internal class MangaReaderActivity : ComponentActivity() {

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
    lateinit var navigationRoutes: NavigationRoutes

    @Inject
    lateinit var themeProvider: AppThemeProvider

    @Inject
    lateinit var pageImageLoader: PageImageLoader

    private val viewModel by viewModels<MangaReaderViewModel>()

    private lateinit var binding: ActivityMangaReaderBinding

    private var viewer: Viewer? = null
    private var viewerMode: MangaReadingMode? = null

    /** Тулбар (меню) виден — тап в зону MENU переключает. */
    private val toolbarVisible = mutableStateOf(false)

    /** Шит настроек открыт. */
    private val showSettingsSheet = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMangaReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookUrl = intent.getStringExtra(EXTRA_BOOK_URL)
        val chapterUrl = intent.getStringExtra(EXTRA_CHAPTER_URL)
        if (bookUrl == null || chapterUrl == null) {
            finish()
            return
        }
        viewModel.init(bookUrl, chapterUrl)

        binding.mangaComposeHost.setContent {
            // Общая тема NoveLA (тёмная/светлая/AMOLED из настроек приложения),
            // как у текстового ридера — настройки и тулбар не светятся в тёмном.
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

    // ---------- Вьюер ----------

    private fun ensureViewer(mode: MangaReadingMode) {
        if (viewerMode == mode && viewer != null) return
        viewer?.destroy()
        val container = binding.mangaViewerContainer
        container.removeAllViews()
        val newViewer = when (mode) {
            MangaReadingMode.WEBTOON -> MangaWebtoonViewer(this, pageImageLoader, appPreferences)
            else -> createPagerViewer(mode, this, pageImageLoader, appPreferences, lifecycleScope)
        }
        newViewer.onPageChanged = { page -> onViewerPageChanged(page) }
        newViewer.onLastPageReached = { onLastPageReached() }
        newViewer.onFirstPageReached = { onFirstPageReached() }
        newViewer.pageClickListener = { toolbarVisible.value = !toolbarVisible.value }
        container.addView(newViewer.view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        viewer = newViewer
        viewerMode = mode
    }

    private fun onViewerPageChanged(page: Int) {
        viewModel.setCurrentPage(page)
        val chapter = viewModel.chapter.value ?: return
        // Префетч страниц вперёд (значение настройки применяется к обоим
        // вьюерам: у пейджера кэш прогревается, у ленты — холдеры).
        val count = appPreferences.READER_PAGE_PREFETCH_COUNT.value
        if (count > 0 && page + 1 < chapter.pageCount) {
            val end = min(page + 1 + count, chapter.pageCount)
            pageImageLoader.prefetchPages(chapter.url, chapter.pages.subList(page + 1, end))
        }
        // Последняя страница последней главы — глава прочитана.
        if (page == chapter.pageCount - 1 && !viewModel.hasNextChapter()) {
            viewModel.markChapterRead(chapter.url)
        }
    }

    private fun onLastPageReached() {
        val chapter = viewModel.chapter.value ?: return
        viewModel.markChapterRead(chapter.url)
        if (viewModel.hasNextChapter()) {
            viewModel.moveChapter(1)
        } else {
            Toast.makeText(this, R.string.manga_reader_end_of_book, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onFirstPageReached() {
        if (viewModel.hasPrevChapter()) {
            viewModel.moveChapter(-1)
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

        requestedOrientation = settings.orientation.flag

        // Фон читалки следует теме приложения (night-aware), как остальной UI
        // NoveLA; отдельной "темы читалки" в манга-режиме больше нет.
        val bg = if (isDark) 0xFF202125.toInt() else android.graphics.Color.WHITE
        binding.mangaViewerContainer.setBackgroundColor(bg)
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
        binding.mangaViewerContainer.setLayerType(
            if (needsFilter) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            filterPaint,
        )

        if (settings.fullscreen) setupFullScreenMode() else setupNormalScreenMode()
    }

    private fun setupFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        WindowCompat.setDecorFitsSystemWindows(window, true)
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
        val settings = viewModel.settings.value
        val chapter = viewModel.chapter.value
        val mode = settings.readingMode

        LaunchedEffect(viewModel.chapterLoadId.value) {
            val loadedChapter = viewModel.chapter.value ?: return@LaunchedEffect
            val mode = viewModel.settings.value.readingMode
            ensureViewer(mode)
            viewer?.setChapter(loadedChapter, viewModel.currentPage.value)
        }

        val isDark = LocalIsDark.current
        LaunchedEffect(viewModel.settings.value, isDark) {
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

        Box(Modifier.fillMaxSize()) {
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
            // заменяет нижний навигатор с номерами страниц).
            if (settings.showPageNumber && chapter != null && !viewModel.loading.value && !toolbarVisible.value) {
                Text(
                    text = stringResource(R.string.manga_reader_page_of, viewModel.currentPage.value + 1, chapter.pageCount),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Верх: тулбар (назад + названия) и под ним раскрывающаяся панель
            // автопрокрутки (SY: ReaderTopBar + ExhUtils под ней).
            if (toolbarVisible.value) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    MangaReaderToolbar()
                    if (mode == MangaReadingMode.WEBTOON) {
                        AutoScrollPanel()
                    }
                }
            }

            // Низ: горизонтальный навигатор по главе — линия-слайдер страниц
            // с кнопками prev/next по бокам (SY: ChapterNavigator), под ним
            // планка: настройки, список глав, открыть в браузере (SY: bottom bar).
            if (toolbarVisible.value && chapter != null && !viewModel.loading.value) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    MangaChapterNavigator(
                        modifier = Modifier.fillMaxWidth(),
                        onJumpToPage = { viewer?.moveToPage(it) },
                    )
                    MangaReaderBottomBar()
                }
            }

            // Загрузка главы / ошибка.
            if (viewModel.loading.value) {
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
            } else if (viewModel.loadError.value) {
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
        }

        if (showSettingsSheet.value) {
            MangaReaderSettingsSheet(
                settings = viewModel.settings.value,
                actions = viewModel.actions,
                onClose = { showSettingsSheet.value = false },
            )
        }
    }

    @Composable
    private fun MangaReaderToolbar(modifier: Modifier = Modifier) {
        val bookTitle = viewModel.bookTitle.value
        val chapterTitle = viewModel.chapter.value?.title
        Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0xEE000000))
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                viewModel.saveState()
                finish()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                if (!bookTitle.isNullOrEmpty()) {
                    Text(
                        text = bookTitle,
                        color = Color(0xFFBDBDBD),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = chapterTitle ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    /**
     * Панель автопрокрутки под тулбаром (SY: ExhUtils). Строка тап
     * вкл/выкл (Play/Pause на тулбар-эквиваленте); связаны через один преф —
     * пока не нажата пауза, лента скроллится. Шеврон разворачивает скорость.
     */
    @Composable
    private fun AutoScrollPanel(modifier: Modifier = Modifier) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)
        val autoscrollOn by appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.flow()
            .collectAsState(initial = appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value)
        val speed by appPreferences.MANGA_READER_AUTOSCROLL_SPEED.flow()
            .collectAsState(initial = appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xEE000000))
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value = !autoscrollOn
                    }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (autoscrollOn) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.manga_reader_auto_scroll),
                    tint = Color.White,
                )
                Text(
                    text = stringResource(R.string.manga_expand_autoscroll),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Switch(
                    checked = autoscrollOn,
                    onCheckedChange = {
                        appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value = it
                    },
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value =
                                (speed - 10).coerceAtLeast(10)
                        },
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = null, tint = Color.White)
                    }
                    Text(
                        text = stringResource(R.string.manga_autoscroll_speed_value, speed),
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value =
                                (speed + 10).coerceAtMost(200)
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    }
                }
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
        modifier: Modifier = Modifier,
        onJumpToPage: (Int) -> Unit,
    ) {
        val chapter = viewModel.chapter.value ?: return
        val total = chapter.pageCount
        val current = viewModel.currentPage.value + 1
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
                onClick = { viewModel.moveChapter(-1) },
                enabled = canPrev,
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.manga_reader_prev_chapter),
                    tint = Color.White,
                )
            }
            if (total > 1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xEE000000))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.manga_reader_page_of, current, total),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                    Slider(
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        text = total.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(
                onClick = { viewModel.moveChapter(1) },
                enabled = canNext,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.manga_reader_next_chapter),
                    tint = Color.White,
                )
            }
        }
    }

    /**
     * Нижняя планка (SY: ReaderBottomBar): настройки, список глав,
     * открыть главу в браузере. Тёмная, как остальные бары читалки.
     */
    @Composable
    private fun MangaReaderBottomBar(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xEE000000))
                .navigationBarsPadding()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { showSettingsSheet.value = true }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.manga_reader_settings),
                    tint = Color.White,
                )
            }
            IconButton(onClick = { openChapterList() }) {
                Icon(
                    Icons.Filled.List,
                    contentDescription = stringResource(R.string.manga_reader_chapter_list),
                    tint = Color.White,
                )
            }
            IconButton(onClick = { openInBrowser() }) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.manga_reader_open_in_browser),
                    tint = Color.White,
                )
            }
        }
    }

    /** Список глав книги (ChaptersActivity), как из библиотеки. */
    private fun openChapterList() {
        runCatching {
            startActivity(
                navigationRoutes.chapters(
                    this,
                    BookMetadata(
                        title = viewModel.bookTitle.value.orEmpty(),
                        url = viewModel.bookUrl,
                    ),
                ),
            )
        }
    }

    /** Открыть текущую главу в браузере (URL источника, не текст). */
    private fun openInBrowser() {
        val url = viewModel.chapter.value?.url ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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