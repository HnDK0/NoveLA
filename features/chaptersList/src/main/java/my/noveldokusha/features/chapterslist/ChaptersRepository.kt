package my.noveldokusha.features.chapterslist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao.UrlSize
import my.noveldokusha.feature.local_database.DAOs.DownloadedPageChaptersDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter
import my.noveldokusha.core.utils.normalizeBookUrl
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChaptersRepository @Inject constructor(
    private val appRepository: AppRepository,
    private val downloaderRepository: DownloaderRepository,
    private val appPreferences: AppPreferences,
    private val chapterBodyDao: ChapterBodyDao,
    private val downloadedPageChaptersDao: DownloadedPageChaptersDao,
    private val downloadedPageChaptersStore: DownloadedPageChaptersStore,
) {

    suspend fun downloadBookMetadata(bookUrl: String, bookTitle: String) = coroutineScope {
        val normalizedUrl = normalizeBookUrl(bookUrl)
        val coverUrl = async { downloaderRepository.bookCoverImageUrl(bookUrl = normalizedUrl) }
        val description = async { downloaderRepository.bookDescription(bookUrl = normalizedUrl) }

        appRepository.libraryBooks.upsertCanonical(
            Book(
                title = bookTitle,
                url = normalizedUrl,
                coverImageUrl = coverUrl.await().toSuccessOrNull()?.data ?: "",
                description = description.await().toSuccessOrNull()?.data ?: ""
            )
        )
    }


    fun getChaptersSortedFlow(bookUrl: String) = combine(
        appRepository.bookChapters.getChaptersWithContextFlow(bookUrl = bookUrl),
        combine(
            chapterBodyDao.getDownloadedUrlsFlow(bookUrl),
            downloadedPageChaptersDao.getByBookUrlsFlow(listOf(bookUrl)),
        ) { bodyUrls, pageRows -> bodyUrls + pageRows.map { it.url } }
    ) { chapters, downloadedUrls ->
        val downloadedSet = downloadedUrls.toSet()
        if (downloadedSet.isEmpty()) chapters
        else chapters.map { if (it.chapter.url in downloadedSet) it.copy(downloaded = true) else it }
    }
        .combine(appPreferences.CHAPTERS_SORT_ASCENDING.flow()) { chapters, sorted ->
            when (sorted) {
                TernaryState.Active -> chapters.sortedBy { it.chapter.position }
                TernaryState.Inverse -> chapters.sortedByDescending { it.chapter.position }
                TernaryState.Inactive -> chapters
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun getLastReadChapter(bookUrl: String): String? =
        appRepository.libraryBooks.get(bookUrl)?.lastReadChapter

    /**
     * Размеры глав для списка. Быстрый путь — только БД (LENGTH(body) +
     * реальные байты скачанных страничных глав), медленный — дебаунс-скан
     * диска по изменившемуся набору URL глав. Скан не выполняется на каждое
     * излучение: результат кэшируется комбинированием двух потоков.
     */
    fun getChapterSizesFlow(bookUrl: String): Flow<Map<String, ChapterSize>> = combine(
        dbInfoFlow(bookUrl),
        diskInfoFlow(bookUrl),
    ) { db, disk -> mergeDiskInfo(db, disk) }
        .map { it.sizeByUrl.mapValues { (_, bytes) -> ChapterSize(bytes) } }
        .distinctUntilChanged()

    private fun dbInfoFlow(bookUrl: String): Flow<DownloadInfo> = combine(
        chapterBodyDao.getSizesByBookUrls(listOf(bookUrl)),
        downloadedPageChaptersDao.getByBookUrlsFlow(listOf(bookUrl)),
    ) { sizes, pageRows -> buildDbInfo(sizes, pageRows) }

    private fun diskInfoFlow(bookUrl: String): Flow<DownloadInfo> = combine(
        appRepository.bookChapters.getChaptersWithContextFlow(bookUrl = bookUrl),
        downloadedPageChaptersDao.getByBookUrlsFlow(listOf(bookUrl)),
    ) { chapters, pageRows ->
        (chapters.map { it.chapter.url } + pageRows.map { it.url })
            .distinct()
            .sorted()
    }
        .distinctUntilChanged()
        .debounce(1_000)
        .map { urls -> downloadedPageChaptersStore.getDiskState(urls) }
        .map { (sizeByUrl, downloadedDirs) -> DownloadInfo(downloadedDirs, sizeByUrl) }
        .onStart { emit(DownloadInfo(emptySet(), emptyMap())) }
}

/** Размер главы для UI-лейбла. `null` — размер неизвестен (лейбл скрыт). */
data class ChapterSize(val sizeBytes: Long? = null)

internal data class DownloadInfo(
    val downloadedUrls: Set<String>,
    val sizeByUrl: Map<String, Long>,
)

/** Быстрый путь из БД: реальные байты скачанных страничных глав имеют приоритет над LENGTH(body). */
internal fun buildDbInfo(
    sizes: List<UrlSize>,
    pageRows: List<DownloadedPageChapter>,
): DownloadInfo = DownloadInfo(
    downloadedUrls = pageRows.mapTo(mutableSetOf()) { it.url },
    sizeByUrl = sizes.associate { it.url to it.sizeBytes } + pageRows.associate { it.url to it.totalBytes },
)

/** Дисковый скан имеет приоритет; загруженные URL — объединение обоих источников. */
internal fun mergeDiskInfo(db: DownloadInfo, disk: DownloadInfo): DownloadInfo = DownloadInfo(
    downloadedUrls = db.downloadedUrls + disk.downloadedUrls,
    sizeByUrl = db.sizeByUrl + disk.sizeByUrl,
)

private const val KB = 1024.0
private const val MB = KB * 1024

internal fun formatBytes(bytes: Long): String = when {
    bytes >= MB -> "%.1f MB".format(Locale.US, bytes / MB)
    bytes >= KB -> "%.0f KB".format(Locale.US, bytes / KB)
    else -> "$bytes B"
}