package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.map
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.ChapterBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterBodyRepository @Inject constructor(
    private val chapterBodyDao: ChapterBodyDao,
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
        chaptersUrl.chunked(500).forEach { chunk ->
            chapterBodyDao.removeChapterRows(chunk)
            chunk.forEach { chapterUrl ->
                chapterTranslationDao.deleteChapterTranslations(chapterUrl)
            }
        }
    }

    private suspend fun insertWithTitle(chapterBody: ChapterBody, @Suppress("UNUSED_PARAMETER") title: String?) = appDatabase.transaction {
        insertReplace(chapterBody)
    }

    suspend fun count() = chapterBodyDao.count()
    suspend fun getChunk(limit: Int, offset: Int) = chapterBodyDao.getChunk(limit, offset)

    suspend fun clearAllCache(): Int {
        val count = chapterBodyDao.deleteAll()
        chapterTranslationDao.deleteAllTranslations()
        return count
    }

    suspend fun getCacheSizeBytes(): Long = chapterBodyDao.getCacheSizeBytes()

    suspend fun getCachedBody(urlChapter: String): String? {
        return chapterBodyDao.get(urlChapter)?.body?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchBody(urlChapter: String, tryCache: Boolean = true): Response<String> {
        if (tryCache) chapterBodyDao.get(urlChapter)?.let {
            // Не возвращать пустое тело из кэша — оно могло быть сохранено при ошибке
            if (it.body.isNotBlank()) return@fetchBody Response.Success(it.body)
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
            // Сохраняем в БД только непустое тело
            if (it.body.isNotBlank()) {
                insertWithTitle(
                    chapterBody = ChapterBody(url = urlChapter, body = it.body),
                    title = it.title
                )
            }
            it.body
        }
    }
}