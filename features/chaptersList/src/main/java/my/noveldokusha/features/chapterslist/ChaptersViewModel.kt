package my.noveldokusha.features.chapterslist

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.BaseViewModel
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.data.EpubImporterRepository
import my.noveldokusha.chapterslist.R
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.isContentUri
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.toState
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.feature.local_database.DAOs.BookGenreDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.BookGenre
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.text_translator.domain.TranslationManager
import javax.inject.Inject

interface ChapterStateBundle {
    val rawBookUrl: String
    val bookTitle: String
}

@HiltViewModel
internal class ChaptersViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val appScope: AppCoroutineScope,
    scraper: Scraper,
    private val toasty: Toasty,
    private val appPreferences: AppPreferences,
    appFileResolver: AppFileResolver,
    private val downloaderRepository: DownloaderRepository,
    private val chaptersRepository: ChaptersRepository,
    private val epubImporterRepository: EpubImporterRepository,
    private val bookGenreDao: BookGenreDao,
    private val chapterTranslationDao: ChapterTranslationDao,
    private val translationManager: TranslationManager,
    stateHandle: SavedStateHandle,
) : BaseViewModel(), ChapterStateBundle {

    companion object {
        private const val TAG = "ChaptersViewModel"
    }

    override val rawBookUrl by StateExtra_String(stateHandle)
    override val bookTitle by StateExtra_String(stateHandle)

    private val bookUrl = appFileResolver.getLocalIfContentType(rawBookUrl, bookFolderName = bookTitle)

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

        viewModelScope.launch {
            if (state.isLocalSource.value) return@launch
            updateGenres()
        }

        viewModelScope.launch {
            chaptersRepository.getChaptersSortedFlow(bookUrl = bookUrl).collect {
                state.chapters.clear()
                state.chapters.addAll(it)
            }
        }

        viewModelScope.launch {
            bookGenreDao.getGenresFlow(bookUrl).collect {
                state.genres.value = it
            }
        }

        // Подписываемся на переведённые названия глав из БД
        viewModelScope.launch {
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow()
                .flatMapLatest { targetLang ->
                    chapterTranslationDao.getTranslatedTitlesFlow(bookUrl, targetLang)
                }
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                .collectLatest { list ->
                    state.translatedChapterTitles.value = list.associate {
                        it.chapterUrl to it.translatedText
                    }
                }        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val isBookmarked =
                appRepository.toggleBookmark(bookTitle = bookTitle, bookUrl = bookUrl)
            val msg = if (isBookmarked) R.string.added_to_library else R.string.removed_from_library
            toasty.show(msg)
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

    private fun updateGenres() = viewModelScope.launch {
        if (state.isLocalSource.value) return@launch
        downloaderRepository.bookGenres(bookUrl = bookUrl).onSuccess { genres ->
            if (genres.isEmpty()) return@onSuccess
            bookGenreDao.deleteByBook(bookUrl)
            bookGenreDao.insert(genres.map { BookGenre(bookUrl = bookUrl, genre = it) })
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
            downloaderRepository.bookChaptersList(bookUrl = url)
                .onSuccess {
                    if (it.isEmpty())
                        toasty.show(R.string.no_chapters_found)
                    appRepository.bookChapters.merge(newChapters = it, bookUrl = url)
                    appRepository.libraryBooks.updateLastUpdateEpochTimeMilli(bookUrl = url)
                }.onError {
                    state.error.value = it.message
                }
            state.isRefreshing.value = false
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

    fun downloadSelected() {
        if (state.isLocalSource.value) return

        val selectedUrls = state.selectedChaptersUrl.keys.toSet()
        val sortedChapters = state.chapters
            .filter { selectedUrls.contains(it.chapter.url) }
            .sortedBy { it.chapter.position }

        appScope.launch(Dispatchers.Default) {
            var successCount = 0
            var errorCount = 0
            sortedChapters.forEach { chapter ->
                appRepository.chapterBody.fetchBody(chapter.chapter.url).onSuccess {
                    successCount++
                }.onError {
                    errorCount++
                    android.util.Log.w(TAG, "downloadSelected: failed for ${chapter.chapter.title}: ${it.message}")
                }
            }
            if (errorCount > 0) {
                val msg = if (successCount > 0) {
                    "Downloaded: $successCount, errors: $errorCount"
                } else {
                    "Download error: $errorCount chapters"
                }
                toasty.show(msg)
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
        appScope.launch {
            appRepository.chapterBody.fetchBody(chapter.chapter.url).onSuccess {
                toasty.show(R.string.chapter_downloaded)
            }.onError {
                toasty.show("Download error: ${chapter.chapter.title}")
                android.util.Log.w(TAG, "onChapterDownload: failed for ${chapter.chapter.title}: ${it.message}")
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
