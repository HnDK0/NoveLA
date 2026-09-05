package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.isValidChapterContent
import my.noveldokusha.core.map
import my.noveldokusha.core.utils.decodePages
import my.noveldokusha.core.utils.encodePages
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterPagesDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterPages
import timber.log.Timber
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
    private val downloadedPageChaptersStore: DownloadedPageChaptersStore,
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
        // Файлы скачанных страничных глав удаляем вместе с записями.
        downloadedPageChaptersStore.deleteChapters(chaptersUrl)
    }

    /**
     * Скачивание главы для оффлайн-доступа.
     * - Страничная глава (манхва/манга): файлы картинок в [DownloadedPageChaptersStore]
     *   (всегда оригиналы, без пересжатия), кэш списка страниц для оффлайн-открытия;
     *   возвращает "" — легитимный успех, ретраи не нужны.
     * - Текстовая глава: как [fetchBody] + сохранение тела в кэш.
     * - Ошибка: [Response.Error] — DownloadManager ретраит.
     */
    suspend fun fetchChapterForDownload(
        urlChapter: String,
    ): Response<String> {
        if (urlChapter.isLocalUri) {
            // Локальные главы — только текст, страниц у них нет.
            // Кэш обязателен: локальный источник не имеет API для перезагрузки.
            return fetchBody(urlChapter, tryCache = true)
        }
        return withContext(Dispatchers.IO) {
            downloaderRepository.bookChapter(urlChapter)
        }.let { response ->
            when (response) {
                is Response.Success -> {
                    val download = response.data
                    val pages = download.pages
                    if (!pages.isNullOrEmpty()) {
                        try {
                            chapterPagesDao.insertReplace(
                                ChapterPages(url = urlChapter, pages = encodePages(pages))
                            )
                            val bytes = downloadedPageChaptersStore.downloadChapter(urlChapter, pages)
                            Timber.d("page chapter downloaded: $urlChapter ($bytes bytes)")
                            Response.Success("")
                        } catch (e: Exception) {
                            Timber.e(e, "page download failed: $urlChapter")
                            Response.Error("Page download failed: ${e.message}", e)
                        }
                    } else if (download.body.isNotBlank() && isValidChapterContent(download.body)) {
                        insertWithTitle(
                            ChapterBody(url = urlChapter, body = download.body),
                            title = download.title
                        )
                        Response.Success(download.body)
                    } else {
                        Response.Error("Empty content for $urlChapter", Exception())
                    }
                }
                is Response.Error -> response
            }
        }
    }

    private suspend fun insertWithTitle(chapterBody: ChapterBody, title: String?) = appDatabase.transaction {
        insertReplace(chapterBody)
        // Название приходит из списка глав, а не из тела: пустой/пробельный
        // title не должен затирать сохранённое (регресс 5f96c931).
        if (!title.isNullOrBlank()) bookChaptersRepository.updateTitle(chapterBody.url, title)
    }

    suspend fun count() = chapterBodyDao.count()
    suspend fun getChunk(limit: Int, offset: Int) = chapterBodyDao.getChunk(limit, offset)

    suspend fun clearAllCache(): Int {
        val count = appDatabase.transaction {
            val c = chapterBodyDao.deleteAll()
            chapterPagesDao.deleteAll()
            chapterTranslationDao.deleteAllTranslations()
            c
        }
        // Файлы скачанных страничных глав — вместе со строками.
        downloadedPageChaptersStore.deleteAll()
        return count
    }

    suspend fun getCacheSizeBytes(): Long =
        chapterBodyDao.getCacheSizeBytes() + chapterPagesDao.getCacheSizeBytes() +
            downloadedPageChaptersStore.getDiskSizeBytes()

    suspend fun getCachedBody(urlChapter: String): String? {
        return chapterBodyDao.get(urlChapter)?.body?.takeIf { it.isNotBlank() && isValidChapterContent(it) }
    }

    suspend fun fetchBody(urlChapter: String, tryCache: Boolean = true): Response<String> {
        Timber.d("FetchBody: url=$urlChapter tryCache=$tryCache isLocal=${urlChapter.isLocalUri}")
        if (tryCache) chapterBodyDao.get(urlChapter)?.let {
            Timber.d("FetchBody: cache HIT url=$urlChapter bodyLen=${it.body.length}")
            // Локальные главы (fb2/epub) не имеют сетевого источника для перезагрузки:
            // Cloudflare-валидация к ним неприменима, а удаление записи необратимо.
            // Возвращаем тело как есть, даже короткое.
            if (urlChapter.isLocalUri) return@fetchBody Response.Success(it.body)
            // Возвращаем из кэша только валидный контент
            if (it.body.isNotBlank() && isValidChapterContent(it.body)) return@fetchBody Response.Success(it.body)
            // Удаляем невалидную запись чтобы не мешала следующим попыткам
            Timber.w("FetchBody: removed invalid cached body url=$urlChapter bodyLen=${it.body.length}")
            chapterBodyDao.removeChapterRows(listOf(urlChapter))
        }

        Timber.d("FetchBody: cache MISS url=$urlChapter")

        if (urlChapter.isLocalUri) {
            Timber.e("FetchBody: LOCAL ERROR — тело отсутствует в БД url=$urlChapter. Возможная причина: тело удалено старой версией или не вставлено при импорте. Нужен переимпорт книги.")
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
        }.onSuccess {
            Timber.d("FetchBody: network url=$urlChapter bodyLen=${it.body.length} valid=${it.body.isNotBlank() && isValidChapterContent(it.body)}")
        }.onError {
            Timber.w("FetchBody: network ERROR url=$urlChapter error=${it.message}")
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
        Timber.d("FetchPages: url=$urlChapter tryCache=$tryCache isLocal=${urlChapter.isLocalUri}")
        if (tryCache) chapterPagesDao.get(urlChapter)?.let { cached ->
            val pages = decodePages(cached.pages)
            // "[]" = «источник опрошён, глава не страничная» — это ответ.
            if (pages != null) {
                Timber.d("FetchPages: cache HIT url=$urlChapter pages=${pages.size}")
                return@fetchPages Response.Success(pages)
            }
        }

        Timber.d("FetchPages: cache MISS url=$urlChapter")

        // Офлайн-путь: строка кэша ChapterPages могла пропасть (очистка кэша,
        // обновление после скачивания), но глава скачана — список страниц
        // берём из DownloadedPageChaptersStore, сеть не нужна.
        downloadedPageChaptersStore.getChapterPages(urlChapter)?.let { pages ->
            Timber.d("FetchPages: offline store HIT url=$urlChapter pages=${pages.size}")
            return@fetchPages Response.Success(pages)
        }

        if (urlChapter.isLocalUri) {
            Timber.e("FetchPages: LOCAL ERROR — страницы отсутствуют в БД url=$urlChapter. Нужен переимпорт книги.")
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
        }.onSuccess { chapterDownload ->
            Timber.d("FetchPages: network url=$urlChapter pages=${chapterDownload.pages?.size ?: 0}")
        }.onError {
            Timber.w("FetchPages: network ERROR url=$urlChapter error=${it.message}")
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
}
