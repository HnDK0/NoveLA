package my.noveldokusha.tooling.application_workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.isCoverValid
import my.noveldokusha.core.isHttpsUrl
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.data.CoverRepository
import my.noveldokusha.epub_tooling.exportStreaming
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.strings.R as StringsR
import org.json.JSONArray
import timber.log.Timber

/** Режим экспорта книги: оригинал (тела глав из кэша) или перевод. */
enum class ExportMode { ORIGINAL, TRANSLATION }

/**
 * Экспортирует книгу в EPUB через SAF (Storage Access Framework).
 *
 * Режимы:
 *  - "original" — главы из кэша тел (ChapterBody), язык = sourceLang;
 *  - "translation" — главы из переводов (ChapterTranslation) для пары языков,
 *    язык = targetLang. Главы с невалидным JSON перевода пропускаются.
 *
 * Прогресс публикуется в уведомление не чаще, чем раз в 5 глав
 * (каждый вызов modifyNotification → notify() — дорогая операция).
 */
class BookExportWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BookExportEntryPoint {
        fun appDatabase(): AppDatabase
        fun appPreferences(): AppPreferences
        fun notificationsCenter(): NotificationsCenter
        fun appFileResolver(): AppFileResolver
        fun coverRepository(): CoverRepository
    }

    companion object {
        const val TAG = "BookExport"

        private const val KEY_BOOK_URL = "book_url"
        private const val KEY_BOOK_TITLE = "book_title"
        private const val KEY_MODE = "mode"
        private const val KEY_SOURCE_LANG = "source_lang"
        private const val KEY_TARGET_LANG = "target_lang"
        private const val KEY_AVAILABLE_COUNT = "available_count"
        private const val KEY_DIRECTORY_URI = "directory_uri"

        fun cancelTask(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }

        fun enqueue(
            context: Context,
            bookUrl: String,
            bookTitle: String,
            mode: ExportMode,
            sourceLang: String,
            targetLang: String,
            availableCount: Int,
            directoryUri: String,
        ) {
            val request = OneTimeWorkRequestBuilder<BookExportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_BOOK_URL to bookUrl,
                        KEY_BOOK_TITLE to bookTitle,
                        KEY_MODE to mode.name,
                        KEY_SOURCE_LANG to sourceLang,
                        KEY_TARGET_LANG to targetLang,
                        KEY_AVAILABLE_COUNT to availableCount,
                        KEY_DIRECTORY_URI to directoryUri,
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun isDirectoryAccessible(context: Context, directoryUri: String): Boolean {
            return try {
                val treeUri = Uri.parse(directoryUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null
                )?.use { true } ?: false
            } catch (e: Exception) {
                Timber.e(e, "isDirectoryAccessible: FAILED")
                false
            }
        }
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BookExportEntryPoint::class.java
        )
        val appDatabase = entryPoint.appDatabase()
        val appPreferences = entryPoint.appPreferences()
        val notificationsCenter = entryPoint.notificationsCenter()
        val appFileResolver = entryPoint.appFileResolver()
        val coverRepository = entryPoint.coverRepository()

        val bookUrl = inputData.getString(KEY_BOOK_URL) ?: return Result.failure()
        val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: return Result.failure()
        val mode = inputData.getString(KEY_MODE) ?: return Result.failure()
        // Режим экспорта: строка из inputData — это ExportMode.name; при
        // неизвестном значении (старые данные) безопасно откатываемся к ORIGINAL.
        val exportMode = runCatching { ExportMode.valueOf(mode) }.getOrDefault(ExportMode.ORIGINAL)
        val sourceLang = inputData.getString(KEY_SOURCE_LANG) ?: ""
        val targetLang = inputData.getString(KEY_TARGET_LANG) ?: ""
        val directoryUri = inputData.getString(KEY_DIRECTORY_URI) ?: return Result.failure()

        val notification = BookExportNotification(bookTitle, context, notificationsCenter)

        // Имя файла вычисляется до обращения к БД: для проверки директории оно не
        // нужно, а сам запрос к БД на пути ошибки выполнять не стоит. Язык книги
        // вычисляется позже (после проверки глав) — см. ниже.
        val modeLabel = if (exportMode == ExportMode.ORIGINAL) {
            "Original"
        } else if (targetLang.isBlank()) {
            "Translation"
        } else {
            "Translation $targetLang"
        }
        val fileName = "${sanitize(bookTitle)} [$modeLabel].epub"

        // Валидация директории до чтения данных из БД: при недоступной директории
        // экономим лишние DB-запросы. close() здесь безвреден — уведомление ещё не показано.
        if (!withContext(Dispatchers.IO) { isDirectoryAccessible(context, directoryUri) }) {
            Timber.e("BookExport: directory NOT accessible, clearing EXPORT_DIRECTORY_URI")
            notification.close()
            appPreferences.EXPORT_DIRECTORY_URI.value = ""
            // Диалог выбора директории уже закрыт — без уведомления пользователь
            // не узнает о провале (B3): показываем ошибку экспорта.
            notification.showError(context.getString(StringsR.string.book_export_failed))
            return Result.failure()
        }

        val chapters = appDatabase.chapterDao().chapters(bookUrl)
        if (chapters.isEmpty()) {
            // Пустой список глав: молчаливый failure выглядит как «ничего не
            // произошло» — показываем уведомление.
            Timber.w("BookExport: nothing to export")
            notification.showError(context.getString(StringsR.string.book_export_no_chapters))
            return Result.failure()
        }

        // Язык книги: в translation-режиме — целевой язык перевода; в original —
        // исходный язык, а если он не задан — язык исходников из первой группы
        // переводов книги (он же язык оригинала), иначе "und" (RFC 5646).
        val language = if (exportMode == ExportMode.ORIGINAL) {
            sourceLang.ifBlank {
                appDatabase.chapterTranslationDao()
                    .getTranslationGroups(bookUrl)
                    .firstOrNull()?.sourceLang ?: "und"
            }
        } else targetLang

        // Прогресс считаем по числу глав, которые реально будут записаны
        // (availableCount приходит из ViewModel), а не по общему числу глав
        // книги: при частичных загрузках иначе индикатор не дойдёт до 100%.
        val availableCount = inputData.getInt(KEY_AVAILABLE_COUNT, 0)
        val progressTotal = if (availableCount > 0) max(1, availableCount) else chapters.size

        // Много-минутный экспорт держим foreground-сервисом, чтобы система не
        // убила worker при сворачивании приложения (FOREGROUND_SERVICE_DATA_SYNC
        // в манифесте). Если FGS запрещён политикой — экспорт продолжится как
        // обычный background-worker.
        // FGS стартуем ДО createDocument: если процесс умрёт между созданием
        // файла и стартом сервиса, останется осиротевший пустой файл (M7).
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            setForeground(ForegroundInfo(notification.notificationId, notification.foregroundNotification(chapters.size), foregroundType))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "BookExport: setForeground failed, continuing as background worker")
        }

        val treeUri = Uri.parse(directoryUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        // createDocument — блокирующий SAF-вызов, выполняем на Dispatchers.IO.
        // Исключение (отзыв прав, сбой провайдера) и null ловим в showError:
        // молчаливый failure в этой точке — невидимый для пользователя провал (B3).
        val createUri = try {
            withContext(Dispatchers.IO) {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    docUri,
                    "application/epub+zip",
                    fileName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "BookExport: createDocument FAILED")
            notification.showError(context.getString(StringsR.string.book_export_failed))
            return Result.failure()
        } ?: run {
            Timber.e("BookExport: FAILED to create file via SAF")
            notification.showError(context.getString(StringsR.string.book_export_failed))
            return Result.failure()
        }

        // Описание и обложка берутся из записи книги: описание кладём в
        // dc:description EPUB, обложку — из локального кэша (или скачиваем,
        // если кэша нет, но есть remote-URL). Автора локально не храним.
        val book = appDatabase.libraryDao().get(bookUrl)
        val description = book?.description?.takeIf { it.isNotBlank() }
        val coverBytes = loadCoverBytes(book, appFileResolver, coverRepository)

        try {
            // openOutputStream — блокирующий SAF-вызов, выполняем на Dispatchers.IO.
            val stream = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(createUri)
            } ?: run {
                Timber.e("BookExport: FAILED to open output stream")
                notification.showError(context.getString(StringsR.string.book_export_failed))
                context.contentResolver.delete(createUri, null, null)
                return Result.failure()
            }
            var lastProgress = 0
            var lastProgressNotify = 0L
            stream.use { outputStream ->
                exportStreaming(
                    outputStream,
                    bookTitle,
                    language,
                    chapters.size,
                    { offset, count ->
                        loadChapterBatch(appDatabase, chapters, offset, count, exportMode, sourceLang, targetLang)
                    },
                    coverBytes,
                    description
                ) { done, _ ->
                    lastProgress = done
                    // Троттлинг по времени: notify() на каждые 5 глав при быстром
                    // экспорте (тысячи глав в минуту) превышает системный лимит
                    // (~5 уведомлений/сек) — NotificationManager сбрасывает наше
                    // уведомление (Shedding в логах). Обновляем не чаще раза в
                    // секунду; финальное значение публикуем всегда.
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressNotify >= 1_000 || done == progressTotal) {
                        notification.showProgress(done, progressTotal)
                        lastProgressNotify = now
                    }
                }
            }
            // При пропущенных главах (нет тела/перевода) done не достигает total —
            // финальный штрих прогресса, чтобы индикатор не застревал на 99%.
            if (lastProgress < progressTotal) {
                notification.showProgress(lastProgress, progressTotal)
            }
            // SAF может переименовать файл при коллизии имён (добавляет " (1)") —
            // берём фактическое имя из созданного документа, а не запрошенное.
            val displayName = runCatching {
                context.contentResolver.query(
                    createUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: fileName
            // showComplete публикует auto-cancel уведомление, которое исчезнет само:
            // close() здесь убрал бы его из шторки сразу после показа (M2).
            notification.showComplete(displayName, createUri)
            return Result.success()
        } catch (e: CancellationException) {
            // Отмена: закрываем уведомление и убираем частичный файл, чтобы не оставлять битый EPUB.
            notification.close()
            context.contentResolver.delete(createUri, null, null)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "BookExport: EXPORT FAILED")
            notification.showError(context.getString(StringsR.string.book_export_failed))
            context.contentResolver.delete(createUri, null, null)
            return Result.failure()
        }
    }

    /**
     * Загружает батч глав (offset, count) для потокового экспорта: в
     * original-режиме — тела из кэша, в translation — тела переводов пары
     * языков (главы без валидного перевода пропускаются). Тела загружаются
     * только для запрошенного диапазона (≤ CHAPTER_BATCH), поэтому книги на
     * тысячи глав не держат весь текст в памяти (OOM на больших книгах).
     */
    private suspend fun loadChapterBatch(
        appDatabase: AppDatabase,
        chapters: List<Chapter>,
        offset: Int,
        count: Int,
        exportMode: ExportMode,
        sourceLang: String,
        targetLang: String,
    ): List<Pair<String, String>> {
        val batch = chapters.subList(offset, minOf(offset + count, chapters.size))
        val urls = batch.map { it.url }
        return if (exportMode == ExportMode.TRANSLATION) {
            val translations = appDatabase.chapterTranslationDao()
                .getTranslationsByChapterUrls(urls, sourceLang, targetLang)
                .filter { it.translatedParagraphs.isNotEmpty() }
                .associateBy { it.chapterUrl }
            batch.mapNotNull { chapter ->
                val translation = translations[chapter.url] ?: return@mapNotNull null
                val body = try {
                    val paragraphs = JSONArray(translation.translatedParagraphs)
                    (0 until paragraphs.length()).joinToString("\n\n") { paragraphs.getString(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "BookExport: invalid translation JSON for ${chapter.url}, skipping chapter")
                    return@mapNotNull null
                }
                (translation.titleTranslation.ifBlank { chapter.title }) to body
            }
        } else {
            val bodies = appDatabase.chapterBodyDao()
                .getBodiesByUrls(urls)
                .associate { it.url to it.body }
            batch.mapNotNull { chapter -> bodies[chapter.url]?.let { chapter.title to it } }
        }
    }

    /**
     * Возвращает байты обложки книги для встраивания в EPUB.
     * Приоритет: локальный кэш-файл (filesDir/books/<folder>/__cover_image);
     * если его нет или он битый — пробуем скачать с remote-URL через CoverRepository.
     * Ошибки не фатальны: EPUB без обложки валиден, просто не несёт картинку.
     */
    private suspend fun loadCoverBytes(
        book: Book?,
        appFileResolver: AppFileResolver,
        coverRepository: CoverRepository,
    ): ByteArray? = book?.let { b ->
        val coverFile = appFileResolver.getStorageBookCoverImageFile(
            appFileResolver.getLocalBookFolderName(b.url)
        )
        withContext(Dispatchers.IO) {
            if (isCoverValid(coverFile)) {
                coverFile.readBytes()
            } else {
                coverRepository.ensureCover(coverFile, b.coverImageUrl.takeIf { it.isHttpsUrl })
                if (isCoverValid(coverFile)) coverFile.readBytes() else null
            }
        }
    }

    // Имя файла — display-name для SAF: режем недопустимые символы и control-коды,
    // пустое имя заменяем заглушкой (пустой createDocument вернёт null).
    // SAF-лимит на длину display-name: длинные названия (>255 байт) ломают
    // createDocument — режем до 80 символов.
    private fun sanitize(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
        return cleaned.take(80).ifBlank { "book" }
    }
}