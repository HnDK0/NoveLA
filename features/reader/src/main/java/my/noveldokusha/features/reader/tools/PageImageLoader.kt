package my.noveldokusha.features.reader.tools

import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
) {
    companion object {
        private const val MAX_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB
        private const val CACHE_DIR = "page_images"
    }

    private val cacheDir: File = File(context.cacheDir, CACHE_DIR)

    private val mutex = Mutex()

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
     * Фоновая загрузка страниц, которые вот-вот станут видны (скролл
     * вперёд или следующая глава). Ошибки игнорируются — load() при
     * показе повторит запрос.
     */
    fun prefetch(urls: List<String>) {
        if (urls.isEmpty()) return
        prefetchScope.launch {
            urls.forEach { load(it) }
        }
    }

    /**
     * Возвращает страницу из кэша или скачивает её. null — ошибка сети/CDN.
     * Размеры декодируются из заголовка файла (inJustDecodeBounds).
     */
    suspend fun load(url: String): PageImage? {
        inflight[url]?.let { return it.await() }
        val deferred = kotlinx.coroutines.CompletableDeferred<PageImage?>()
        inflight[url] = deferred
        try {
            val result = doLoad(url)
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
