package my.noveldokusha.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Paths
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import my.noveldokusha.core.isCoverValid


@Singleton
class AppFileResolver @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        const val COVER_PATH_RELATIVE_TO_BOOK = "__cover_image"
    }

    val folderBooks = File(context.filesDir, "books")

    fun getLocalIfContentType(url: String, bookFolderName: String) =
        if (url.isContentUri) bookFolderName.addLocalUriPrefix else url

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

    fun getStorageBookCoverImageFile(bookFolderName: String): File = Paths.get(
        folderBooks.absolutePath,
        bookFolderName.removeLocalUriPrefix,
        COVER_PATH_RELATIVE_TO_BOOK
    ).toFile()

    fun getStorageBookImageFile(bookFolderName: String, imagePath: String): File {
        val localBookFolderName = when {
            imagePath.isLocalUri -> getLocalBookFolderName(bookFolderName)
            else -> bookFolderName
        }
        return Paths.get(
            folderBooks.absolutePath,
            localBookFolderName.removeLocalUriPrefix,
            imagePath.removeLocalUriPrefix
        ).toFile()
    }

    fun getLocalBookFolderName(bookUrl: String): String = when {
        bookUrl.isHttpsUrl -> Base64.getEncoder().encodeToString(bookUrl.encodeToByteArray())
        bookUrl.isLocalUri -> bookUrl.removeLocalUriPrefix
        else -> bookUrl
    }

    /**
     * Returns the path to the image if local, no changes if non local.
     *
     * For a cover image (isCover=true) with a remote (https) URL we prefer the locally
     * cached cover file when it exists and is a valid image; otherwise we return the
     * remote URL so a missing local cover (e.g. after a backup restore) is transparently
     * re-fetched from the site instead of showing a broken/empty image.
     *
     * For inline chapter images (isCover=false) remote URLs are returned as-is so Coil
     * fetches them directly from the source.
     */
    fun resolvedBookImagePath(
        bookUrl: String,
        imagePath: String,
        isCover: Boolean = true
    ): Any {
        val resolved = if (imagePath.startsWith("//")) "https:$imagePath" else imagePath
        return when {
            resolved.isContentUri -> resolved
            bookUrl.isContentUri -> resolved
            resolved.isHttpsUrl && isCover -> {
                val coverFile = getStorageBookCoverImageFile(getLocalBookFolderName(bookUrl))
                if (isCoverValid(coverFile)) coverFile else resolved
            }
            resolved.isHttpsUrl -> resolved
            else -> getStorageBookImageFile(bookUrl, resolved)
        }
    }
}

/**
 * Returns the path to the image if local, no changes if non local.
 */
@Composable
fun rememberResolvedBookImagePath(
    bookUrl: String,
    imagePath: String,
    isCover: Boolean = true
): Any {
    val context = LocalContext.current
    val appFileResolver = remember(context) { AppFileResolver(context) }
    return remember(context, bookUrl, imagePath, isCover) {
        mutableStateOf(
            appFileResolver.resolvedBookImagePath(
                bookUrl = bookUrl,
                imagePath = imagePath,
                isCover = isCover
            )
        )
    }.value
}