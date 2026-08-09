package my.noveldokusha.features.reader

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import timber.log.Timber
import android.view.WindowManager
import android.widget.AbsListView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.ImageQuality
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.BaseActivity
import my.noveldokusha.coreui.composableActions.SetSystemBarTransparent
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.coreui.theme.readerTheme
import my.noveldokusha.core.utils.Extra_Boolean
import my.noveldokusha.core.utils.Extra_String
import my.noveldokusha.core.utils.dpToPx
import my.noveldokusha.core.utils.fadeIn
import my.noveldokusha.data.AppRepository
import my.noveldokusha.core.models.RegexRule
import my.noveldokusha.settings.RegexCleanupSettingsViewModel
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.domain.ReaderItemAdapter
import my.noveldokusha.features.reader.domain.ReaderState
import my.noveldokusha.features.reader.domain.indexOfReaderItem
import my.noveldokusha.features.reader.manager.ReaderManager
import my.noveldokusha.features.reader.services.NarratorMediaControlsService
import my.noveldokusha.features.reader.tools.FontsLoader
import my.noveldokusha.features.reader.tools.PageImageLoader
import my.noveldokusha.features.reader.ui.ReaderScreen
import my.noveldokusha.features.reader.ui.ReaderScreenState
import my.noveldokusha.features.reader.ui.ReaderViewHandlersActions
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.reader.R
import my.noveldokusha.reader.databinding.ActivityReaderBinding
import my.noveldokusha.text_to_speech.Utterance
import javax.inject.Inject

@AndroidEntryPoint
class ReaderActivity : BaseActivity() {
    class IntentData : Intent, ReaderStateBundle {
        override var bookUrl by Extra_String()
        override var chapterUrl by Extra_String()
        override var introScrollToSpeaker by Extra_Boolean()

        constructor(intent: Intent) : super(intent)
        constructor(
            ctx: Context,
            bookUrl: String,
            chapterUrl: String,
            scrollToSpeakingItem: Boolean = false
        ) : super(
            ctx,
            ReaderActivity::class.java
        ) {
            this.bookUrl = bookUrl
            this.chapterUrl = chapterUrl
            this.introScrollToSpeaker = scrollToSpeakingItem
        }
    }

    @Inject
    lateinit var navigationRoutes: NavigationRoutes

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    internal lateinit var readerViewHandlersActions: ReaderViewHandlersActions

    @Inject
    internal lateinit var readerManager: ReaderManager

    @Inject
    internal lateinit var appRepository: AppRepository

    @Inject
    internal lateinit var pageImageLoader: PageImageLoader

    private var listIsScrolling = false
    // Время последнего события скролла: используется как watchdog для сброса
    // «залипшего» listIsScrolling, если fling был прерван (notifyDataSetChanged
    // штормом/подгрузкой главы) и IDLE-событие так и не пришло.
    private var lastScrollEventTime = 0L
    // Последний scrollState (IDLE/TOUCH_SCROLL/FLING): watchdog сбрасывает залипший
    // listIsScrolling только для прерванного FLING. При TOUCH_SCROLL палец может лежать
    // неподвижно дольше порога (чтение/выделение) — сброс дёргал бы список под пальцем.
    private var lastScrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE
    // Последний абзац, для которого был выполнен полный rebind (notifyDataSetChanged).
    // currentReaderItem эмитит на каждый PLAYING/LOADING того же абзаца — повторный
    // rebind не нужен, пока позиция не сменилась (подсветка рисуется в getView).
    private var lastReboundChapterIndex = -1
    private var lastReboundChapterItemPosition = -1
    private var lastReboundPlayState: Utterance.PlayState? = null
    private val fadeInTextLiveData = MutableLiveData(false)
    // Предотвращает загрузку следующей главы до первого реального скролла пользователя.
    // Сбрасывается в false при каждом открытии Activity, устанавливается в true только при
    // TOUCH_SCROLL или FLING — т.е. при реальном жесте, не при programmatic setSelectionFromTop.
    private var userHasScrolled = false

    // Double-tap detection for showing/hiding reader info
    private var lastTapTime = 0L
    private val doubleTapThresholdMs = 350L

    private val viewModel by viewModels<ReaderViewModel>()

