package my.noveldokusha.tooling.novel_migration.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import my.noveldokusha.core.Response
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.domain.ChapterResult
import timber.log.Timber

data class ScoredSearchResult(
    val source: SourceInterface.Catalog,
    val book: my.noveldokusha.scraper.domain.BookResult,
    val chapters: List<ChapterResult>,
)

object ChapterFetcher {
    // ponytail: bound concurrency for parallel chapter-page fetching. 4 concurrent requests
    // to the same source is the same per-host limit DownloadManager uses; raising it would
    // risk the user's IP being rate-limited by the source site.
    private val PARALLEL_PAGE_LIMITED = Dispatchers.IO.limitedParallelism(4)

    suspend fun fetchChapters(source: SourceInterface.Catalog, bookUrl: String): List<ChapterResult> {
        Timber.e("fetchChapters: source=${source.baseUrl} bookUrl=$bookUrl")
        return withTimeoutOrNull(60_000L) {
            withContext(Dispatchers.IO) {
                val parseResult = source.parsePage(bookUrl, 1)
                if (parseResult != null) {
                    Timber.e("fetchChapters: using parsePage method")
                    return@withContext when (parseResult) {
                        is Response.Success -> {
                            val firstPage = parseResult.data
                            // ponytail: parallelise the remaining page fetches (2..totalPages)
                            // instead of fetching them sequentially. For a book with 20 pages
                            // of chapters this cuts ~19 sequential HTTP round-trips down to
                            // ~5 batches of 4 (with PARALLEL_PAGE_LIMITED). Order is preserved
                            // by collecting into a list-of-lists indexed by page number and
                            // flattening in order; failed pages contribute an empty list
                            // (matches the prior `break` behaviour but doesn't lose later
                            // successful pages if a single page fails).
                            val remainingPageCount =
                                (firstPage.totalPages - 1).coerceAtLeast(0)
                            val remainingPages = if (remainingPageCount == 0) {
                                emptyList()
                            } else {
                                withContext(PARALLEL_PAGE_LIMITED) {
                                    coroutineScope {
                                        (2..firstPage.totalPages).map { page ->
                                            async {
                                                runCatching {
                                                    source.parsePage(bookUrl, page)
                                                }.getOrNull() as? Response.Success
                                            }
                                        }.awaitAll()
                                    }
                                }
                            }
                            val allChapters = firstPage.chapters.toMutableList()
                            remainingPages.forEach { successResp ->
                                successResp?.data?.chapters?.let { allChapters.addAll(it) }
                            }
                            Timber.e("fetchChapters: parsePage got ${allChapters.size} chapters")
                            allChapters
                        }
                        else -> {
                            Timber.e("fetchChapters: parsePage returned non-success response=$parseResult")
                            emptyList()
                        }
                    }
                }
                Timber.e("fetchChapters: parsePage returned null, falling back to getChapterList")
                return@withContext when (val resp = source.getChapterList(bookUrl)) {
                    is Response.Success -> {
                        Timber.e("fetchChapters: getChapterList got ${resp.data.size} chapters")
                        resp.data
                    }
                    else -> {
                        Timber.e("fetchChapters: getChapterList returned response=$resp")
                        emptyList()
                    }
                }
            }
        } ?: run {
            Timber.e("fetchChapters: timed out for ${source.baseUrl} $bookUrl")
            emptyList()
        }
    }
}
