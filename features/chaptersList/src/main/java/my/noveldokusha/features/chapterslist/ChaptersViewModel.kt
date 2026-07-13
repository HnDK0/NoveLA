package my.noveldokusha.features.chapterslist

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldokusha.core.Response
import my.noveldokusha.coreui.BaseViewModel
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloadManager
import my.noveldokusha.data.EnqueueResult
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.data.EpubImporterRepository
import my.noveldokusha.chapterslist.R
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
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
import my.noveldokusha.scraper.utils.normalizeBookUrl
import my.noveldokusha.text_translator.domain.TranslationManager
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
    appFileResolver: AppFileResolver,
    private val downloaderRepository: DownloaderRepository,
    val downloadManager: DownloadManager,
    private val chaptersRepository: ChaptersRepository,
    private val epubImporterRepository: EpubImporterRepository,
    private val libraryDao: LibraryDao,
    private val chapterTranslationDao: ChapterTranslationDao,
    private val translationManager: TranslationManager,
    stateHandle: SavedStateHandle,
) : BaseViewModel(), ChapterStateBundle {

    companion object {
        private const val TAG = "ChaptersViewModel"
    }

    override val rawBookUrl by StateExtra_String(stateHandle)
    override val bookTitle by StateExtra_String(stateHandle)

    private val bookUrl = normalizeBookUrl(
        appFileResolver.getLocalIfContentType(rawBookUrl, bookFolderName = bookTitle)
    )

    @Volatile
    private var loadChaptersJob: Job? = null

    @Volatile
    private var lastSelectedChapterUrl: String? = null
    private val source = scraper.getCompatibleSource(bookUrl)
    private val book = appRepository.libraryBooks.getFlow(bookUrl)
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
        appScope.launch {
            if (rawBookUrl.isContentUri && appRepository.libraryBooks.get(bookUrl) == null) {
                importUriContent()
            }
        }

        viewModelScope.launch {
            if (state.isLocalSource.value) return@launch

            if (!appRepository.bookChapters.hasChapters(bookUrl))
                updateChaptersList()

            if (appRepository.libraryBooks.get(bookUrl) != null)
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
            chaptersRepository.getChaptersSortedFlow(bookUrl = bookUrl).collect {
                state.chapters.clear()
                state.chapters.addAll(it)
            }
        }

        // Подписываемся на статус загрузки текущей книги
        viewModelScope.launch {
            downloadManager.tasks.collect { tasks ->
                state.downloadTask.value = tasks.find { it.bookUrl == bookUrl }
            }
        }

        // Подписываемся на переведённые названия глав из БД
        viewModelScope.launch {
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow()
                .flatMapLatest { targetLang ->
                    chapterTranslationDao.getTranslatedTitlesFlow(bookUrl, targetLang)
                }
                .collectLatest { list ->
                    state.translatedChapterTitles.value = list.associate {
                        it.chapterUrl to it.translatedText
                    }
                }
        }
    }

    fun toggleBookmark() {
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
            val isInLibrary = appRepository.libraryBooks.existInLibrary(bookUrl)
            epubImporterRepository.importEpubFromContentUri(
                contentUri = rawBookUrl,
                bookTitle = bookTitle,
                addToLibrary = isInLibrary
            ).onError {
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
            if (lastPage != null) {
                updateChaptersIncremental(url, lastPage)
            } else {
                // First time or legacy: try full parsePage, fallback to getChapterList
                updateChaptersFull(url)
            }

            appRepository.libraryBooks.updateLastUpdateEpochTimeMilli(bookUrl = url)
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
            android.util.Log.w(TAG, "updateChaptersIncremental: failed to load lastPage=$lastKnownPage, falling back to full update")
            updateChaptersFull(bookUrl)
            return
        }

        val existingUrls = appRepository.bookChapters.chapters(bookUrl).map { it.url }.toSet()
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
            }.onError {
                state.error.value = it.message
            }
    }

    suspend fun getLastReadChapter(): String? =
        chaptersRepository.getLastReadChapter(bookUrl = bookUrl)

    fun setAsUnreadSelected() {
        val list = state.selectedChaptersUrl.toList()
        appScope.launch(Dispatchers.Default) {
            appRepository.bookChapters.setAsUnread(list.map { it.first })
        }
    }

    fun setAsReadSelected() {
        val list = state.selectedChaptersUrl.toList()
        appScope.launch(Dispatchers.Default) {
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
            appScope.launch(Dispatchers.Default) {
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
            appScope.launch(Dispatchers.Default) {
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
        appScope.launch(Dispatchers.Default) {
            appRepository.chapterBody.removeRows(list.map { it.first })
        }
    }

    fun deleteTranslationsForBook() {
        appScope.launch(Dispatchers.Default) {
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