package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.isValidChapterContent
import my.noveldokusha.core.map
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterPagesDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterPages
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterBodyRepository @Inject constructor(
    private val chapterBodyDao: ChapterBodyDao,
    private val chapterPagesDao: ChapterPagesDao,
    private val chapterTranslationDao: ChapterTranslationDao,
    private val appDatabase: AppDatabase,
    private val bookChaptersRepository: BookChaptersRepository,
    private val downloaderRepository: DownloaderRepository,
) {
    suspend fun getAll() = chapterBodyDao.getAll()
    suspend fun insertReplace(chapterBodies: List<ChapterBody>) =
        chapterBodyDao.insertReplace(chapterBodies)

    private suspend fun insertReplace(chapterBody: ChapterBody) =
        chapterBodyDao.insertReplace(chapterBody)

    suspend fun removeRows(chaptersUrl: List<String>) {
        appDatabase.transaction {
            chaptersUrl.chunked(500).forEach { chunk ->
                chapterBodyDao.removeChapterRows(chunk)
                chapterPagesDao.removeRows(chunk)
                chunk.forEach { chapterUrl ->
                    chapterTranslationDao.deleteChapterTranslations(chapterUrl)
                }
            }
        }
    }

    private suspend fun insertWithTitle(chapterBody: ChapterBody, @Suppress("UNUSED_PARAMETER") title: String?) = appDatabase.transaction {
        insertReplace(chapterBody)
    }

    suspend fun count() = chapterBodyDao.count()
    suspend fun getChunk(limit: Int, offset: Int) = chapterBodyDao.getChunk(limit, offset)

    suspend fun clearAllCache(): Int = appDatabase.transaction {
        val count = chapterBodyDao.deleteAll()
        chapterPagesDao.deleteAll()
        chapterTranslationDao.deleteAllTranslations()
        count
    }

    suspend fun getCacheSizeBytes(): Long =
        chapterBodyDao.getCacheSizeBytes() + chapterPagesDao.getCacheSizeBytes()

    suspend fun getCachedBody(urlChapter: String): String? {
        return chapterBodyDao.get(urlChapter)?.body?.takeIf { it.isNotBlank() && isValidChapterContent(it) }
    }

    suspend fun fetchBody(urlChapter: String, tryCache: Boolean = true): Response<String> {
        if (tryCache) chapterBodyDao.get(urlChapter)?.let {
            // Возвращаем из кэша только валидный контент
            if (it.body.isNotBlank() && isValidChapterContent(it.body)) return@fetchBody Response.Success(it.body)
            // Удаляем невалидную запись чтобы не мешала следующим попыткам
            chapterBodyDao.removeChapterRows(listOf(urlChapter))
        }

        if (urlChapter.isLocalUri) {
            return Response.Error(
                """
                Unable to load chapter from url:
                $urlChapter
                
                Source is local but chapter content missing.
            """.trimIndent(), Exception()
            )
        }

        // Сетевой вызов — явно переключаемся на Dispatchers.IO
        return withContext(Dispatchers.IO) {
            downloaderRepository.bookChapter(urlChapter)
        }.map {
            // Сохраняем в БД только валидный контент
            if (it.body.isNotBlank() && isValidChapterContent(it.body)) {
                insertWithTitle(
                    chapterBody = ChapterBody(url = urlChapter, body = it.body),
                    title = it.title
                )
            }
            it.body
        }
    }

    /**
     * URL страниц манхвы/манги из кэша. null — кэша нет (или запись
     * повреждена); пустой список — источник опрошён, глава не страничная.
     */
    suspend fun getCachedPages(urlChapter: String): List<String>? {
        return chapterPagesDao.get(urlChapter)?.pages?.let(::decodePages)
    }

    /**
     * Один сетевой запрос HTML главы: если источник вернул страницы
     * (getPageList), кэширует их и пустое тело остаётся вне кэша тела.
     * Legacy-главы получают маркер "[]" и валидное тело — повторное
     * открытие не делает второго запроса (pages из кэша, тело из кэша).
     */
    suspend fun fetchPages(urlChapter: String, tryCache: Boolean = true): Response<List<String>> {
        if (tryCache) chapterPagesDao.get(urlChapter)?.let { cached ->
            val pages = decodePages(cached.pages)
            // "[]" = «источник опрошён, глава не страничная» — это ответ.
            if (pages != null) return@fetchPages Response.Success(pages)
        }

        if (urlChapter.isLocalUri) {
            return Response.Error(
                """
                Unable to load chapter from url:
                $urlChapter
                
                Source is local but chapter content missing.
            """.trimIndent(), Exception()
            )
        }

        return withContext(Dispatchers.IO) {
            downloaderRepository.bookChapter(urlChapter)
        }.map { chapterDownload ->
            val pages = chapterDownload.pages ?: emptyList()
            chapterPagesDao.insertReplace(ChapterPages(url = urlChapter, pages = encodePages(pages)))
            // Страничные главы: тело пустое и в кэш тела не пишется
            // (isValidChapterContent его всё равно отверг бы).
            if (chapterDownload.body.isNotBlank() && isValidChapterContent(chapterDownload.body)) {
                insertWithTitle(
                    chapterBody = ChapterBody(url = urlChapter, body = chapterDownload.body),
                    title = chapterDownload.title
                )
            }
            pages
        }
    }

    private fun encodePages(pages: List<String>): String =
        org.json.JSONArray(pages).toString()

    private fun decodePages(pagesJson: String): List<String>? = try {
        val arr = org.json.JSONArray(pagesJson)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: org.json.JSONException) {
        null
    }
}