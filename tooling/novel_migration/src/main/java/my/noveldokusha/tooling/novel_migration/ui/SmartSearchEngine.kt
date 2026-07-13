package my.noveldokusha.tooling.novel_migration.ui

import kotlinx.coroutines.Dispatchers
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
                            val allChapters = firstPage.chapters.toMutableList()
                            for (page in 2..firstPage.totalPages) {
                                val pageData = source.parsePage(bookUrl, page)
                                if (pageData is Response.Success) {
                                    allChapters.addAll(pageData.data.chapters)
                                } else break
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
