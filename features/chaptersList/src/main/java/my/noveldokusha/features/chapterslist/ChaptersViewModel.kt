package my.noveldokusha.features.chapterslist

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldokusha.core.Response
import androidx.lifecycle.ViewModel
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloadManager
import my.noveldokusha.data.EnqueueResult
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.data.LocalBookImporterRepository
import my.noveldokusha.chapterslist.R
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.domain.ChapterPagination
import my.noveldokusha.core.isContentUri
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.utils.GenreUtils
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.toState
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.core.utils.normalizeBookUrl
import my.noveldokusha.chapterslist.BuildConfig
import my.noveldokusha.debug.MemoryDiagnostics
import my.noveldokusha.text_translator.domain.TranslationManager
import timber.log.Timber
import javax.inject.Inject

interface ChapterStateBundle {
    val rawBookUrl: String
    val bookTitle: String
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class ChaptersViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val appScope: AppCoroutineScope,
    scraper: Scraper,
    private val toasty: Toasty,
    private val appPreferences: AppPreferences,
    private val appFileResolver: AppFileResolver,
    private val downloaderRepository: DownloaderRepository,
    val downloadManager: DownloadManager,
    private val chaptersRepository: ChaptersRepository,
    private val localBookImporterRepository: LocalBookImporterRepository,
    private val libraryDao: LibraryDao,
    private val chapterTranslationDao: ChapterTranslationDao,
    private val translationManager: TranslationManager,
    stateHandle: SavedStateHandle,
) : ViewModel(), ChapterStateBundle {

    override val rawBookUrl by StateExtra_String(stateHandle)
    override val bookTitle by StateExtra_String(stateHandle)

    private val bookUrlFlow = MutableStateFlow(
        normalizeBookUrl(
            appFileResolver.getLocalIfContentType(rawBookUrl, bookFolderName = bookTitle)
        )
    )
    private var bookUrl: String
        get() = bookUrlFlow.value
        set(value) {
            bookUrlFlow.value = value
        }

    @Volatile
    private var loadChaptersJob: Job? = null

    private var lastBookmarkClickMs = 0L

    @Volatile
    private var lastSelectedChapterUrl: String? = null
    private val source = scraper.getCompatibleSource(bookUrl)
    private val book = bookUrlFlow.flatMapLatest { url ->
        appRepository.libraryBooks.getFlow(url)
    }
        .filterNotNull()
        .map(ChaptersScreenState::BookState)
        .toState(
            viewModelScope,
            ChaptersScreenState.BookState(title = bookTitle, url = bookUrl, coverImageUrl = null)
        )

    val scraper: Scraper = scraper

    val state = ChaptersScreenState(
        book = book,
        error = mutableStateOf(""),
        chapters = mutableStateListOf(),
        selectedChaptersUrl = mutableStateMapOf(),
        isRefreshing = mutableStateOf(false),
        sourceCatalogNameStrRes = mutableStateOf(source?.nameStrId),
        settingChapterSort = appPreferences.CHAPTERS_SORT_ASCENDING.state(viewModelScope),
        isLocalSource = mutableStateOf(bookUrl.isLocalUri),
        isRefreshable = mutableStateOf(rawBookUrl.isContentUri || !bookUrl.isLocalUri),
        genres = mutableStateOf(emptyList()),
        translatedChapterTitles = mutableStateOf(emptyMap()),
        downloadTask = mutableStateOf(null),
    )

    // ─── Перевод названия и описания ──────────────────────────────────────────

    val translatedTitle = mutableStateOf<String?>(null)
    val translatedDescription = mutableStateOf<String?>(null)
    val isTranslatingInfo = mutableStateOf(false)

    fun translateBookInfo() {
        if (isTranslatingInfo.value) return
        viewModelScope.launch {
            val targetLang = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
            if (targetLang.isBlank()) {
                toasty.show(R.string.translate_target_lang_not_set)
                return@launch
            }

            isTranslatingInfo.value = true
            try {
                val translator = translationManager.getTranslator(
                    source = "auto",
                    target = targetLang
                )

                val title = state.book.value.title
                val description = state.book.value.description

                if (title.isNotBlank())
                    translatedTitle.value = translator.translate(title)
                if (description.isNotBlank())
                    translatedDescription.value = translator.translate(description)

            } catch (e: Exception) {
                toasty.show(R.string.translate_failed)
            } finally {
                isTranslatingInfo.value = false
            }
        }
    }

    fun clearBookInfoTranslation() {
        translatedTitle.value = null
        translatedDescription.value = null
    }

    // ─── Инициализация ────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            bookUrl = appRepository.libraryBooks.resolveStoredUrl(rawBookUrl)
            val canonical = normalizeBookUrl(bookUrl)
            if (canonical != bookUrl) {
                appRepository.libraryBooks.reparentBookUrl(bookUrl, canonical)
                bookUrl = canonical
            }

            if (rawBookUrl.isContentUri) {
                val localUrl = normalizeBookUrl(
                    appFileResolver.getLocalIfContentType(rawBookUrl, bookFolderName = bookTitle)
                )
                if (appRepository.libraryBooks.get(localUrl) == null) {
                    importUriContent()
                }
                bookUrl = localUrl
            }

            if (state.isLocalSource.value) return@launch

            if (!appRepository.bookChapters.hasChapters(bookUrl))
                updateChaptersList()

            if (appRepository.libraryBooks.getByUrl(bookUrl) != null)
                return@launch

            chaptersRepository.downloadBookMetadata(bookUrl = bookUrl, bookTitle = bookTitle)
        }

        // Берём жанры из БД. Если их нет — загружаем с сети.
        viewModelScope.launch {
            if (state.isLocalSource.value) return@launch
            val cachedBook = libraryDao.get(bookUrl)
            if (cachedBook?.genres?.isNotBlank() == true) {
                state.genres.value = GenreUtils.parse(cachedBook.genres)
                return@launch
            }
            updateGenres()
        }

        viewModelScope.launch {
            bookUrlFlow.collect { url ->
                chaptersRepository.getChaptersSortedFlow(bookUrl = url).collect {
                    state.chapters.clear()
                    state.chapters.addAll(it)
                }
            }
        }

        // Подписываемся на статус загрузки текущей книги
        viewModelScope.launch {
            downloadManager.tasks.collect { tasks ->
                state.downloadTask.value = tasks.find { it.bookUrl == bookUrlFlow.value }
            }
        }

        // Подписываемся на переведённые названия глав из БД
        viewModelScope.launch {
            combine(
                appPreferences.GLOBAL_TRANSLATION_ENABLED.flow(),
                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow()
            ) { enabled, targetLang -> enabled to targetLang }
                .flatMapLatest { (enabled, targetLang) ->
                    if (enabled) {
                        chapterTranslationDao.getTranslatedTitlesFlow(bookUrl, targetLang)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collectLatest { list ->
                    state.translatedChapterTitles.value = list.associate {
                        it.chapterUrl to it.translatedText
                    }
                }
        }
    }

    fun toggleBookmark() {
        val now = System.currentTimeMillis()
        if (now - lastBookmarkClickMs < 300L) return
        lastBookmarkClickMs = now
        viewModelScope.launch {
            val isBookmarked =
                appRepository.toggleBookmark(bookTitle = bookTitle, bookUrl = bookUrl)
            val msg = if (isBookmarked) R.string.added_to_library else R.string.removed_from_library
            toasty.show(msg)
        }
    }

    fun getCategories(): List<String> =
        listOf("", "Completed") + appPreferences.LIBRARY_CUSTOM_CATEGORIES.value

    fun updateBookCategory(category: String) {
        viewModelScope.launch {
            val isCompleted = category == "Completed"
            libraryDao.updateCategoryAndCompleted(bookUrl, category, isCompleted)
        }
    }

    /**
     * «Починить книгу»: принудительно сбрасывает кэш-признаки списка глав
     * (chaptersListHash + chaptersLastPage) и запускает ПОЛНЫЙ репарс глав.
     * Прогресс (read/позиция) сохраняется — merge берёт его из старых записей.
     */
    fun fixBook() {
        val url = bookUrl
        viewModelScope.launch {
            appRepository.libraryBooks.updateChaptersListHash(url, null)
            appRepository.libraryBooks.updateChaptersLastPage(url, null)
            updateChaptersList()
        }
    }

    fun onPullRefresh() {
        if (!state.isRefreshable.value) {
            toasty.show(R.string.local_book_nothing_to_update)
            state.isRefreshing.value = false
            return
        }
        toasty.show(R.string.updating_book_info)
        if (rawBookUrl.isContentUri) {
            importUriContent()
        } else if (!state.isLocalSource.value) {
            updateCover()
            updateTitle()
            updateDescription()
            updateChaptersList()
        }
    }

    private suspend fun updateGenres() {
        downloaderRepository.bookGenres(bookUrl = bookUrl).onSuccess { genres ->
            if (genres.isEmpty()) return@onSuccess
            val normalized = GenreUtils.normalize(genres)
            libraryDao.updateGenres(bookUrl, normalized)
            state.genres.value = GenreUtils.parse(normalized)
        }
    }

    private fun updateCover() = viewModelScope.launch {
        if (state.isLocalSource.value || book.value.coverImageUrl?.isLocalUri == true) return@launch
        downloaderRepository.bookCoverImageUrl(bookUrl = bookUrl).onSuccess {
            if (it == null) return@onSuccess
            appRepository.libraryBooks.updateCover(bookUrl, it)
        }
    }

    private fun updateTitle() = viewModelScope.launch {
        if (state.isLocalSource.value) return@launch
        downloaderRepository.bookTitle(bookUrl = bookUrl).onSuccess {
            if (it == null) return@onSuccess
            val currentBook = appRepository.libraryBooks.get(bookUrl)
            if (currentBook?.title == "Unknown Novel" || currentBook?.title.isNullOrBlank()) {
                appRepository.libraryBooks.updateTitle(bookUrl, it)
            }
        }
    }

    private fun updateDescription() = viewModelScope.launch {
        if (state.isLocalSource.value) return@launch
        downloaderRepository.bookDescription(bookUrl = bookUrl).onSuccess {
            if (it == null) return@onSuccess
            appRepository.libraryBooks.updateDescription(bookUrl, it)
        }
    }

    private fun importUriContent() {
        if (loadChaptersJob?.isActive == true) return
        loadChaptersJob = appScope.launch {
            state.error.value = ""
            state.isRefreshing.value = true
            val localUrl = normalizeBookUrl(
                appFileResolver.getLocalIfContentType(rawBookUrl, bookFolderName = bookTitle)
            )
            val isInLibrary = appRepository.libraryBooks.existInLibrary(localUrl)
            localBookImporterRepository.importFromContentUri(
                contentUri = rawBookUrl,
                bookTitle = bookTitle,
                addToLibrary = isInLibrary
            ).onSuccess {
                bookUrl = localUrl
            }.onError {
                state.error.value = it.message
            }
            state.isRefreshing.value = false
        }
    }

    private fun updateChaptersList() {
        if (loadChaptersJob?.isActive == true) return
        loadChaptersJob = appScope.launch {
            state.error.value = ""
            state.isRefreshing.value = true
            val url = bookUrl
            val book = appRepository.libraryBooks.get(url)

            // Try incremental parsePage if book already has chaptersLastPage.
            // This only re-checks the last known page + loads new pages,
            // instead of re-parsing all pages from scratch.
            val lastPage = book?.chaptersLastPage
            val chapterCount = appRepository.bookChapters.countByBookUrl(url)
            if (lastPage != null &&
                ChapterPagination.isPageCounterConsistent(lastPage, chapterCount)
            ) {
                updateChaptersIncremental(url, lastPage)
            } else {
                // Счётчик страниц рассинхронизирован с БД (или первый парс/legacy) —
                // сбрасываем и делаем полный репарс, позиции пересобираются с нуля.
                if (lastPage != null) {
                    Timber.w("updateChaptersList: lastPage=$lastPage несогласован с $chapterCount главами, сброс и полный репарс")
                    appRepository.libraryBooks.updateChaptersLastPage(url, null)
                }
                // First time or legacy: try full parsePage, fallback to getChapterList
                updateChaptersFull(url)
            }

            appRepository.libraryBooks.updateLastUpdateEpochTimeMilli(bookUrl = url)
            if (BuildConfig.DEBUG) MemoryDiagnostics.logMemoryStats("ChaptersList:updateChaptersList")
            state.isRefreshing.value = false
        }
    }

    /**
     * Incremental update: re-read the last known page to detect new chapters,
     * then load any new pages beyond the last known total.
     */
    private suspend fun updateChaptersIncremental(bookUrl: String, lastKnownPage: Int) {
        val lastPageResult = downloaderRepository.bookChaptersPage(bookUrl, lastKnownPage)
        val lastPageData = (lastPageResult as? Response.Success)?.data
        if (lastPageData == null) {
            Timber.w("updateChaptersIncremental: failed to load lastPage=$lastKnownPage, falling back to full update")
            updateChaptersFull(bookUrl)
            return
        }

        val existingUrls = appRepository.bookChapters.getChapterUrls(bookUrl).toSet()
        var positionOffset = existingUrls.size
        val chaptersToAdd = mutableListOf<Chapter>()

        // From the last page, only take chapters that don't exist yet
        val newFromLastPage = lastPageData.chapters.filter { it.url !in existingUrls }
        newFromLastPage.forEachIndexed { idx, ch ->
            chaptersToAdd.add(
                Chapter(
                    title = ch.title, url = ch.url, bookUrl = bookUrl, position = positionOffset + idx
                )
            )
        }
        positionOffset += chaptersToAdd.size

        // Load any new pages beyond the last known total
        val newTotalPages = lastPageData.totalPages
        for (page in (lastKnownPage + 1)..newTotalPages) {
            val pageData = (downloaderRepository.bookChaptersPage(bookUrl, page) as? Response.Success)?.data
                ?: break
            val offset = positionOffset
            pageData.chapters.forEachIndexed { idx, ch ->
                chaptersToAdd.add(
                    Chapter(
                        title = ch.title, url = ch.url, bookUrl = bookUrl, position = offset + idx
                    )
                )
            }
            positionOffset += pageData.chapters.size
        }

        if (chaptersToAdd.isNotEmpty()) {
            appRepository.bookChapters.merge(newChapters = chaptersToAdd, bookUrl = bookUrl)
        }

        if (newTotalPages != lastKnownPage) {
            appRepository.libraryBooks.updateChaptersLastPage(bookUrl, newTotalPages)
        }

        if (BuildConfig.DEBUG) MemoryDiagnostics.logMemoryStats("ChaptersList:updateChaptersIncremental")
    }

    /**
     * Full update: load all pages via parsePage or fallback to getChapterList.
     */
    private suspend fun updateChaptersFull(bookUrl: String) {
        downloaderRepository.bookChaptersList(bookUrl = bookUrl)
            .onSuccess {
                if (it.isEmpty()) toasty.show(R.string.no_chapters_found)
                appRepository.bookChapters.merge(newChapters = it, bookUrl = bookUrl)
                // Save chaptersLastPage for future incremental updates
                val firstPage = downloaderRepository.bookChaptersPage(bookUrl, 1)
                val totalPages = (firstPage as? Response.Success)?.data?.totalPages
                if (totalPages != null) {
                    appRepository.libraryBooks.updateChaptersLastPage(bookUrl, totalPages)
                }
                if (BuildConfig.DEBUG) MemoryDiagnostics.logMemoryStats("ChaptersList:updateChaptersFull")
            }.onError {
                state.error.value = it.message
            }
    }

    suspend fun getLastReadChapter(): String? =
        chaptersRepository.getLastReadChapter(bookUrl = bookUrl)

    fun setAsUnreadSelected() {
        val list = state.selectedChaptersUrl.toList()
        appScope.launch {
            appRepository.bookChapters.setAsUnread(list.map { it.first })
        }
    }

    fun setAsReadSelected() {
        val list = state.selectedChaptersUrl.toList()
        appScope.launch {
            appRepository.bookChapters.setAsRead(list.map { it.first })
        }
    }

    fun setAsReadUpToSelected() {
        if (state.selectedChaptersUrl.size > 1) return
        val selectedIndex = state.selectedChaptersUrl.keys.firstOrNull()?.let { selectedUrl ->
            state.chapters.indexOfFirst { it.chapter.url == selectedUrl }
        } ?: return

        if (selectedIndex != -1) {
            val chaptersToMarkAsRead = state.chapters.take(selectedIndex + 1).map { it.chapter.url }
            appScope.launch {
                appRepository.bookChapters.setAsRead(chaptersToMarkAsRead)
            }
        }
    }

    fun setAsReadUpToUnSelected() {
        if (state.selectedChaptersUrl.size > 1) return
        val selectedIndex = state.selectedChaptersUrl.keys.firstOrNull()?.let { selectedUrl ->
            state.chapters.indexOfFirst { it.chapter.url == selectedUrl }
        } ?: return

        if (selectedIndex != -1) {
            val chaptersToMarkAsUnread = state.chapters.take(selectedIndex + 1).map { it.chapter.url }
            appScope.launch {
                appRepository.bookChapters.setAsUnread(chaptersToMarkAsUnread)
            }
        }
    }

    fun downloadAllChapters() {
        if (state.isLocalSource.value) return
        val allChapters = state.chapters.toList().sortedBy { it.chapter.position }
        val chapterUrls = allChapters.map { it.chapter.url }
        viewModelScope.launch {
            when (val result = downloadManager.enqueue(
                bookTitle = bookTitle,
                bookUrl = bookUrl,
                chapterUrls = chapterUrls,
            )) {
                is EnqueueResult.Added -> toasty.show(R.string.download_added_to_queue)
                is EnqueueResult.ChaptersAdded -> toasty.show(R.string.download_chapters_added)
                is EnqueueResult.Resumed -> toasty.show(R.string.download_resumed)
                is EnqueueResult.AlreadyQueued -> toasty.show(R.string.download_already_queued)
                is EnqueueResult.AllCached -> toasty.show(R.string.download_all_cached)
            }
        }
    }

    fun downloadSelected() {
        if (state.isLocalSource.value) return

        val selectedUrls = state.selectedChaptersUrl.keys.toSet()
        val sortedChapters = state.chapters
            .filter { selectedUrls.contains(it.chapter.url) }
            .sortedBy { it.chapter.position }

        val chapterUrls = sortedChapters.map { it.chapter.url }
        viewModelScope.launch {
            when (val result = downloadManager.enqueue(
                bookTitle = bookTitle,
                bookUrl = bookUrl,
                chapterUrls = chapterUrls,
            )) {
                is EnqueueResult.Added -> toasty.show(R.string.download_added_to_queue)
                is EnqueueResult.ChaptersAdded -> toasty.show(R.string.download_chapters_added)
                is EnqueueResult.Resumed -> toasty.show(R.string.download_resumed)
                is EnqueueResult.AlreadyQueued -> toasty.show(R.string.download_already_queued)
                is EnqueueResult.AllCached -> toasty.show(R.string.download_all_cached)
            }
        }
    }

    fun deleteDownloadsSelected() {
        if (state.isLocalSource.value) return
        val list = state.selectedChaptersUrl.toList()
        appScope.launch {
            appRepository.chapterBody.removeRows(list.map { it.first })
        }
    }

    fun deleteTranslationsForBook() {
        appScope.launch {
            chapterTranslationDao.deleteTranslationsByBookUrls(listOf(bookUrl))
        }
    }

    fun onSelectionModeChapterClick(chapter: ChapterWithContext) {
        val url = chapter.chapter.url
        if (state.selectedChaptersUrl.containsKey(url)) {
            state.selectedChaptersUrl.remove(url)
        } else {
            state.selectedChaptersUrl[url] = Unit
        }
        lastSelectedChapterUrl = url
    }

    fun saveImageAsCover(uri: Uri) {
        appRepository.libraryBooks.saveImageAsCover(imageUri = uri, bookUrl = bookUrl)
    }

    fun onSelectionModeChapterLongClick(chapter: ChapterWithContext) {
        val url = chapter.chapter.url
        if (url != lastSelectedChapterUrl) {
            val indexOld = state.chapters.indexOfFirst { it.chapter.url == lastSelectedChapterUrl }
            val indexNew = state.chapters.indexOfFirst { it.chapter.url == url }
            val min = minOf(indexOld, indexNew)
            val max = maxOf(indexOld, indexNew)
            if (min >= 0 && max >= 0) {
                for (index in min..max) {
                    state.selectedChaptersUrl[state.chapters[index].chapter.url] = Unit
                }
                lastSelectedChapterUrl = state.chapters[indexNew].chapter.url
                return
            }
        }

        if (state.selectedChaptersUrl.containsKey(url)) {
            state.selectedChaptersUrl.remove(url)
        } else {
            state.selectedChaptersUrl[url] = Unit
        }
        lastSelectedChapterUrl = url
    }

    fun onChapterLongClick(chapter: ChapterWithContext) {
        val url = chapter.chapter.url
        state.selectedChaptersUrl[url] = Unit
        lastSelectedChapterUrl = url
    }

    fun onChapterDownload(chapter: ChapterWithContext) {
        if (state.isLocalSource.value) return
        viewModelScope.launch {
            when (downloadManager.enqueue(
                bookTitle = bookTitle,
                bookUrl = bookUrl,
                chapterUrls = listOf(chapter.chapter.url),
            )) {
                is EnqueueResult.Added -> toasty.show(R.string.download_added_to_queue)
                is EnqueueResult.ChaptersAdded -> toasty.show(R.string.download_chapters_added)
                is EnqueueResult.Resumed -> toasty.show(R.string.download_resumed)
                is EnqueueResult.AlreadyQueued -> toasty.show(R.string.download_already_queued)
                is EnqueueResult.AllCached -> toasty.show(R.string.download_all_cached)
            }
        }
    }

    fun unselectAll() {
        state.selectedChaptersUrl.clear()
    }

    fun selectAll() {
        state.chapters
            .toList()
            .map { it.chapter.url to Unit }
            .let { state.selectedChaptersUrl.putAll(it) }
    }

    fun invertSelection() {
        val allChaptersUrl = state.chapters.asSequence().map { it.chapter.url }.toSet()
        val selectedUrl = state.selectedChaptersUrl.asSequence().map { it.key }.toSet()
        val inverse = (allChaptersUrl - selectedUrl).asSequence().associateWith { }
        state.selectedChaptersUrl.clear()
        state.selectedChaptersUrl.putAll(inverse)
    }
}