package my.noveldokusha.features.reader

import android.net.Uri
import kotlinx.coroutines.flow.first
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.data.ScraperRepository

/**
 * Тип контента книги, определяющий, какой ридер открывать.
 */
enum class ReaderType {
    NOVEL,
    MANGA,
}

/**
 * Чистая функция маршрутизации по метке contentType.
 * Единственная метка манги — "manga"; всё остальное (включая null и "") — новелла.
 */
fun resolveReaderType(contentType: String?): ReaderType =
    if (contentType == "manga") ReaderType.MANGA else ReaderType.NOVEL

/**
 * Решение гейта ридера по сохранённому в БД contentType книги — БЕЗ сетевого probe.
 *
 * Книга может быть не добавлена в библиотеку (открыта напрямую из каталога): тогда
 * БД тип не знает, и тип берётся у источника (плагина), чей baseUrl совпадает с
 * host'ом bookUrl. Источники без метки content_type считаются новеллами.
 *
 * Ошибка чтения БД или отсутствие источника не должны ронять Activity: падаем в NOVEL,
 * новелл-ридер покажет свой error/retry.
 */
suspend fun resolveGateType(
    repo: LibraryBooksRepository,
    scraperRepository: ScraperRepository,
    bookUrl: String,
): ReaderType {
    val stored = runCatching { repo.get(bookUrl)?.contentType }.getOrNull()
    if (!stored.isNullOrEmpty()) return resolveReaderType(stored)

    val host = runCatching { Uri.parse(bookUrl).host }.getOrNull()
    val sourceType = if (host != null) {
        runCatching {
            scraperRepository.sourcesCatalogListFlow().first()
                .firstOrNull { catalog -> runCatching { Uri.parse(catalog.catalog.baseUrl).host == host }.getOrDefault(false) }
                ?.catalog?.contentType
        }.getOrNull()
    } else null

    return resolveReaderType(sourceType)
}
