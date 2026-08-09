package my.noveldokusha.features.reader.tools

import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.network.NetworkClient
import okhttp3.CacheControl
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат загрузки страницы: файл + декодированные размеры (для
 * пропорции ряда). Файл кэшируется в page_images (LRU по размеру) —
 * Coil/OkHttp-кэши не задействуются, чтобы ошибки (404/503 от CDN) не
 * отравляли общий кэш: evict(url) просто удаляет файл.
 * URL грузятся ОРИГИНАЛЬНЫМИ (tachiyomi-style): никакого пересжатия.
 */
data class PageImage(
    val file: File,
    val width: Int,
    val height: Int
)

@Singleton
class PageImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkClient: NetworkClient,
    private val downloadedPageChaptersStore: DownloadedPageChaptersStore,
) {
    companion object {
        private const val MAX_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB
        private const val CACHE_DIR = "page_images"
    }

    private val cacheDir: File = File(context.cacheDir, CACHE_DIR)

    private val mutex = Mutex()

    /**
     * Пропорции страниц (raw URL → ширина/высота) для стабильной высоты
     * рядов: высота известна до появления картинки, ListView не дёргается.
     * Заполняется при загрузке/префетче и из локально скачанных файлов.
     */
    private val dimsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    // Дедупликация одновременных загрузок одной страницы (скролл + префетч).
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<PageImage?>>()
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO.limitedParallelism(4)
    )

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun fileFor(url: String): File = File(cacheDir, sha256(url) + ".img")

    /**
     * Размеры страницы без сетевых запросов: память → файл кэша. null —
     * неизвестны (страница ещё ни разу не грузилась). Только чтение
     * заголовка файла (inJustDecodeBounds) — безопасно с любого потока.
     * Скачанные главы: dims записываются в память при первом load().
     */
    fun getDimensions(chapterUrl: String, url: String): Pair<Int, Int>? {
        dimsCache[url]?.let { return it }
        val fromCache = fileFor(url)
            .takeIf { it.exists() && it.length() > 0 }
            ?.let { decodeBounds(it) }
        if (fromCache != null) dimsCache[url] = fromCache
        return fromCache
    }

    /**
     * Фоновая загрузка страниц, которые вот-вот станут видны (скролл
     * вперёд или следующая глава). Ошибки игнорируются — load() при
     * показе повторит запрос.
     */
    internal fun prefetch(pages: List<ReaderItem.Page>) {
        if (pages.isEmpty()) return
        prefetchScope.launch {
            pages.forEach { load(it.chapterUrl, it.url) }
        }
    }

    /**
     * Префетч страниц манга-ридера (следующие страницы текущей главы).
     * Ошибки игнорируются — load() при показе повторит запрос.
     */
    internal fun prefetchPages(chapterUrl: String, pages: List<my.noveldokusha.features.reader.manga.MangaPage>) {
        if (pages.isEmpty()) return
        prefetchScope.launch {
            pages.forEach { load(chapterUrl, it.url) }
        }
    }

    /**
     * Возвращает страницу из кэша или скачивает её (оригинал, без
     * пересжатия). null — ошибка сети/CDN. Размеры декодируются из
     * заголовка файла (inJustDecodeBounds). Ключ кэша/дедупликации —
     * исходный URL страницы.
     *
     * Сначала ищем локально скачанную копию (скачанная глава = оффлайн
     * чтение без сети): файл уже лежит в скачанном виде.
     */
    suspend fun load(chapterUrl: String, url: String): PageImage? {
        downloadedPageChaptersStore.getLocalPageFile(chapterUrl, url)?.let { file ->
            decodeBounds(file)?.let { dims ->
                dimsCache[url] = dims
                return PageImage(file, dims.first, dims.second)
            }
        }
        inflight[url]?.let { return it.await() }
        val deferred = kotlinx.coroutines.CompletableDeferred<PageImage?>()
        inflight[url] = deferred
        try {
            val result = doLoad(url)
            if (result != null) dimsCache[url] = result.width to result.height
            deferred.complete(result)
            return result
        } catch (e: Exception) {
            Timber.e(e, "PageImageLoader: unexpected error for $url")
            deferred.complete(null)
            return null
        } finally {
            inflight.remove(url)
        }
    }

    private suspend fun doLoad(url: String): PageImage? = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (file.exists() && file.length() > 0) {
            decodeBounds(file)?.let { return@withContext PageImage(file, it.first, it.second) }
            file.delete()
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", refererFor(url))
                .cacheControl(CacheControl.FORCE_NETWORK) // свой кэш, не OkHttp
                .build()
            networkClient.call(request.newBuilder()).use { response ->
                if (!response.isSuccessful) {
                    Timber.w("PageImageLoader: HTTP ${response.code} for $url")
                    return@withContext null
                }
                val bytes = response.body?.bytes() ?: return@withContext null
                if (bytes.isEmpty()) return@withContext null
                mutex.withLock {
                    cacheDir.mkdirs()
                    val tmp = File(cacheDir, file.name + ".tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(file)) {
                        tmp.delete()
                        file.writeBytes(bytes)
                    }
                }
                decodeBounds(file)?.let { PageImage(file, it.first, it.second) }
            }
        } catch (e: Exception) {
            Timber.e(e, "PageImageLoader: fetch failed for $url")
            file.delete()
            null
        }
    }

    /**
     * Удаляет запись из кэша (после ошибки рендера / 404) и ужимает кэш
     * до лимита (LRU по lastModified).
     */
    suspend fun evictAndTrim(url: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (url != null) fileFor(url).delete()
            val files = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".img") } ?: return@withLock
            var total = files.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return@withLock
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= MAX_CACHE_BYTES) return@withLock
                total -= f.length()
                f.delete()
            }
        }
    }

    private fun decodeBounds(file: File): Pair<Int, Int>? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    } catch (e: Exception) {
        null
    }

    private fun refererFor(url: String): String = try {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}/"
    } catch (_: Exception) {
        ""
    }
}