    private val viewBind by lazy { ActivityReaderBinding.inflate(layoutInflater) }
    private val viewAdapter = object {
        val listView by lazy {
            ReaderItemAdapter(
                this@ReaderActivity,
                viewModel.items,
                viewModel.bookUrl,
                currentTextSelectability = { appPreferences.READER_SELECTABLE_TEXT.value },
                currentFontSize = { appPreferences.READER_FONT_SIZE.value },
                currentLineHeight = { appPreferences.READER_LINE_HEIGHT.value },
                currentParagraphSpacing = { appPreferences.READER_PARAGRAPH_SPACING.value },
                currentLetterSpacing = { appPreferences.READER_LETTER_SPACING.value },
                currentTypeface = { fontsLoader.getTypeFaceNORMAL(appPreferences.READER_FONT_FAMILY.value) },
                currentTypefaceBold = { fontsLoader.getTypeFaceBOLD(appPreferences.READER_FONT_FAMILY.value) },
                currentSpeakerActiveItem = { viewModel.readerSpeaker.currentTextPlaying.value },
                currentParallelEnabled = { appPreferences.TRANSLATION_PARALLEL_ENABLED.value },
                currentParallelOrder = { appPreferences.TRANSLATION_PARALLEL_ORDER.value },
                onChapterStartVisible = viewModel::markChapterStartAsSeen,
                onChapterEndVisible = viewModel::markChapterEndAsSeen,
                onReloadReader = viewModel::reloadReader,
                onRetryChapter = { chapterIndex ->
                    // Удаляет все items главы с ошибкой (включая Title/Divider),
                    // сбрасывает chaptersStats и loadedChapters для этой главы,
                    // затем перезагружает её заново. Остальные уже загруженные главы не трогает.
                    viewModel.chaptersLoader.retryChapter(chapterIndex)
                },
                onOpenChapterInBrowser = { url ->
                    navigationRoutes.webView(this@ReaderActivity, url = url)
                        .let(::startActivity)
                },
                onClick = {
                    if (appPreferences.READER_SINGLE_TAP_TO_OPEN_SETTINGS.value) {
                        viewModel.state.showReaderInfo.value = !viewModel.state.showReaderInfo.value
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < doubleTapThresholdMs) {
                            viewModel.state.showReaderInfo.value = !viewModel.state.showReaderInfo.value
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                        }
                    }
                },
                currentTtsHighlightEnabled = { appPreferences.TTS_HIGHLIGHT_ENABLED.value },
                currentTtsHighlightColor = { appPreferences.TTS_HIGHLIGHT_COLOR.value },
                currentSpokenWordRange = { viewModel.readerSpeaker.state.spokenWordRange.value },
                currentManualHighlight = { viewModel.state.settings.manualHighlight.highlightedItem.value },
                pageImageLoader = pageImageLoader,
                itemViewScope = lifecycleScope,
            )
        }
    }

    private val fontsLoader by lazy { FontsLoader(this) }

    private fun initPageImageLoaderQuality() {
        pageImageLoader.quality = ImageQuality.parse(appPreferences.READER_IMAGE_QUALITY.value)
    }

    /**
     * Префетч страниц манхвы/манги на ~8 рядов вперёд по скроллу.
     * URL известны из getPageList заранее; load() дедуплицирует
     * одновременные запросы, повторный показ после ошибки — отдельный load.
     */
    private fun prefetchPagesNear(firstVisibleItem: Int, visibleItemCount: Int) {
        val adapter = viewAdapter.listView
        val start = (firstVisibleItem + visibleItemCount).coerceIn(0, adapter.count - 1)
        val end = (start + 8).coerceAtMost(adapter.count - 1)
        val pages = ArrayList<ReaderItem.Page>(end - start + 1)
        for (pos in start..end) {
            (adapter.getItem(pos) as? ReaderItem.Page)?.let { pages.add(it) }
        }
        pageImageLoader.prefetch(pages)
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.onCloseManually()
            finish()
        }
    }

    override fun onDestroy() {
        readerViewHandlersActions.invalidate()
        if (isFinishing) {
            viewModel.onCloseManually()
        }
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPageImageLoaderQuality()

        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        viewBind.listView.adapter = viewAdapter.listView
        readerViewHandlersActions.listView = viewBind.listView

        fadeInTextLiveData.distinctUntilChanged().observe(this) {
            if (it) {
                viewBind.listView.fadeIn(durationMillis = 150)
            }
        }

        lifecycleScope.launch {
            viewModel.onTranslatorChanged.collect {
                viewModel.reloadReader()
            }
        }
        lifecycleScope.launch {
            viewModel.onDisplaySettingsChanged.collect {
                viewAdapter.listView.notifyDataSetChanged()
            }
        }
        lifecycleScope.launch {
            readerManager.onCloseRequested.receiveAsFlow().collect {
                if (!isFinishing) finish()
            }
        }
        readerViewHandlersActions.forceUpdateListViewState = {
            withContext(Dispatchers.Main.immediate) {
                viewAdapter.listView.notifyDataSetChanged()
            }
        }

        readerViewHandlersActions.maintainStartPosition = {
            withContext(Dispatchers.Main.immediate) {
                it()
                val titleIndex = (0 until viewAdapter.listView.count)
                    .indexOfFirst { viewAdapter.listView.getItem(it) is ReaderItem.Title }

                if (titleIndex != -1) {
                    viewBind.listView.setSelection(titleIndex)
                }
            }
        }

        readerViewHandlersActions.setInitialPosition = {
            withContext(Dispatchers.Main.immediate) {
                initialScrollToChapterItemPosition(
                    chapterIndex = it.chapterIndex,
                    chapterItemPosition = it.chapterItemPosition,
                    offset = it.chapterItemOffset
                )
            }
        }

        readerViewHandlersActions.maintainLastVisiblePosition = {
            withContext(Dispatchers.Main.immediate) {
                val oldSize = viewAdapter.listView.count
                val position = viewBind.listView.lastVisiblePosition
                val positionView = position - viewBind.listView.firstVisiblePosition
                val top = viewBind.listView.getChildAt(positionView).run { top - paddingTop }
                it()
                val displacement = viewAdapter.listView.count - oldSize
                viewBind.listView.setSelectionFromTop(position + displacement, top)
            }
        }

        viewModel.ttsScrolledToTheTop.asLiveData().observe(this) {
            viewAdapter.listView.notifyDataSetChanged()
            if (viewAdapter.listView.count < 1) {
                return@observe
            }
            viewBind.listView.smoothScrollToPositionFromTop(
                1,
                300.dpToPx(this),
                250
            )
        }

        viewModel.ttsScrolledToTheBottom.asLiveData().observe(this) {
            viewAdapter.listView.notifyDataSetChanged()
            if (viewAdapter.listView.count < 2) {
                return@observe
            }
            viewBind.listView.smoothScrollToPositionFromTop(
                viewAdapter.listView.count - 2,
                300.dpToPx(this),
                250
            )
        }

        viewModel.readerSpeaker.currentReaderItem
            .filter { it.playState == Utterance.PlayState.PLAYING || it.playState == Utterance.PlayState.LOADING }
            .asLiveData().observe(this) {
                scrollToReadingPositionOptional(
                    chapterIndex = it.itemPos.chapterIndex,
                    chapterItemPosition = it.itemPos.chapterItemPosition,
                    playState = it.playState,
                )
            }

        viewModel.readerSpeaker.scrollToReaderItem.asLiveData().observe(this) {
            if (it !is ReaderItem.Position) return@observe
            scrollToReadingPositionForced(
                chapterIndex = it.chapterIndex,
                chapterItemPosition = it.chapterItemPosition,
            )
        }

        viewModel.manualHighlightScrollToItem.asLiveData().observe(this) {
            scrollToReadingPositionForced(
                chapterIndex = it.chapterIndex,
                chapterItemPosition = it.chapterItemPosition,
            )
        }

        viewModel.readerSpeaker.scrollToChapterTop.asLiveData()
            .observe(this) { chapterIndex ->
                scrollToReadingPositionForced(
                    chapterIndex = chapterIndex,
                    chapterItemPosition = 0,
                )
            }

        viewModel.readerSpeaker.startReadingFromFirstVisibleItem.asLiveData().observe(this) {
            viewModel.startSpeaker(
                itemIndex = getFirstFullyVisibleItemIndex()
            )
        }

        // Notify manually text font changed for list view
        snapshotFlow { viewModel.state.settings.style.textFont.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify manually text size changed for list view
        snapshotFlow { viewModel.state.settings.style.textSize.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify manually line height changed for list view
        snapshotFlow { viewModel.state.settings.style.lineHeight.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify manually paragraph spacing changed for list view
        snapshotFlow { viewModel.state.settings.style.paragraphSpacing.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify manually letter spacing changed for list view
        snapshotFlow { viewModel.state.settings.style.letterSpacing.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify manually selectable text changed for list view
        snapshotFlow { viewModel.state.settings.isTextSelectable.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify TTS highlight changed for list view
        snapshotFlow {
            appPreferences.TTS_HIGHLIGHT_ENABLED.value to appPreferences.TTS_HIGHLIGHT_COLOR.value
        }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Notify manual highlight changed for list view
        snapshotFlow { viewModel.state.settings.manualHighlight.highlightedItem.value }.drop(1)
            .asLiveData()
            .observe(this) { viewAdapter.listView.notifyDataSetChanged() }

        // Periodic refresh for TTS word highlighting while playing
        lifecycleScope.launch {
            var lastRange: IntRange? = null
            snapshotFlow { viewModel.readerSpeaker.state.spokenWordRange.value }
                .debounce(50)
                .collect {
                    if (appPreferences.TTS_HIGHLIGHT_ENABLED.value && it != null && it != lastRange) {
                        lastRange = it
                        viewAdapter.listView.notifyDataSetChanged()
                    }
                    if (it == null) lastRange = null
                }
        }

        // Set current screen to be kept bright always or not
        snapshotFlow { viewModel.state.settings.keepScreenOn.value }
            .asLiveData()
            .observe(this) { keepScreenOn ->
                val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                if (keepScreenOn) window.addFlags(flag) else window.clearFlags(flag)
            }

        setContent {
            val regexCleanupViewModel: RegexCleanupSettingsViewModel = viewModel(
                key = "regexCleanupSettings",
                factory = viewModelFactory {
                    initializer {
                        RegexCleanupSettingsViewModel(
                            appPreferences = appPreferences,
                            appRepository = appRepository,
                            stateHandler = SavedStateHandle(
                                mapOf("bookUrl" to viewModel.bookUrl)
                            )
                        )
                    }
                }
            )

            var regexRulesSnapshot by remember { mutableStateOf<List<RegexRule>?>(null) }
            LaunchedEffect(viewModel.state.settings.selectedSetting.value) {
                val selected = viewModel.state.settings.selectedSetting.value
                if (selected == ReaderScreenState.Settings.Type.RegexRules) {
                    regexRulesSnapshot = appPreferences.effectiveRegexRules(viewModel.bookUrl)
                } else {
                    val before = regexRulesSnapshot
                    regexRulesSnapshot = null
                    if (before != null &&
                        before != appPreferences.effectiveRegexRules(viewModel.bookUrl)
                    ) {
                        viewModel.reloadReader()
                    }
                }
            }

            Theme(themeProvider) {
                readerTheme {
                    SetSystemBarTransparent()

                    // Reader info
                    ReaderScreen(
                    state = viewModel.state,
                    onTextFontChanged = { appPreferences.READER_FONT_FAMILY.value = it },
                    onTextSizeChanged = { appPreferences.READER_FONT_SIZE.value = it },
                    onLineHeightChanged = { appPreferences.READER_LINE_HEIGHT.value = it },
                    onParagraphSpacingChanged = { appPreferences.READER_PARAGRAPH_SPACING.value = it },
                    onLetterSpacingChanged = { appPreferences.READER_LETTER_SPACING.value = it },
                    onSelectableTextChange = { appPreferences.READER_SELECTABLE_TEXT.value = it },
                    onKeepScreenOn = { appPreferences.READER_KEEP_SCREEN_ON.value = it },
                    onDarkModeSelected = { appPreferences.THEME_DARK_MODE.value = it.name; recreate() },
                    onAppThemeChanged = { appPreferences.APP_THEME.value = it.name; recreate() },
                    onFullScreen = { appPreferences.READER_FULL_SCREEN.value = it; recreate() },
                    onSingleTapToOpenSettingsChange = { appPreferences.READER_SINGLE_TAP_TO_OPEN_SETTINGS.value = it },
                    onTtsHighlightEnabledChange = { appPreferences.TTS_HIGHLIGHT_ENABLED.value = it },
                    onTtsHighlightColorChange = { appPreferences.TTS_HIGHLIGHT_COLOR.value = it },
                    onManualHighlightEnabledChange = {
                        appPreferences.MANUAL_HIGHLIGHT_ENABLED.value = it
                        if (!it) viewModel.stopManualHighlight()
                    },
                    onImageQualityChange = {
                        appPreferences.READER_IMAGE_QUALITY.value = it
                        // Действует сразу: новые/префетченные страницы
                        // грузятся в новом качестве без пересборки главы.
                        pageImageLoader.quality = ImageQuality.parse(it)
                    },
                    manualHighlight = viewModel.state.settings.manualHighlight,
                    onManualHighlightStart = {
                        val firstVisible = getFirstFullyVisibleItemIndex()
                        // Клампим: нижняя подложка даёт count-1 (за концом списка)
                        viewModel.startManualHighlightAt(
                            firstVisible.coerceAtMost(
                                (viewModel.items.size - 1).coerceAtLeast(0)
                            )
                        )
                    },
                    manualHighlightInitialPosition = run {
                        val x = appPreferences.MANUAL_HIGHLIGHT_POS_X.value
                        val y = appPreferences.MANUAL_HIGHLIGHT_POS_Y.value
                        if (x >= 0f && y >= 0f) x to y else null
                    },
                    onManualHighlightPositionChange = { x, y ->
                        appPreferences.MANUAL_HIGHLIGHT_POS_X.value = x
                        appPreferences.MANUAL_HIGHLIGHT_POS_Y.value = y
                    },
                    onPressBack = {
                        viewModel.onCloseManually()
                        finish()
                    },
                    onOpenChapterInWeb = {
                        val url = viewModel.chapterUrl
                        if (url.isNotBlank()) {
                            navigationRoutes.webView(this, url = url).let(::startActivity)
                        }
                    },
                    regexCleanupViewModel = regexCleanupViewModel,
                    readerContent = {
                        AndroidView(factory = { viewBind.root })
                    },
                    )

                    if (viewModel.state.showInvalidChapterDialog.value) {
                        BasicAlertDialog(onDismissRequest = {
                            viewModel.state.showInvalidChapterDialog.value = false
                        }) {
                            Text(stringResource(id = R.string.invalid_chapter))
                        }
                    }
                }
            }
        }

        viewBind.listView.setOnScrollListener(
            object : AbsListView.OnScrollListener {
                override fun onScroll(
                    view: AbsListView?,
                    firstVisibleItem: Int,
                    visibleItemCount: Int,
                    totalItemCount: Int
                ) {
                    lastScrollEventTime = SystemClock.elapsedRealtime()
                    prefetchPagesNear(firstVisibleItem, visibleItemCount)
                    updateCurrentReadingPosSavingState(
                        firstVisibleItemIndex = viewAdapter.listView.fromPositionToIndex(
                            viewBind.listView.firstVisiblePosition
                        )
                    )
                    updateInfoView()
                    // Only trigger chapter loading when the user is actually scrolling,
                    // not during programmatic layout changes (e.g. after notifyDataSetChanged).
                    if (listIsScrolling) {
                        updateReadingState()
                    }
                }

                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                    lastScrollEventTime = SystemClock.elapsedRealtime()
                    lastScrollState = scrollState
                    listIsScrolling = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING ||
                        scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
                    ) {
                        userHasScrolled = true
                    }
                    // When the user lifts their finger, check if we need to load more chapters
                    if (!listIsScrolling) {
                        updateReadingState()
                        // TTS catch-up: после окончания жеста сразу возвращаем подсветку
                        // на экран, не дожидаясь следующей эмиссии абзаца (может быть
                        // через секунды). Само-скролл не дёргает: optional-путь ничего
                        // не делает, если текущий абзац уже видим.
                        if (viewModel.readerSpeaker.isSpeaking.value) {
                            val playing = viewModel.readerSpeaker.currentTextPlaying.value
                            scrollToReadingPositionOptional(
                                chapterIndex = playing.itemPos.chapterIndex,
                                chapterItemPosition = playing.itemPos.chapterItemPosition,
                                playState = playing.playState,
                            )
                        }
                    }
                }
            })

        snapshotFlow { viewModel.state.settings.fullScreen.value }
            .asLiveData()
            .observe(this) { fullscreen ->
                when {
                    fullscreen -> setupFullScreenMode()
                    else -> setupNormalScreenMode()
                }
            }
        setupSystemBarAppearance()


        viewAdapter.listView.notifyDataSetChanged()
        lifecycleScope.launch {
            delay(200)
            fadeInTextLiveData.postValue(true)
        }



        when {
            // Use case: user opens app from media control intent
            viewModel.introScrollToSpeaker -> {
                viewModel.introScrollToSpeaker = false
                val itemPos = viewModel.readerSpeaker.currentTextPlaying.value.itemPos
                scrollToReadingPositionImmediately(
                    chapterIndex = itemPos.chapterIndex,
                    chapterItemPosition = itemPos.chapterItemPosition,
                )
            }
            // Use case: user opens reader on the same book, on the same chapter url (session is maintained)
            readerViewHandlersActions.introScrollToCurrentChapter -> {
                readerViewHandlersActions.introScrollToCurrentChapter = false
                val chapterState = viewModel.readingCurrentChapter
                val chapterStats =
                    viewModel.chaptersLoader.chaptersStats[chapterState.chapterUrl] ?: return
                initialScrollToChapterItemPosition(
                    chapterIndex = chapterStats.orderedChaptersIndex,
                    chapterItemPosition = chapterState.chapterItemPosition,
                    offset = chapterState.offset
                )
            }
        }
    }

    private fun setupNormalScreenMode() {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.displayCutout())
        controller.show(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setupFullScreenMode() {
        enableEdgeToEdge()
        // Fullscreen mode that ignores any cutout, notch etc.
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

    private fun setupSystemBarAppearance() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        combine(
            snapshotFlow { viewModel.state.showReaderInfo.value },
            snapshotFlow { viewModel.state.settings.fullScreen.value }
        ) { showReaderInfo, fullScreen -> showReaderInfo to fullScreen }
            .distinctUntilChangedBy { (showReaderInfo, fullScreen) -> showReaderInfo || !fullScreen }
            .asLiveData().observe(this) { (showReaderInfo, fullScreen) ->
                val show = showReaderInfo || !fullScreen
                when {
                    show -> controller.show(WindowInsetsCompat.Type.statusBars())
                    else -> controller.hide(WindowInsetsCompat.Type.statusBars())
                }
            }
    }

    private fun scrollToReadingPositionOptional(
        chapterIndex: Int,
        chapterItemPosition: Int,
        playState: Utterance.PlayState,
    ) {
        // Always update the view to show current TTS item highlighting.
        // Полный rebind — только при смене абзаца или его состояния: currentReaderItem
        // эмитит на каждый PLAYING/LOADING того же абзаца, повторный
        // notifyDataSetChanged на каждой эмиссии вызывает rebind-шторм
        // (Skipped frames). Подсветка нового абзаца рисуется в getView, поэтому
        // одного rebind на смену позиции достаточно. playState включён в ключ,
        // т.к. LOADING и PLAYING одного абзаца рисуются адаптером по-разному.
        if (chapterIndex != lastReboundChapterIndex ||
            chapterItemPosition != lastReboundChapterItemPosition ||
            playState != lastReboundPlayState
        ) {
            lastReboundChapterIndex = chapterIndex
            lastReboundChapterItemPosition = chapterItemPosition
            lastReboundPlayState = playState
            viewAdapter.listView.notifyDataSetChanged()
        }

        // If user is scrolling, don't auto-scroll
        if (listIsScrolling) {
            // Fling мог быть прерван (шторм notifyDataSetChanged/подгрузка главы) без
            // финального IDLE — гейт «залипает» и follow-скролл молча отключается.
            // Сбрасываем только прерванный fling: при TOUCH_SCROLL палец может лежать
            // неподвижно дольше порога, и сброс дёргал бы список под ним.
            if (lastScrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING &&
                SystemClock.elapsedRealtime() - lastScrollEventTime > 500L
            ) {
                listIsScrolling = false
            } else {
                return
            }
        }

        // Check if the TTS item is already visible on screen
        val firstIndex = viewBind.listView.firstVisiblePosition
        val lastIndex = viewBind.listView.lastVisiblePosition

        for (index in firstIndex..lastIndex) {
            val item = viewAdapter.listView.getItem(index)
            if (
                item.chapterIndex == chapterIndex &&
                item is ReaderItem.Position &&
                item.chapterItemPosition == chapterItemPosition
            ) {
                val viewIndex = index - viewBind.listView.firstVisiblePosition
                val currentOffsetPx =
                    viewBind.listView.getChildAt(viewIndex).run { top - paddingTop }
                val newOffsetPx = 200.dpToPx(this@ReaderActivity)

                // Scroll only if item is below the desired visible position (fast scroll)
                if (currentOffsetPx > newOffsetPx) {
                    viewBind.listView.smoothScrollToPositionFromTop(index, newOffsetPx, 400)
                }
                return
            }
        }

        // Item not visible - use hybrid approach based on distance
        val itemIndex = indexOfReaderItem(
            list = viewModel.items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        if (itemIndex == -1) return

        val itemPosition = viewAdapter.listView.fromIndexToPosition(itemIndex)
        val newOffsetPx = 200.dpToPx(this@ReaderActivity)

        // Calculate distance from visible area
        val distanceBelow = itemPosition - lastIndex
        val distanceAbove = firstIndex - itemPosition
        val threshold = 5 // Items within this distance get smooth scroll

        when {
            distanceBelow in 1..threshold -> {
                // Close below visible area - smooth scroll
                viewBind.listView.smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 400)
            }
            distanceAbove in 1..threshold -> {
                // Close above visible area - smooth scroll
                viewBind.listView.smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 400)
            }
            else -> {
                // Far from visible area - instant scroll for better responsiveness
                viewBind.listView.setSelectionFromTop(itemPosition, newOffsetPx)
            }
        }
    }

    private fun scrollToReadingPositionForced(chapterIndex: Int, chapterItemPosition: Int) {
        // Search for the item being read otherwise do nothing
        val itemIndex = indexOfReaderItem(
            list = viewModel.items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        if (itemIndex == -1) return
        val itemPosition = viewAdapter.listView.fromIndexToPosition(itemIndex)
        val newOffsetPx = 200.dpToPx(this)
        viewAdapter.listView.notifyDataSetChanged()
        viewBind.listView.smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 500)
    }

    private fun scrollToReadingPositionImmediately(chapterIndex: Int, chapterItemPosition: Int) {
        // Search for the item being read otherwise do nothing
        val itemIndex = indexOfReaderItem(
            list = viewModel.items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        if (itemIndex == -1) return
        val itemPosition = viewAdapter.listView.fromIndexToPosition(itemIndex)
        val newOffsetPx = 200.dpToPx(this)
        viewAdapter.listView.notifyDataSetChanged()
        viewBind.listView.setSelectionFromTop(itemPosition, newOffsetPx)
        viewAdapter.listView.notifyDataSetChanged()
    }

    /**
     * Индекс первого ПОЛНОСТЬЮ видимого элемента списка, а не первого частично
     * срезанного сверху: если верхний ребёнок обрезан (top < paddingTop), берём
     * следующий. Итерация по детям устойчива к флингу/оверскроллу, когда
     * одновременно обрезано несколько верхних вью.
     */
    private fun getFirstFullyVisibleItemIndex(): Int {
        val listView = viewBind.listView
        if (listView.childCount == 0) {
            return viewAdapter.listView.getFirstVisibleItemIndexGivenPosition(
                listView.firstVisiblePosition
            )
        }
        for (index in 0 until listView.childCount) {
            val child = listView.getChildAt(index) ?: continue
            if (child.top >= listView.paddingTop) {
                return viewAdapter.listView.getFirstVisibleItemIndexGivenPosition(
                    (listView.firstVisiblePosition + index)
                        .coerceAtMost(viewAdapter.listView.count - 1)
                )
            }
        }
        // Все дети обрезаны сверху — крайний случай (оверскролл/загрузка),
        // ограничиваемся нижней подложкой-границей.
        return viewAdapter.listView.getFirstVisibleItemIndexGivenPosition(
            (listView.firstVisiblePosition + listView.childCount - 1)
                .coerceAtMost(viewAdapter.listView.count - 1)
        )
    }

    private fun updateReadingState() {
        val firstVisibleItem = viewBind.listView.firstVisiblePosition
        val lastVisibleItem = viewBind.listView.lastVisiblePosition
        val totalItemCount = viewAdapter.listView.count
        val visibleItemCount =
            if (totalItemCount == 0) 0 else (lastVisibleItem - firstVisibleItem + 1)

        val isTop = visibleItemCount != 0 && firstVisibleItem <= 1
        val isBottom =
            visibleItemCount != 0 && (firstVisibleItem + visibleItemCount) >= totalItemCount - 1

        when (viewModel.chaptersLoader.readerState) {
            ReaderState.IDLE -> {
                // Загружаем следующую главу только если пользователь уже реально скроллил.
                // Это предотвращает преждевременную загрузку при programmatic setSelectionFromTop
                // в начале сессии, когда список ещё короткий и isBottom сразу true.
                if (isBottom && userHasScrolled) {
                    viewModel.chaptersLoader.tryLoadNext()
                }
                if (isTop) {
                    val firstItem = viewModel.items.getOrNull(0)
                    if (firstItem != null && firstItem !is ReaderItem.BookStart) {
                        viewModel.chaptersLoader.tryLoadPrevious()
                    }
                }
            }
            ReaderState.LOADING -> run {}
            ReaderState.INITIAL_LOAD -> run {}
        }
    }

    private fun initialScrollToChapterItemPosition(
        chapterIndex: Int,
        chapterItemPosition: Int,
        offset: Int
    ) {
        val index = indexOfReaderItem(
            list = viewModel.items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        val position = viewAdapter.listView.fromIndexToPosition(index)
        if (index != -1) {
            viewBind.listView.setSelectionFromTop(position, offset)
        }
        fadeInTextLiveData.postValue(true)
    }

    private fun updateInfoView() {
        val lastVisiblePosition = viewBind.listView.lastVisiblePosition
        val itemIndex = viewAdapter.listView.fromPositionToIndex(lastVisiblePosition)
        viewModel.updateInfoViewTo(itemIndex, userHasScrolled = userHasScrolled)
    }

    override fun onPause() {
        updateCurrentReadingPosSavingState(
            firstVisibleItemIndex = viewAdapter.listView.fromPositionToIndex(
                viewBind.listView.firstVisiblePosition
            )
        )
        // Explicitly save to database when app pauses
        viewModel.saveCurrentReadingPosition()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        NarratorMediaControlsService.maybeAutoResume()

        if (viewModel.readerSpeaker.isSpeaking.value) {
            viewModel.readerSpeaker.forceUpdateCurrentItemState()
            val position = viewModel.readerSpeaker.getActualPlayingPosition()
                ?: viewModel.readerSpeaker.currentTextPlaying.value.itemPos
            val itemIndex = indexOfReaderItem(
                list = viewModel.items,
                chapterIndex = position.chapterIndex,
                chapterItemPosition = position.chapterItemPosition
            )
            if (itemIndex == -1) return
            val itemPosition = viewAdapter.listView.fromIndexToPosition(itemIndex)
            val newOffsetPx = 200.dpToPx(this)
            viewBind.listView.smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 500)
        }

        if (viewModel.chaptersLoader.hasLoadingError) {
            viewModel.chaptersLoader.retryFailed()
        }
    }

    private fun updateCurrentReadingPosSavingState(firstVisibleItemIndex: Int) {
        val item = viewModel.items.getOrNull(firstVisibleItemIndex) ?: return
        if (item !is ReaderItem.Position) return

        val offset = viewBind.listView.run { getChildAt(0).top - paddingTop }
        viewModel.readingCurrentChapter = ChapterState(
            chapterUrl = item.chapterUrl,
            chapterItemPosition = item.chapterItemPosition,
            offset = offset
        )
    }
}