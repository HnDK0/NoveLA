package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.isCoverValid
import my.noveldokusha.core.isHttpsUrl
import my.noveldokusha.core.isImage
import my.noveldokusha.network.NetworkClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverRepository @Inject constructor(
    private val networkClient: NetworkClient,
) {
    suspend fun ensureCover(
        coverFile: File,
        remoteUrl: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (remoteUrl.isNullOrBlank() || !remoteUrl.isHttpsUrl) return@withContext false
        if (isCoverValid(coverFile)) return@withContext true

        val bytes = try {
            networkClient.get(remoteUrl).use { response ->
                if (!response.isSuccessful) return@withContext false
                response.body?.bytes()
            }
        } catch (_: Exception) {
            return@withContext false
        }
        if (bytes == null || bytes.isEmpty() || !isImage(bytes)) {
            return@withContext false
        }

        try {
            atomicWrite(coverFile, bytes)
        } catch (_: Exception) {
            return@withContext false
        }

        true
    }
}

private fun atomicWrite(target: File, bytes: ByteArray) {
    val tmp = File(target.path + ".tmp")
    try {
        tmp.parentFile?.mkdirs()
        FileOutputStream(tmp).use { fos ->
            fos.write(bytes)
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        if (!tmp.renameTo(target)) {
            throw IOException("CoverRepository: cannot commit cover file")
        }
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
}
