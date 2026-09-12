package my.noveldokusha.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFileResolver @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        const val COVER_PATH_RELATIVE_TO_BOOK = "__cover_image"
        private const val MAX_FOLDER_NAME_LENGTH = 200
        private const val HASH_BYTE_COUNT = 16
    }

    val folderBooks = File(context.filesDir, "books")

    fun getLocalIfContentType(url: String, bookFolderName: String) =
        if (url.isContentUri) getLocalBookPath(bookFolderName) else url

    fun getLocalBookCoverPath(): String = Paths.get(
        COVER_PATH_RELATIVE_TO_BOOK
    ).toString().addLocalUriPrefix

    fun getLocalBookChapterPath(bookFolderName: String, chapterName: String): String = Paths.get(
        bookFolderName.removeLocalUriPrefix,
        chapterName.removeLocalUriPrefix
    ).toString().addLocalUriPrefix

    fun getLocalBookPath(bookFolderName: String): String = Paths.get(
        bookFolderName.removeLocalUriPrefix
    ).toString().addLocalUriPrefix

    fun getStorageBookCoverImageFile(bookFolderName: String): File =
        Paths.get(
            folderBooks.absolutePath,
            bookFolderName.removeLocalUriPrefix,
            COVER_PATH_RELATIVE_TO_BOOK
        ).toFile().also { ensureInsideBooksDir(it, bookFolderName) }

    fun getStorageBookImageFile(bookFolderName: String, imagePath: String): File {
        // ponytail: defensive — reject empty paths from plugins or corrupted EPUBs
        require(imagePath.isNotBlank()) { "Image path must not be blank" }
        val localBookFolderName = when {
            imagePath.isLocalUri -> getLocalBookFolderName(bookFolderName)
            else -> bookFolderName
        }
        return Paths.get(
            folderBooks.absolutePath,
            localBookFolderName.removeLocalUriPrefix,
            imagePath.removeLocalUriPrefix
        ).toFile().also { ensureInsideBooksDir(it, imagePath) }
    }

    /**
     * Отклоняет пути, которые через `..` выходят за пределы каталога книг.
     * Защита от записи в произвольные каталоги приложения (lua_extensions и т.п.)
     * через недоверенные имена путей из импортируемых книг.
     */
    private fun ensureInsideBooksDir(file: File, sourcePath: String) {
        val canonicalBase = folderBooks.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.path.startsWith(canonicalBase.path + File.separator)) {
            "Image path escapes the books directory: $sourcePath"
        }
    }

    fun getLocalBookFolderName(bookUrl: String): String = when {
        bookUrl.isHttpsUrl -> {
            val encoded = Base64.getEncoder().encodeToString(bookUrl.encodeToByteArray())
            if (encoded.length > MAX_FOLDER_NAME_LENGTH) {
                // Hash long names to stay within filesystem 255-byte limit
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(bookUrl.encodeToByteArray())
                    .take(HASH_BYTE_COUNT)
                    .joinToString("") { "%02x".format(it) }
                "h_$hash"
            } else {
                encoded
            }
        }
        bookUrl.isLocalUri -> bookUrl.removeLocalUriPrefix
        else -> bookUrl
    }

    /**
     * Возвращает путь к изображению: локальный File если обложка есть на диске,
     * иначе remote URL (для загрузки из сети).
     *
     * Для обложек (isCover=true) с HTTPS-URL проверяем наличие файла на диске.
     * Если файл существует — возвращаем его (Coil грузит локально, без сети).
     * Если файла нет — возвращаем remote URL (Coil скачает).
     * Повреждённые файлы Coil обработает сам (placeholder), что лучше 4-6 сек
     * DNS timeout при обращении к мёртвому домену.
     *
     * Для изображений глав (isCover=false) remote URL возвращается как есть.
     */
    fun resolvedBookImagePath(
        bookUrl: String,
        imagePath: String,
        isCover: Boolean = true
    ): Any {
        // ponytail: defensive — plugin returns empty cover path, don't crash
        if (imagePath.isBlank()) return ""
        val resolved = if (imagePath.startsWith("//")) "https:$imagePath" else imagePath
        return when {
            resolved.isContentUri -> resolved
            bookUrl.isContentUri -> resolved
            resolved.isHttpsUrl && isCover -> {
                val coverFile = getStorageBookCoverImageFile(getLocalBookFolderName(bookUrl))
                if (coverFile.exists()) coverFile else resolved
            }
            resolved.isHttpsUrl -> resolved
            else -> getStorageBookImageFile(bookUrl, resolved)
        }
    }
}

/**
 * Resolves the path to the image if local, no changes if non local.
 */
@Composable
fun rememberResolvedBookImagePath(
    bookUrl: String,
    imagePath: String,
    isCover: Boolean = true
): Any {
    val context = LocalContext.current
    val appFileResolver = remember(context) { AppFileResolver(context) }

    return remember(appFileResolver, bookUrl, imagePath, isCover) {
        appFileResolver.resolvedBookImagePath(
            bookUrl = bookUrl,
            imagePath = imagePath,
            isCover = isCover
        )
    }
}