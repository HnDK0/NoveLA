package my.noveldokusha.features.chapterslist

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao.UrlSize
import my.noveldokusha.feature.local_database.DAOs.ChapterPagesDao
import my.noveldokusha.feature.local_database.DAOs.DownloadedPageChaptersDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter
import my.noveldokusha.core.utils.decodePages
import my.noveldokusha.core.utils.normalizeBookUrl
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
internal class ChaptersRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val downloaderRepository: DownloaderRepository,
    private val appPreferences: AppPreferences,
    private val chapterBodyDao: ChapterBodyDao,
    private val chapterPagesDao: ChapterPagesDao,
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
     * Размеры глав для списка. Три источника:
     * - БД (LENGTH(body) + реальные байты скачанных страничных глав)
     * - Дисковый скан downloaded_pages/
     * - Локальные CBZ-файлы (размер архива через ContentResolver)
     */
    fun getChapterSizesFlow(bookUrl: String): Flow<Map<String, ChapterSize>> = combine(
        dbInfoFlow(bookUrl),
        diskInfoFlow(bookUrl),
        localCbzInfoFlow(bookUrl),
    ) { db, disk, local -> mergeAllInfo(db, disk, local) }
        .map { it.sizeByUrl.mapValues { (_, bytes) -> ChapterSize(bytes) } }
        .distinctUntilChanged()

    /**
     * Размеры локальных CBZ-файлов через ContentResolver.
     * Для локальных манг-глав (URL начинается с local://) извлекает content URI
     * из ChapterPages и запрашивает реальный размер файла.
     */
    private fun localCbzInfoFlow(bookUrl: String): Flow<DownloadInfo> {
        if (!bookUrl.isLocalUri) return flowOf(DownloadInfo(emptySet(), emptyMap()))
        return combine(
            appRepository.bookChapters.getChaptersWithContextFlow(bookUrl = bookUrl),
            chapterPagesDao.getByBookUrls(listOf(bookUrl)),
        ) { chapters, pageRows ->
            val cbzSizes = HashMap<String, Long>()
            chapters.forEach { chapter ->
                val chapterUrl = chapter.chapter.url
                val pages = pageRows.find { it.url == chapterUrl }?.pages?.let(::decodePages)
                if (pages.isNullOrEmpty()) return@forEach
                // Строка cbz://contentUri#entryName → извлекаем contentUri
                val firstPage = pages.firstOrNull() ?: return@forEach
                if (!firstPage.startsWith("cbz://")) return@forEach
                val contentUriStr = firstPage.removePrefix("cbz://").substringBefore('#')
                if (contentUriStr.isBlank()) return@forEach
                try {
                    val uri = android.net.Uri.parse(contentUriStr)
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val size = pfd.statSize
                        if (size > 0) cbzSizes[chapterUrl] = size
                    }
                } catch (e: Exception) {
                    Timber.d("localCbzInfo: failed to get size for $chapterUrl: ${e.message}")
                }
            }
            DownloadInfo(downloadedUrls = emptySet(), sizeByUrl = cbzSizes)
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .onStart { emit(DownloadInfo(emptySet(), emptyMap())) }
    }

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

/** Объединение трёх источников: БД, диск, локальные CBZ. Приоритет: CBZ > диск > БД. */
internal fun mergeAllInfo(
    db: DownloadInfo,
    disk: DownloadInfo,
    local: DownloadInfo,
): DownloadInfo = DownloadInfo(
    downloadedUrls = db.downloadedUrls + disk.downloadedUrls + local.downloadedUrls,
    sizeByUrl = db.sizeByUrl + disk.sizeByUrl + local.sizeByUrl,
)

private const val KB = 1024.0
private const val MB = KB * 1024

internal fun formatBytes(bytes: Long): String = when {
    bytes >= MB -> "%.1f MB".format(Locale.US, bytes / MB)
    bytes >= KB -> "%.0f KB".format(Locale.US, bytes / KB)
    else -> "$bytes B"
}