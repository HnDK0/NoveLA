package my.noveldokusha.features.chapterslist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.core.ImageQuality
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterPagesDao
import my.noveldokusha.feature.local_database.DAOs.DownloadedPageChaptersDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.core.utils.normalizeBookUrl
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChaptersRepository @Inject constructor(
    private val appRepository: AppRepository,
    private val downloaderRepository: DownloaderRepository,
    private val appPreferences: AppPreferences,
    private val chapterBodyDao: ChapterBodyDao,
    private val chapterPagesDao: ChapterPagesDao,
    private val downloadedPageChaptersDao: DownloadedPageChaptersDao,
) {

    companion object {
        // Средний размер страницы в каждом качестве (веб-страница манхвы).
        private fun averagePageBytes(quality: ImageQuality): Long = when (quality) {
            ImageQuality.HIGH -> 400L * 1024
            ImageQuality.BALANCED -> 150L * 1024
            ImageQuality.DATA_SAVER -> 70L * 1024
        }

        private fun decodePages(json: String): List<String>? = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            null
        }
    }

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
        combine(
            appRepository.bookChapters.getChaptersWithContextFlow(bookUrl = bookUrl),
            chapterBodyDao.getDownloadedUrlsFlow(bookUrl),
            chapterBodyDao.getSizesByBookUrls(listOf(bookUrl)),
        ) { chapters, downloadedUrls, bodySizes ->
            Triple(chapters, downloadedUrls, bodySizes)
        },
        combine(
            chapterPagesDao.getByBookUrls(listOf(bookUrl)),
            downloadedPageChaptersDao.getByBookUrlsFlow(listOf(bookUrl)),
            appPreferences.READER_IMAGE_QUALITY.flow(),
        ) { cachedPages, pageDownloads, qualityName ->
            Triple(cachedPages, pageDownloads, qualityName)
        },
    ) { (chapters, downloadedUrls, bodySizes), (cachedPages, pageDownloads, qualityName) ->
        val downloadedSet = downloadedUrls.toSet() + pageDownloads.map { it.url }
        val sizeByUrl = HashMap<String, Long>()
        bodySizes.forEach { sizeByUrl[it.url] = it.sizeBytes }
        val pageCountByUrl = HashMap<String, Int>()
        cachedPages.forEach { row ->
            decodePages(row.pages)?.let { pages ->
                if (pages.isNotEmpty()) pageCountByUrl[row.url] = pages.size
            }
        }
        val pageBytesByUrl = HashMap<String, Long>()
        pageDownloads.forEach { pageBytesByUrl[it.url] = it.totalBytes }
        val avgPageBytes = averagePageBytes(ImageQuality.parse(qualityName))
        chapters.map { ch ->
            val url = ch.chapter.url
            var result = if (url in downloadedSet) ch.copy(downloaded = true) else ch
            val actual = sizeByUrl[url] ?: pageBytesByUrl[url]
            if (actual != null) {
                result = result.copy(sizeBytes = actual)
            } else {
                pageCountByUrl[url]?.let { count ->
                    result = result.copy(estimatedBytes = count * avgPageBytes)
                }
            }
            result
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

    suspend fun getLastReadChapter(bookUrl: String): String? =
        appRepository.libraryBooks.get(bookUrl)?.lastReadChapter
            ?: appRepository.bookChapters.getFirstChapter(bookUrl)?.url

}