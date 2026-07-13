package my.noveldokusha.data

import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.isHttpsUrl
import timber.log.Timber
import my.noveldokusha.feature.local_database.tables.Book

suspend fun backfillCovers(
    books: List<Book>,
    appFileResolver: AppFileResolver,
    coverRepository: CoverRepository,
) {
    for (book in books) {
        val remote = book.coverImageUrl
        if (remote.isBlank() || !remote.isHttpsUrl) continue
        val folderName = appFileResolver.getLocalBookFolderName(book.url)
        val coverFile = appFileResolver.getStorageBookCoverImageFile(folderName)
        if (!coverRepository.ensureCover(coverFile, remote)) {
            Timber.w("Failed to download cover for ${book.url}")
        }
    }
}
