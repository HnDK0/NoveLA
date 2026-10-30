package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.core.atomicWrite
import my.noveldokusha.core.isCoverValid
import my.noveldokusha.core.isHttpsUrl
import my.noveldokusha.core.isImage
import my.noveldokusha.core.utils.refererFor
import my.noveldokusha.network.NetworkClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverRepository @Inject constructor(
    private val networkClient: NetworkClient,
) {
    private val fileLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun ensureCover(
        coverFile: File,
        remoteUrl: String?,
        referer: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (remoteUrl.isNullOrBlank() || !remoteUrl.isHttpsUrl) return@withContext false
        if (isCoverValid(coverFile)) return@withContext true

        val lock = fileLocks.getOrPut(coverFile.absolutePath) { Mutex() }
        lock.withLock {
            if (isCoverValid(coverFile)) return@withLock true

            val maxAttempts = 3
            for (attempt in 1..maxAttempts) {
                val bytes = try {
                    // Явно переданный referer (со страницы книги) важнее same-origin
                    // от URL картинки. Пустой Referer недопустим: isHttpsUrl — лишь
                    // префиксная проверка, поэтому для некорректного URL (например
                    // "https://") refererFor вернёт "" — заголовок не добавляем.
                    val headers = (referer?.takeIf { it.isNotBlank() } ?: refererFor(remoteUrl))
                        .takeIf { it.isNotEmpty() }
                        ?.let { mapOf("Referer" to it) } ?: emptyMap()
                    networkClient.getWithHeaders(remoteUrl, headers).use { response ->
                        if (!response.isSuccessful) {
                            // 404/410 — URL мёртв, повторные запросы бессмысленны.
                            if (response.code == 404 || response.code == 410) {
                                return@withLock false
                            }
                            if (attempt < maxAttempts) {
                                delay(500L * attempt) // exponential backoff: 500ms, 1000ms
                                continue
                            }
                            return@withLock false
                        }
                        response.body.bytes()
                    }
                } catch (_: Exception) {
                    if (attempt < maxAttempts) {
                        delay(500L * attempt)
                        continue
                    }
                    return@withLock false
                }
                // bytes dijamin non-null oleh tipe pengembalian body.bytes() dan penanganan catch di atas
                if (bytes.isEmpty() || !isImage(bytes)) {
                    if (attempt < maxAttempts) {
                        delay(500L * attempt)
                        continue
                    }
                    return@withLock false
                }

                try {
                    atomicWrite(coverFile, bytes)
                    return@withLock true
                } catch (_: Exception) {
                    if (attempt < maxAttempts) {
                        delay(500L * attempt)
                        continue
                    }
                    return@withLock false
                }
            }
            false // unreachable, but satisfies compiler
        }
    }

}
