package my.noveldokusha.interactor

import timber.log.Timber
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withContext
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.domain.ChapterPagination
import my.noveldokusha.core.isHttpsUrl
import my.noveldokusha.core.utils.normalizeBookUrl
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.data.CoverRepository
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryUpdatesInteractions @Inject constructor(
    private val appRepository: AppRepository,
    private val downloaderRepository: DownloaderRepository,
    private val libraryDao: LibraryDao,
    private val coverRepository: CoverRepository,
    private val appFileResolver: AppFileResolver,
) {
    companion object {
        private val hostGroupSemaphore = Semaphore(4)
        private val isUpdating = AtomicBoolean(false)
    }

    data class NewUpdate(
        val newChapters: List<Chapter>,
        val book: Book
    )

    data class CountingUpdating(
        val updated: Int,
        val total: Int
    )

    suspend fun updateLibraryBooks(
        completedOnes: Boolean,
        countingUpdating: MutableStateFlow<CountingUpdating?>,
        currentUpdating: MutableStateFlow<Set<Book>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
        failedUpdates: MutableStateFlow<Set<Book>>,
    ): Unit = withContext(Dispatchers.IO) {
        if (!isUpdating.compareAndSet(false, true)) {
            Timber.d("Library update already in progress, skipping")
            return@withContext
        }
        try {
            appRepository.libraryBooks.getAllInLibrary()
                .filter { it.completed == completedOnes }
                .filter { !it.url.isLocalUri }
                .also { list ->
                    Timber.d("=== Library update started: ${list.size} books (completed=$completedOnes) ===")
                    countingUpdating.update {
                        CountingUpdating(
                            updated = 0,
                            total = list.size
                        )
                    }
                }
                .groupBy { it.url.toHttpUrlOrNull()?.host }
                .map { (_, books) ->
                    async {
                        hostGroupSemaphore.withPermit {
                            for (book in books) {
                                updateBook(
                                    book = book,
                                    currentUpdating = currentUpdating,
                                    newUpdates = newUpdates,
                                    failedUpdates = failedUpdates,
                                    countingUpdating = countingUpdating
                                )
                            }
                        }
                    }
                }
                .awaitAll()

            Timber.d("=== Library update finished ===")
        } finally {
            isUpdating.set(false)
        }
    }

    suspend fun updateSpecificBooks(
        books: List<Book>,
        countingUpdating: MutableStateFlow<CountingUpdating?>,
        currentUpdating: MutableStateFlow<Set<Book>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
        failedUpdates: MutableStateFlow<Set<Book>>,
    ) = withContext(Dispatchers.IO) {
        countingUpdating.update { CountingUpdating(updated = 0, total = books.size) }
        books.filter { !it.url.isLocalUri }
            .groupBy { it.url.toHttpUrlOrNull()?.host }
            .map { (_, group) ->
                async {
                    hostGroupSemaphore.withPermit {
                        for (book in group) {
                            updateBook(book, countingUpdating, currentUpdating, newUpdates, failedUpdates)
                        }
                    }
                }
            }
            .awaitAll()
    }

    private suspend fun updateBook(
        book: Book,
        countingUpdating: MutableStateFlow<CountingUpdating?>,
        currentUpdating: MutableStateFlow<Set<Book>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
        failedUpdates: MutableStateFlow<Set<Book>>,
    ): Unit = withContext(Dispatchers.IO) {
        var activeBook = book
        val canonical = normalizeBookUrl(activeBook.url)
        if (canonical != activeBook.url) {
            appRepository.libraryBooks.reparentBookUrl(activeBook.url, canonical)
            activeBook = activeBook.copy(url = canonical)
        }

        Timber.d("[book] \"${activeBook.title}\" — start update | chaptersLastPage=${activeBook.chaptersLastPage} | chaptersListHash=${activeBook.chaptersListHash?.take(8)?.let { "$it…" } ?: "null"}")
        currentUpdating.update { it + activeBook }

        // Загружаем текущий список URL глав один раз — используется во всех стратегиях.
        val oldChaptersList = async(Dispatchers.IO) {
            appRepository.bookChapters.chapters(activeBook.url).map { it.url }.toSet()
        }

        // Обновляем метаданные книги на каждом апдейте (до проверки хэша глав).
        // ensureCover сам решает, качать ковер или нет (Skipped если локально уже есть валидный).
        if (activeBook.title == "Unknown Novel" || activeBook.title.isBlank()) {
            downloaderRepository.bookTitle(bookUrl = activeBook.url).onSuccess { newTitle ->
                if (!newTitle.isNullOrBlank() && newTitle != "Unknown Novel") {
                    appRepository.libraryBooks.updateTitle(activeBook.url, newTitle)
                }
            }
        }
        if (activeBook.coverImageUrl.isBlank() || activeBook.coverImageUrl.isHttpsUrl) {
            downloaderRepository.bookCoverImageUrl(bookUrl = activeBook.url).onSuccess { newCoverUrl ->
                if (!newCoverUrl.isNullOrBlank()) {
                    syncCover(activeBook.url, newCoverUrl)
                }
            }
        }
        if (activeBook.description.isBlank()) {
            downloaderRepository.bookDescription(bookUrl = activeBook.url).onSuccess { newDescription ->
                if (!newDescription.isNullOrBlank()) {
                    appRepository.libraryBooks.updateDescription(activeBook.url, newDescription)
                }
            }
        }

        // Загружаем и сохраняем жанры книги только если они ещё не заполнены
        if (activeBook.genres.isBlank()) {
            downloaderRepository.bookGenres(bookUrl = activeBook.url).onSuccess { genres ->
                if (genres.isNotEmpty()) {
                    libraryDao.updateGenres(activeBook.url, my.noveldokusha.core.utils.GenreUtils.normalize(genres))
                }
            }
        }

        // Быстрая проверка хэша — только для книг без chaptersLastPage (старый путь).
        // parsePage-плагины не используют хэш.
        if (activeBook.chaptersLastPage == null && activeBook.chaptersListHash != null) {
            val hashUnchanged = downloaderRepository.bookChaptersListHash(bookUrl = activeBook.url).let { result ->
                if (result is my.noveldokusha.core.Response.Success) {
                    result.data == activeBook.chaptersListHash
                } else false
            }
            if (hashUnchanged) {
                Timber.d("[SKIP] \"${activeBook.title}\" — hash unchanged, no new chapters")
                currentUpdating.update { it - activeBook }
                countingUpdating.update { it?.copy(updated = it.updated + 1) }
                return@withContext
            }
        }

        // ── Выбор стратегии обновления ────────────────────────────────────────
        val lastPage = activeBook.chaptersLastPage
        if (lastPage != null &&
            ChapterPagination.isPageCounterConsistent(lastPage, oldChaptersList.await().size)
        ) {
            // parsePage-режим: книга уже была спарсена через parsePage.
            // Перечитываем последнюю известную страницу + догружаем новые.
            Timber.d("[STRATEGY: parsePage incremental] \"${activeBook.title}\" — lastPage=$lastPage")
            updateBookWithParsePage(activeBook, oldChaptersList, newUpdates, failedUpdates)
        } else {
            // Счётчик страниц рассинхронизирован с числом глав в БД (главы потеряны) —
            // сбрасываем счётчик и делаем полный репарс. Троттлинг повторов обеспечивает
            // сам интервал воркера автообновления — отдельный guard не нужен.
            if (lastPage != null) {
                Timber.w("[RECOVERY] \"${activeBook.title}\" — lastPage=$lastPage несогласован с ${oldChaptersList.await().size} главами, сброс счётчика и полный репарс")
                appRepository.libraryBooks.updateChaptersLastPage(activeBook.url, null)
            }
            // Проверяем, поддерживает ли плагин parsePage (первый раз для этой книги).
            val firstPageResult = downloaderRepository.bookChaptersPage(activeBook.url, page = 1)
            if (firstPageResult != null) {
                // Плагин поддерживает parsePage — полный первоначальный парс всех страниц
                Timber.d("[STRATEGY: parsePage first-time] \"${activeBook.title}\" — will scan all pages")
                updateBookFirstTimeParsePage(activeBook, firstPageResult, oldChaptersList, newUpdates, failedUpdates)
            } else {
                // Плагин не поддерживает parsePage — старый путь через getChapterList
                Timber.d("[STRATEGY: legacy getChapterList] \"${activeBook.title}\"")
                updateBookLegacy(activeBook, oldChaptersList, newUpdates, failedUpdates)
            }
        }

        currentUpdating.update { it - activeBook }
        countingUpdating.update { it?.copy(updated = it.updated + 1) }
    }

    /**
     * Первый парс книги через parsePage: загружаем все страницы 1..totalPages,
     * сохраняем chaptersLastPage = totalPages.
     */
    private suspend fun updateBookFirstTimeParsePage(
        book: Book,
        firstPageResult: my.noveldokusha.core.Response<my.noveldokusha.scraper.SourceInterface.Catalog.PagedChapterResult>,
        oldChaptersList: Deferred<Set<String>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
        failedUpdates: MutableStateFlow<Set<Book>>,
    ) {
        val firstPage = (firstPageResult as? my.noveldokusha.core.Response.Success)?.data
            ?: run {
                Timber.d("[parsePage first-time] \"${book.title}\" — FAILED to parse page 1")
                failedUpdates.update { it + book }
                return
            }

        val totalPages = firstPage.totalPages
        Timber.d("[parsePage first-time] \"${book.title}\" — totalPages=$totalPages, page 1 chapters=${firstPage.chapters.size}")
        val allChapters = mutableListOf<Chapter>()

        // Добавляем главы первой страницы
        firstPage.chapters.forEachIndexed { idx, ch ->
            allChapters.add(Chapter(title = ch.title, url = ch.url, bookUrl = book.url, position = idx))
        }

        // Загружаем оставшиеся страницы 2..totalPages
        for (page in 2..totalPages) {
            val pageData = (downloaderRepository.bookChaptersPage(book.url, page) as? my.noveldokusha.core.Response.Success)?.data
            if (pageData == null) {
                Timber.d("[parsePage first-time] \"${book.title}\" — FAILED to load page $page, stopping early")
                break
            }
            Timber.d("[parsePage first-time] \"${book.title}\" — page $page chapters=${pageData.chapters.size}")
            // Захватываем offset ДО начала итерации — allChapters.size меняется внутри forEachIndexed
            val offset = allChapters.size
            pageData.chapters.forEachIndexed { idx, ch ->
                allChapters.add(
                    Chapter(title = ch.title, url = ch.url, bookUrl = book.url, position = offset + idx)
                )
            }
        }

        Timber.d("[parsePage first-time] \"${book.title}\" — total chapters collected=${allChapters.size}, saving lastPage=$totalPages")
        mergeAndNotify(book, allChapters, oldChaptersList, newUpdates)
        appRepository.libraryBooks.updateChaptersLastPage(book.url, totalPages)
    }

    /**
     * Инкрементальное обновление для parsePage-книги:
     * перечитываем последнюю известную страницу и берём из неё только НОВЫЕ главы
     * (не меняем позиции уже сохранённых), затем догружаем страницы lastPage+1..newTotalPages.
     *
     * Важно: в merge() позиция перезаписывается для любого переданного URL,
     * поэтому существующие главы нельзя включать в список с пересчитанными позициями.
     */
    private suspend fun updateBookWithParsePage(
        book: Book,
        oldChaptersList: Deferred<Set<String>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
        failedUpdates: MutableStateFlow<Set<Book>>,
    ) {
        val lastKnownPage = book.chaptersLastPage ?: return

        val lastPageResult = downloaderRepository.bookChaptersPage(book.url, lastKnownPage)
        val lastPageData = (lastPageResult as? my.noveldokusha.core.Response.Success)?.data
            ?: run {
                Timber.d("[parsePage incremental] \"${book.title}\" — FAILED to load lastPage=$lastKnownPage")
                failedUpdates.update { it + book }
                return
            }

        val newTotalPages = lastPageData.totalPages

        // Единственный источник истины о существующих главах.
        // Переиспользуем уже запущенный deferred — никакого дополнительного DB-запроса.
        val existingUrls = oldChaptersList.await()
        var positionOffset = existingUrls.size

        Timber.d("[parsePage incremental] \"${book.title}\" — lastPage=$lastKnownPage, newTotalPages=$newTotalPages, existingChapters=${existingUrls.size}, lastPageChapters=${lastPageData.chapters.size}")

        val chaptersToAdd = mutableListOf<Chapter>()

        // Из последней страницы берём ТОЛЬКО новые главы.
        // Существующие не передаём в merge() — иначе их позиции будут перезаписаны неверными значениями.
        val newFromLastPage = lastPageData.chapters.filter { it.url !in existingUrls }
        Timber.d("[parsePage incremental] \"${book.title}\" — new chapters from lastPage=$lastKnownPage: ${newFromLastPage.size}")
        newFromLastPage.forEachIndexed { idx, ch ->
            chaptersToAdd.add(
                Chapter(title = ch.title, url = ch.url, bookUrl = book.url, position = positionOffset + idx)
            )
        }
        positionOffset += chaptersToAdd.size

        // Если появились новые страницы — загружаем их
        if (newTotalPages > lastKnownPage) {
            Timber.d("[parsePage incremental] \"${book.title}\" — ${newTotalPages - lastKnownPage} new page(s) detected (${lastKnownPage + 1}..$newTotalPages), loading...")
        }
        for (page in (lastKnownPage + 1)..newTotalPages) {
            val pageData = (downloaderRepository.bookChaptersPage(book.url, page) as? my.noveldokusha.core.Response.Success)?.data
            if (pageData == null) {
                Timber.d("[parsePage incremental] \"${book.title}\" — FAILED to load new page $page, stopping early")
                break
            }
            Timber.d("[parsePage incremental] \"${book.title}\" — new page $page chapters=${pageData.chapters.size}")
            val offset = positionOffset
            pageData.chapters.forEachIndexed { idx, ch ->
                chaptersToAdd.add(
                    Chapter(title = ch.title, url = ch.url, bookUrl = book.url, position = offset + idx)
                )
            }
            positionOffset += pageData.chapters.size
        }

        Timber.d("[parsePage incremental] \"${book.title}\" — total new chapters to add: ${chaptersToAdd.size}")
        mergeAndNotify(book, chaptersToAdd, oldChaptersList, newUpdates)

        if (newTotalPages != lastKnownPage) {
            appRepository.libraryBooks.updateChaptersLastPage(book.url, newTotalPages)
            Timber.d("[parsePage incremental] \"${book.title}\" — updating lastPage $lastKnownPage → $newTotalPages")
        } else {
            Timber.d("[parsePage incremental] \"${book.title}\" — no new pages, lastPage=$lastKnownPage unchanged")
        }
    }

    /**
     * Старый путь — полный getChapterList без пагинации.
     */
    private suspend fun updateBookLegacy(
        book: Book,
        oldChaptersList: Deferred<Set<String>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
        failedUpdates: MutableStateFlow<Set<Book>>,
    ) {
        downloaderRepository.bookChaptersList(bookUrl = book.url).onSuccess { chapters ->
            Timber.d("[legacy] \"${book.title}\" — fetched ${chapters.size} chapters total")
            mergeAndNotify(book, chapters, oldChaptersList, newUpdates)
            // Обновляем хэш для быстрого скипа в следующий раз
            downloaderRepository.bookChaptersListHash(bookUrl = book.url).onSuccess { hash ->
                if (hash != null) {
                    appRepository.libraryBooks.updateChaptersListHash(book.url, hash)
                }
            }
        }.onError {
            Timber.e("[legacy] \"${book.title}\" — FAILED to fetch chapter list: ${it.message}")
            failedUpdates.update { it + book }
        }
    }

    private suspend fun mergeAndNotify(
        book: Book,
        chapters: List<Chapter>,
        oldChaptersList: Deferred<Set<String>>,
        newUpdates: MutableStateFlow<Set<NewUpdate>>,
    ) {
        oldChaptersList.join()
        appRepository.bookChapters.merge(chapters, book.url)
        val newChapters = chapters.filter { it.url !in oldChaptersList.await() }
        if (newChapters.isNotEmpty()) {
            Timber.d("[merge] \"${book.title}\" — NEW chapters added: ${newChapters.size}")
            appRepository.libraryBooks.updateLastUpdateEpochTimeMilli(bookUrl = book.url)
            newUpdates.update { it + NewUpdate(book = book, newChapters = newChapters) }
        } else {
            Timber.d("[merge] \"${book.title}\" — no new chapters (merged ${chapters.size} existing)")
        }
    }

    suspend fun updateSingleBookMetadata(url: String, maxRetries: Int = 3) {
        val canonical = normalizeBookUrl(url)
        if (canonical != url) {
            val book = appRepository.libraryBooks.get(url)
            if (book != null) {
                appRepository.libraryBooks.reparentBookUrl(url, canonical)
            }
        }
        if (canonical in updatesInProgress) return
        updatesInProgress.add(canonical)
        try {
            var retryCount = 0
            var success = false
            while (retryCount < maxRetries && !success) {
                try {
                    val book = appRepository.libraryBooks.get(canonical) ?: return
                    if (book.title == "Unknown Novel" || book.title.isBlank()) {
                        downloaderRepository.bookTitle(bookUrl = canonical).toSuccessOrNull()?.data?.let { newTitle ->
                            if (!newTitle.isNullOrBlank() && newTitle != "Unknown Novel") {
                                appRepository.libraryBooks.updateTitle(canonical, newTitle)
                            }
                        }
                    }
                    if (book.coverImageUrl.isBlank() || book.coverImageUrl.isHttpsUrl) {
                        downloaderRepository.bookCoverImageUrl(bookUrl = canonical).toSuccessOrNull()?.data?.let { coverUrl ->
                            if (!coverUrl.isNullOrBlank()) {
                                syncCover(canonical, coverUrl)
                            }
                        }
                    }
                    if (book.description.isBlank()) {
                        downloaderRepository.bookDescription(bookUrl = canonical).toSuccessOrNull()?.data?.let { description ->
                            if (!description.isNullOrBlank()) {
                                appRepository.libraryBooks.updateDescription(canonical, description)
                            }
                        }
                    }
                    if (book.genres.isBlank()) {
                        downloaderRepository.bookGenres(bookUrl = canonical).toSuccessOrNull()?.data?.let { genres ->
                            if (genres.isNotEmpty()) {
                                libraryDao.updateGenres(canonical, my.noveldokusha.core.utils.GenreUtils.normalize(genres))
                            }
                        }
                    }
                    appRepository.libraryBooks.updateLastUpdateEpochTimeMilli(canonical)
                    success = true
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount < maxRetries) {
                        delay(1000L * (1 shl (retryCount - 1)))
                    }
                }
            }
        } finally {
            updatesInProgress.remove(canonical)
        }
    }

    private val updatesInProgress = mutableSetOf<String>()

    private suspend fun syncCover(bookUrl: String, remoteCoverUrl: String) {
        val coverFile = appFileResolver.getStorageBookCoverImageFile(appFileResolver.getLocalBookFolderName(bookUrl))
        if (!coverRepository.ensureCover(coverFile, remoteCoverUrl)) {
            Timber.w("Failed to download cover for $bookUrl")
        }
    }
}