package my.noveldokusha.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.feature.local_database.DAOs.DownloadedPageChaptersDao
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter
import my.noveldokusha.network.NetworkClient
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Скачанные страничные главы (манхва/манга): картинки лежат в
 * filesDir/downloaded_pages/<sha256(chapterUrl)>/000.ext, 001.ext…
 * Ряд в БД хранит исходные URL страниц (порядок = индексы файлов) и
 * суммарный РЕАЛЬНЫЙ размер сохранённых байт (quality всегда "HIGH" —
 * оригиналы без пересжатия). Удаление главы из приложения удаляет и
 * файлы (deleteChapters/deleteBookChapters).
 */
@Singleton
class DownloadedPageChaptersStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadedPageChaptersDao,
    private val networkClient: NetworkClient,
) {
    companion object {
        private const val ROOT_DIR = "downloaded_pages"
    }

    private val root: File
        get() = File(context.filesDir, ROOT_DIR)

    private fun chapterDir(chapterUrl: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(chapterUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, hash)
    }

    private fun pageFile(chapterUrl: String, index: Int, url: String): File =
        File(chapterDir(chapterUrl), "%03d.%s".format(index, extOf(url)))

    private fun extOf(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) } ?: "img"
    }

    private fun encodePages(pages: List<String>): String = JSONArray(pages).toString()

    private fun decodePages(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun isDownloaded(chapterUrl: String): Boolean = dao.get(chapterUrl) != null

    /**
     * Файл локально скачанной страницы (глава + исходный URL страницы),
     * или null. Используется ридером для оффлайн-рендера.
     */
    suspend fun getLocalPageFile(chapterUrl: String, pageUrl: String): File? {
        val row = dao.get(chapterUrl) ?: return null
        val pages = decodePages(row.pages)
        val idx = pages.indexOf(pageUrl)
        if (idx == -1) return null
        val file = pageFile(chapterUrl, idx, pageUrl)
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Скачивает страницы главы в приложение (оригиналы, без пересжатия).
     * Уже скачанные файлы переиспользуются (ретраи после частичного успеха
     * не качают заново). Возвращает СУММАРНЫЙ РАЗМЕР СОХРАНЁННЫХ байт —
     * точное значение, отображаемое в списке глав. Кидает IOException при
     * сетевой ошибке — DownloadManager ретраит главу целиком.
     */
    suspend fun downloadChapter(
        chapterUrl: String,
        pages: List<String>
    ): Long = withContext(Dispatchers.IO) {
        val dir = chapterDir(chapterUrl)
        dir.mkdirs()
        var total = 0L
        pages.forEachIndexed { index, pageUrl ->
            val file = pageFile(chapterUrl, index, pageUrl)
            if (file.exists() && file.length() > 0) {
                total += file.length()
                return@forEachIndexed
            }
            val fetchUrl = pageUrl
            val request = Request.Builder()
                .url(fetchUrl)
                .header("Referer", refererFor(fetchUrl))
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            networkClient.call(request.newBuilder()).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for $fetchUrl")
                }
                val bytes = response.body?.bytes()
                    ?: throw IOException("Empty body for $fetchUrl")
                if (bytes.isEmpty()) throw IOException("Empty body for $fetchUrl")
                file.writeBytes(bytes)
                total += bytes.size
            }
            if (index % 5 == 4) Timber.d("downloaded pages ${index + 1}/${pages.size} for $chapterUrl")
        }
        dao.insertReplace(
            DownloadedPageChapter(
                url = chapterUrl,
                pages = encodePages(pages),
                totalBytes = total,
                quality = "HIGH"
            )
        )
        total
    }

    suspend fun deleteChapters(chapterUrls: List<String>) = withContext(Dispatchers.IO) {
        if (chapterUrls.isEmpty()) return@withContext
        dao.removeRows(chapterUrls)
        chapterUrls.forEach { chapterDir(it).deleteRecursively() }
    }

    /**
     * Состояние страничных глав на диске (без строки метаданных):
     * url -> реальные байты файлов страниц + set каталогов, которые
     * существуют (главы скачаны). Сумма длин файлов — истина в последней
     * инстанции, переживает потерю строки/старые загрузки.
     */
    suspend fun getDiskState(chapterUrls: List<String>): Pair<Map<String, Long>, Set<String>> =
        withContext(Dispatchers.IO) {
            val sizes = HashMap<String, Long>()
            val dirs = HashSet<String>()
            chapterUrls.forEach { url ->
                val dir = chapterDir(url)
                if (dir.isDirectory) {
                    dirs.add(url)
                    val bytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
                    if (bytes > 0L) sizes[url] = bytes
                }
            }
            sizes to dirs
        }

    /**
     * Реальные байты всех скачанных страничных глав на диске
     * (файлы в downloaded_pages). Для отображения размера кэша —
     * истина в последней инстанции, не зависит от строк БД.
     */
    suspend fun getDiskSizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext 0L
        root.listFiles()
            ?.sumOf { dir ->
                if (dir.isDirectory) dir.listFiles()?.sumOf { it.length() } ?: 0L else 0L
            } ?: 0L
    }

    suspend fun deleteBookChapters(bookUrls: List<String>) = withContext(Dispatchers.IO) {
        if (bookUrls.isEmpty()) return@withContext
        val rows = dao.getByBookUrls(bookUrls)
        if (rows.isEmpty()) return@withContext
        dao.removeRows(rows.map { it.url })
        rows.forEach { chapterDir(it.url).deleteRecursively() }
    }

    /** Полная очистка: все файлы страничных глав + строки БД (настройки → данные). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        root.deleteRecursively()
    }

    private fun refererFor(url: String): String = try {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}/"
    } catch (_: Exception) {
        ""
    }
}
