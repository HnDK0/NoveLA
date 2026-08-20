package my.noveldokusha.features.chapterslist

import android.content.ContentResolver
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Response
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.DownloadManager
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.data.LocalBookImporterRepository
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.DAOs.TranslationGroup
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.DAOs.ReadingHistoryDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterTranslation
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.strings.R as StringsR
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.tooling.application_workers.BookExportWorker
import my.noveldokusha.tooling.application_workers.ExportMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Тесты экспорта книги в EPUB: состояния диалога [ExportDialogState]
 * и вызов [ChaptersViewModel.enqueue] (инжектируемая точка воркера).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChaptersViewModelExportTest {

    private val bookUrl = "http://test/book"
    private val bookTitle = "Test Book"

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun chapter(url: String) = Chapter(title = url, url = url, bookUrl = bookUrl, position = 0)

    private fun body(url: String) = ChapterBody(url = url, body = "body of $url")

    private fun translation(url: String, source: String, target: String) =
        ChapterTranslation(chapterUrl = url, sourceLang = source, targetLang = target, translatedParagraphs = "[]")

    private suspend fun createViewModel(
        chapters: List<Chapter>,
        bodies: List<ChapterBody>,
        translations: List<ChapterTranslation>,
        exportDirectoryUri: String,
    ): Pair<ChaptersViewModel, AppPreferences.Preference<String>> {
        val chapterDao = mock<ChapterDao>().also {
            whenever(it.countByBookUrl(any())).thenReturn(chapters.size)
        }
        val chapterBodyDao = mock<ChapterBodyDao>().also {
            whenever(it.getBodiesByUrls(any())).thenReturn(bodies)
            // ViewModel считает скачанные главы агрегатным запросом, а не списком тел.
            whenever(it.countDownloadedBodies(any())).thenReturn(bodies.size)
        }
        val chapterTranslationDao = mock<ChapterTranslationDao>().also {
            // Метод имеет sourceLang/targetLang со значениями по умолчанию: при стабе
            // с одним any() Kotlin генерирует вызов $default с «сырыми» значениями "" —
            // Mockito падает с InvalidUseOfMatchersException. Стабим все три аргумента.
            whenever(it.getTranslationsByChapterUrls(any(), any(), any())).thenReturn(translations)
            // Агрегат групп переводов: считаем пары (source, target) по непустым JSON-текстам.
            whenever(it.getTranslationGroups(any())).thenReturn(
                translations
                    .filter { tr -> tr.translatedParagraphs.isNotBlank() }
                    .groupBy { tr -> tr.sourceLang to tr.targetLang }
                    .map { (pair, list) -> TranslationGroup(pair.first, pair.second, list.size) }
            )
        }

        val libraryBooks = mock<LibraryBooksRepository>().also {
            whenever(it.resolveStoredUrl(any())).thenReturn(bookUrl)
            whenever(it.getFlow(any())).thenReturn(flowOf<Book?>(null))
            whenever(it.getByUrl(any())).thenReturn(Book(title = bookTitle, url = bookUrl))
        }
        val bookChapters = mock<BookChaptersRepository>().also {
            whenever(it.hasChapters(any())).thenReturn(true)
        }
        val appRepository = mock<AppRepository>().also {
            whenever(it.libraryBooks).thenReturn(libraryBooks)
            whenever(it.bookChapters).thenReturn(bookChapters)
        }

        val downloaderRepository = mock<DownloaderRepository>().also {
            whenever(it.bookGenres(any())).thenReturn(Response.Success(emptyList()))
            whenever(it.bookRating(any())).thenReturn(Response.Success(null))
            whenever(it.bookStatus(any())).thenReturn(Response.Success(null))
            whenever(it.bookLastUpdate(any())).thenReturn(Response.Success(null))
        }

        val chaptersRepository = mock<ChaptersRepository>().also {
            whenever(it.getChaptersSortedFlow(any())).thenReturn(flowOf(emptyList()))
            whenever(it.getChapterSizesFlow(any())).thenReturn(flowOf(emptyMap()))
        }

        val downloadManager = mock<DownloadManager>().also {
            whenever(it.tasks).thenReturn(MutableStateFlow(emptyList()))
        }

        val appFileResolver = mock<AppFileResolver>().also {
            whenever(it.getLocalIfContentType(any(), any())).thenReturn(bookUrl)
        }

        val context = mock<Context>().also {
            whenever(it.contentResolver).thenReturn(mock<ContentResolver>())
            // Ресурсы Android в JVM-тесте недоступны: подставляем строку,
            // которую ViewModel запрашивает на export-путях.
            whenever(it.getString(StringsR.string.export_no_chapters)).thenReturn("Нет скачанных глав")
            whenever(it.getString(StringsR.string.export_no_translated_chapters)).thenReturn("Нет переведённых глав")
            whenever(it.getString(StringsR.string.export_started)).thenReturn("Export started")
        }

        val exportDirectory = mock<AppPreferences.Preference<String>>().also {
            whenever(it.value).thenReturn(exportDirectoryUri)
        }

        val preferences = mock<AppPreferences>().also { prefs ->
            fun <T> pref(value: T): AppPreferences.Preference<T> =
                mock<AppPreferences.Preference<T>>().also { whenever(it.value).thenReturn(value) }

            val chapterSort = pref(TernaryState.Inactive)
            whenever(chapterSort.state(any())).thenReturn(mutableStateOf(TernaryState.Inactive))

            val bookEnabledMap = pref(emptyMap<String, Boolean>())
            whenever(bookEnabledMap.flow()).thenReturn(flowOf(emptyMap<String, Boolean>()))
            val bookLangPair = pref(emptyMap<String, TranslationLangPair>())
            whenever(bookLangPair.flow()).thenReturn(flowOf(emptyMap<String, TranslationLangPair>()))
            val globalEnabled = pref(false)
            whenever(globalEnabled.flow()).thenReturn(flowOf(false))
            val globalTarget = pref("")
            whenever(globalTarget.flow()).thenReturn(flowOf(""))
            val globalMode = pref(false)
            whenever(globalMode.flow()).thenReturn(flowOf(false))

            whenever(prefs.CHAPTERS_SORT_ASCENDING).thenReturn(chapterSort)
            whenever(prefs.TRANSLATION_BOOK_ENABLED_MAP).thenReturn(bookEnabledMap)
            whenever(prefs.TRANSLATION_BOOK_LANG_PAIR).thenReturn(bookLangPair)
            whenever(prefs.GLOBAL_TRANSLATION_ENABLED).thenReturn(globalEnabled)
            whenever(prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET).thenReturn(globalTarget)
            whenever(prefs.TRANSLATION_GLOBAL_MODE).thenReturn(globalMode)
            whenever(prefs.EXPORT_DIRECTORY_URI).thenReturn(exportDirectory)
        }

        val stateHandle = SavedStateHandle().apply {
            set("rawBookUrl", bookUrl)
            set("bookTitle", bookTitle)
        }

        return ChaptersViewModel(
            appRepository = appRepository,
            context = context,
            appScope = mock<AppCoroutineScope>(),
            scraper = mock<Scraper>(),
            toasty = mock<Toasty>(),
            appPreferences = preferences,
            appFileResolver = appFileResolver,
            downloaderRepository = downloaderRepository,
            downloadManager = downloadManager,
            chaptersRepository = chaptersRepository,
            localBookImporterRepository = mock<LocalBookImporterRepository>(),
            libraryDao = mock<LibraryDao>(),
            chapterDao = chapterDao,
            chapterBodyDao = chapterBodyDao,
            chapterTranslationDao = chapterTranslationDao,
            translationManager = mock<TranslationManager>(),
            readingHistoryDao = mock<ReadingHistoryDao>(),
            stateHandle = stateHandle,
        ).also { vm ->
            // Подменяем реальный IO-хоп resolveExportDirectoryDisplayName
            // синхронной лямбдой — иначе тест зависит от реального потока.
            vm.resolveExportDirectoryName = { _, _ -> "Export" }
        } to exportDirectory
    }

    // ── Сценарий 1: нет скачанных глав ───────────────────────────────────────

    @Test
    fun `no downloaded chapters shows message and keeps dialog hidden`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, _) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2")),
            bodies = emptyList(),
            translations = emptyList(),
            exportDirectoryUri = "content://tree/export",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()

        assertEquals(ExportDialogState.Hidden, model.exportDialogState.value)
        assertEquals("Нет скачанных глав", model.exportMessage.value)
        assertTrue(enqueueCalls.isEmpty())
    }

    // ── Сценарий 2: скачано меньше, чем всего глав → экспорт сразу ───────────────

    @Test
    fun `partial download enqueues export immediately without Warning`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, _) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2"), chapter("$bookUrl/ch3")),
            bodies = listOf(body("$bookUrl/ch1")),
            translations = emptyList(),
            exportDirectoryUri = "content://tree/export",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()

        val choice = model.exportDialogState.value as ExportDialogState.ContentChoice
        assertEquals(3, choice.totalChapters)
        assertEquals(1, choice.downloadedChapters)

        model.onExportContentChosen("original", "en", "")

        // Частичная загрузка → экспорт запускается сразу без промежуточного диалога
        assertEquals(ExportDialogState.Hidden, model.exportDialogState.value)
        assertEquals(1, enqueueCalls.size)
        assertEquals(
            listOf(bookUrl, bookTitle, ExportMode.ORIGINAL, "en", "", 1, "content://tree/export"),
            enqueueCalls.single(),
        )
        assertEquals("Export started", model.exportMessage.value)
    }

    // ── Сценарий 3: всё скачано → подтверждение → enqueue + Hidden ──────────

    @Test
    fun `full download confirms and enqueues export`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, _) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2")),
            bodies = listOf(body("$bookUrl/ch1"), body("$bookUrl/ch2")),
            translations = listOf(
                translation("$bookUrl/ch1", "en", "ru"),
                translation("$bookUrl/ch2", "en", "ru"),
            ),
            exportDirectoryUri = "content://tree/export",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()

        val choice = model.exportDialogState.value as ExportDialogState.ContentChoice
        assertEquals(2, choice.totalChapters)
        assertEquals(2, choice.downloadedChapters)
        assertEquals(listOf(LangPair("en", "ru", 2)), choice.availableTranslations)

        model.onExportContentChosen("original", "en", "")

        assertEquals(ExportDialogState.Hidden, model.exportDialogState.value)
        assertEquals(1, enqueueCalls.size)
        assertEquals(
            listOf(bookUrl, bookTitle, ExportMode.ORIGINAL, "en", "", 2, "content://tree/export"),
            enqueueCalls.single(),
        )
        assertEquals("Export started", model.exportMessage.value)
    }

    // ── Сценарий 4: папка не выбрана → NeedDirectory → сохранение → enqueue ──

    @Test
    fun `blank export directory goes to NeedDirectory then enqueues after save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, exportDirectoryPref) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2")),
            bodies = listOf(body("$bookUrl/ch1"), body("$bookUrl/ch2")),
            translations = listOf(
                translation("$bookUrl/ch1", "en", "ru"),
                translation("$bookUrl/ch2", "en", "ru"),
            ),
            exportDirectoryUri = "",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()

        model.onExportContentChosen("translation", "en", "ru")

        assertEquals(ExportDialogState.NeedDirectory, model.exportDialogState.value)
        assertTrue(enqueueCalls.isEmpty())

        model.onExportDirectorySaved("content://tree/export")

        assertEquals(ExportDialogState.Hidden, model.exportDialogState.value)
        assertEquals(1, enqueueCalls.size)
        assertEquals(
            listOf(bookUrl, bookTitle, ExportMode.TRANSLATION, "en", "ru", 2, "content://tree/export"),
            enqueueCalls.single(),
        )
        assertEquals("Export started", model.exportMessage.value)
        verify(exportDirectoryPref).value = "content://tree/export"
    }

    // ── Сценарий 5: dismiss в NeedDirectory сбрасывает отложенный экспорт ────

    @Test
    fun `dismiss after NeedDirectory clears pending export`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, _) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2")),
            bodies = listOf(body("$bookUrl/ch1"), body("$bookUrl/ch2")),
            translations = emptyList(),
            exportDirectoryUri = "",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()

        model.onExportContentChosen("original", "", "")
        assertEquals(ExportDialogState.NeedDirectory, model.exportDialogState.value)

        model.onExportDialogDismiss()
        assertEquals(ExportDialogState.Hidden, model.exportDialogState.value)

        // Отложенный экспорт сброшен: сохранение папки не должно запускать воркер.
        model.onExportDirectorySaved("content://tree/export")
        assertTrue(enqueueCalls.isEmpty())
        assertEquals(ExportDialogState.Hidden, model.exportDialogState.value)
    }

    // ── Сценарий 7: Change папки в ContentChoice обновляет имя в диалоге ───────

    @Test
    fun `change folder in ContentChoice updates directory name in dialog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, exportDirectoryPref) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2")),
            bodies = listOf(body("$bookUrl/ch1"), body("$bookUrl/ch2")),
            translations = emptyList(),
            exportDirectoryUri = "",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()
        assertTrue(model.exportDialogState.value is ExportDialogState.ContentChoice)

        // Смена папки без отложенного экспорта: диалог остаётся, имя обновляется.
        model.onExportDirectorySaved("content://tree/new")
        advanceUntilIdle()

        val choice = model.exportDialogState.value as ExportDialogState.ContentChoice
        assertEquals("Export", choice.exportDirectoryName)
        assertTrue(enqueueCalls.isEmpty())
        verify(exportDirectoryPref).value = "content://tree/new"
    }

    // ── Сценарий 8: перевод без переведённых глав → сообщение, без диалога ────

    @Test
    fun `translation with zero translated chapters shows message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (model, _) = createViewModel(
            chapters = listOf(chapter("$bookUrl/ch1"), chapter("$bookUrl/ch2")),
            bodies = listOf(body("$bookUrl/ch1"), body("$bookUrl/ch2")),
            translations = listOf(
                ChapterTranslation(
                    chapterUrl = "$bookUrl/ch1",
                    sourceLang = "en",
                    targetLang = "ru",
                    translatedParagraphs = ""
                )
            ),
            exportDirectoryUri = "content://tree/export",
        )
        val enqueueCalls = mutableListOf<List<Any>>()
        model.enqueue = { _, bUrl, bTitle, mode, source, target, count, dir ->
            enqueueCalls.add(listOf(bUrl, bTitle, mode, source, target, count, dir))
        }

        model.onExportClicked(bookUrl, bookTitle)
        advanceUntilIdle()
        assertTrue(model.exportDialogState.value is ExportDialogState.ContentChoice)

        model.onExportContentChosen("translation", "en", "ru")

        assertEquals("Нет переведённых глав", model.exportMessage.value)
        assertTrue(model.exportDialogState.value is ExportDialogState.ContentChoice)
        assertTrue(enqueueCalls.isEmpty())
    }
}