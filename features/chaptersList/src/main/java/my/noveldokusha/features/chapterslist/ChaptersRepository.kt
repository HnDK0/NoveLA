package my.noveldokusha.features.chapterslist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.DownloadedPageChaptersDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.core.utils.normalizeBookUrl
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


    /**
     * Размер скачанного содержимого главы (тело или файлы страниц).
     * Только РЕАЛЬНЫЕ байты сохранённых файлов — никаких оценок:
     * нескачанная глава размера не показывает.
     */
    data class ChapterSize(
        val sizeBytes: Long? = null,
    )

    private data class DownloadInfo(
        val downloadedUrls: Set<String>,
        val sizeByUrl: Map<String, ChapterSize>
    )

    private fun downloadInfoFlow(bookUrl: String) = combine(
        chapterBodyDao.getDownloadedUrlsFlow(bookUrl),
        chapterBodyDao.getSizesByBookUrls(listOf(bookUrl)),
        downloadedPageChaptersDao.getByBookUrlsFlow(listOf(bookUrl)),
        appRepository.bookChapters.getChaptersWithContextFlow(bookUrl = bookUrl),
    ) { downloadedUrls, bodySizes, pageDownloads, chapters ->
        val downloadedSet = downloadedUrls.toSet() + pageDownloads.map { it.url }
        val sizeByUrl = HashMap<String, ChapterSize>()
        // Текстовые главы: LENGTH(body).
        bodySizes.forEach { sizeByUrl[it.url] = ChapterSize(sizeBytes = it.sizeBytes) }
        // Страничные главы: totalBytes из строки метаданных (если есть).
        pageDownloads.forEach { pageDownloadsRow ->
            sizeByUrl[pageDownloadsRow.url] = ChapterSize(sizeBytes = pageDownloadsRow.totalBytes)
        }
        // Манга/манхва без строки метаданных (старые загрузки, обновлённая
        // схема) — размер из реальных файлов страниц на диске.
        val missing = chapters.map { it.chapter.url }.filterNot { it in sizeByUrl }
        val diskSizes: Map<String, Long>
        val diskDownloaded: Set<String>
        if (missing.isNotEmpty()) {
            val (sizes, dirs) = downloadedPageChaptersStore.getDiskState(missing)
            diskSizes = sizes
            diskDownloaded = dirs
            diskSizes.forEach { (url, bytes) ->
                sizeByUrl[url] = ChapterSize(sizeBytes = bytes)
            }
        } else {
            diskSizes = emptyMap()
            diskDownloaded = emptySet()
        }
        DownloadInfo(downloadedSet + diskDownloaded, sizeByUrl)
    }

    fun getChaptersSortedFlow(bookUrl: String) = combine(
        appRepository.bookChapters.getChaptersWithContextFlow(bookUrl = bookUrl),
        downloadInfoFlow(bookUrl)
    ) { chapters, info ->
        if (info.downloadedUrls.isEmpty()) chapters
        else chapters.map {
            if (it.chapter.url in info.downloadedUrls) it.copy(downloaded = true) else it
        }
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

    /** URL главы → размер (реальный или оценка), для списка глав. */
    fun getChapterSizesFlow(bookUrl: String) = downloadInfoFlow(bookUrl)
        .map { it.sizeByUrl }
        .flowOn(Dispatchers.Default)

    suspend fun getLastReadChapter(bookUrl: String): String? =
        appRepository.libraryBooks.get(bookUrl)?.lastReadChapter
            ?: appRepository.bookChapters.getFirstChapter(bookUrl)?.url

}