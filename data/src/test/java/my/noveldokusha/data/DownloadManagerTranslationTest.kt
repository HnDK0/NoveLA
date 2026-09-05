package my.noveldokusha.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslatorState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Интеграционный тест конвейера скачивания с per-plugin настройками перевода:
 * - enqueue(translateMode=true) пропускает главу через резолвер (plugin пара zh→ru);
 * - translateAndSave передаёт provider из plugin-настроек в translateBatch;
 * - ensureBookInfoTranslated (FULL scope) сохраняет заголовок+описание книги
 *   в BookTranslation ровно один раз (DAO get-guard против дублей).
 *
 * Реальная Room-БД через публичный AppDatabase.createRoom (AppRoomDatabase internal).
 * TranslationManager замокан — сетевых вызовов нет.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadManagerTranslationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val appPreferences = AppPreferences(context)
    private val scraper = mock<Scraper>()
    private val translationManager = mock<TranslationManager>()
    private val chapterBodyRepository = mock<ChapterBodyRepository>()
    private val downloadedPageChaptersStore = mock<DownloadedPageChaptersStore>()
    private val appRepository = mock<AppRepository>()
    private val libraryBooks = mock<LibraryBooksRepository>()
    private val bookChapters = mock<BookChaptersRepository>()

    private lateinit var db: AppDatabase
    private lateinit var manager: DownloadManager

    private val bookUrl = "https://lua_x.example/book/1"
    private val chapterUrl = "https://lua_x.example/book/1/ch/1"
    private val body =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam."

    @Before
    fun setUp() {
        db = AppDatabase.createRoom(context, "download-manager-translation-test")

        // Plugin-настройки источника "lua_x": FULL scope, пара zh→ru, провайдер GOOGLE_FREE.
        appPreferences.setTranslationPairForPlugin("lua_x", "zh", "ru")
        appPreferences.setTranslationEnabledForPlugin("lua_x", true)
        appPreferences.setTranslationScopeForPlugin("lua_x", "FULL")
        appPreferences.setTranslationProviderForPlugin("lua_x", "GOOGLE_FREE")

        val source = mock<SourceInterface.Catalog>()
        whenever(source.id).thenReturn("lua_x")
        whenever(scraper.getCompatibleSource(bookUrl)).thenReturn(source)

        val resolver = TranslationSettingsResolverImpl(appPreferences, scraper)

        runBlocking {
            // Метаданные книги для ensureBookInfoTranslated.
            whenever(appRepository.libraryBooks).thenReturn(libraryBooks)
            whenever(libraryBooks.get(bookUrl)).thenReturn(
                Book(title = "Title", url = bookUrl, description = "Desc")
            )
            whenever(appRepository.bookChapters).thenReturn(bookChapters)
            whenever(bookChapters.get(chapterUrl)).thenReturn(
                Chapter(title = "Chapter 1", url = chapterUrl, bookUrl = bookUrl, position = 1)
            )

            // Кэшированное тело главы — translateMode пропускает её в очередь.
            whenever(chapterBodyRepository.getCachedBody(chapterUrl)).thenReturn(body)
            whenever(downloadedPageChaptersStore.isDownloaded(chapterUrl)).thenReturn(false)

            // Переводы: каждый параграф → "TR:<параграф>", заголовок/описание — фиксированные.
            whenever(
                translationManager.translateBatch(any(), eq("zh"), eq("ru"), anyOrNull(), eq("GOOGLE_FREE"))
            ).thenAnswer { invocation ->
                invocation.getArgument<List<String>>(0).associateWith { "TR:$it" }
            }
            whenever(translationManager.translateTitle(any(), eq("zh"), eq("ru"), isNull()))
                .thenReturn("Переведённый заголовок")
        }
        whenever(translationManager.getTranslator(eq("zh"), eq("ru"), anyOrNull(), eq("GOOGLE_PA")))
            .thenReturn(TranslatorState("zh", "ru", translate = { "Переведённое описание" }))

        manager = DownloadManager(
            context = context,
            appPreferences = appPreferences,
            appRepository = appRepository,
            chapterBodyRepository = chapterBodyRepository,
            downloadedPageChaptersStore = downloadedPageChaptersStore,
            translationManager = translationManager,
            chapterTranslationDao = db.chapterTranslationDao(),
            bookTranslationDao = db.bookTranslationDao(),
            translationSettingsResolver = resolver,
            notificationsCenter = NotificationsCenter(context),
            downloadTaskDao = db.downloadTaskDao(),
        )
    }

    @After
    fun tearDown() {
        db.closeDatabase()
        appPreferences.context.getSharedPreferences("default", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `FULL scope job translates chapter with plugin provider and persists book info`() {
        runBlocking {
            val result = manager.enqueue("Title", bookUrl, listOf(chapterUrl), translateMode = true)
            assertTrue("ожидается Added, получено $result", result is EnqueueResult.Added)
            awaitCompletion(bookUrl)

            // Глава переведена с провайдером из plugin-настроек (не глобальным).
            verify(translationManager).translateBatch(any(), eq("zh"), eq("ru"), anyOrNull(), eq("GOOGLE_FREE"))

            // Метаданные книги сохранены для пары (zh, ru).
            val row = db.bookTranslationDao().get(bookUrl, "zh", "ru")
            assertNotNull("BookTranslation строка должна существовать", row)
            assertEquals("Переведённый заголовок", row?.titleTranslation)
            assertEquals("Переведённое описание", row?.descriptionTranslation)
        }
    }

    @Test
    fun `second job does not duplicate book info translation`() {
        runBlocking {
            manager.enqueue("Title", bookUrl, listOf(chapterUrl), translateMode = true)
            awaitCompletion(bookUrl)

            // Убираем перевод главы — иначе второй enqueue вернёт AllCached
            // (глава уже переведена) и задача не запустится.
            db.chapterTranslationDao().deleteChapterTranslations(chapterUrl)

            manager.enqueue("Title", bookUrl, listOf(chapterUrl), translateMode = true)
            awaitCompletion(bookUrl)

            // getTranslator вызывается только в ensureBookInfoTranslated (описание книги).
            // Один вызов за два прогона = DAO get-guard предотвратил повторный перевод.
            verify(translationManager, times(1)).getTranslator(eq("zh"), eq("ru"), anyOrNull(), eq("GOOGLE_PA"))
        }
    }

    private suspend fun awaitCompletion(bookUrl: String) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val task = manager.tasks.value.firstOrNull { it.bookUrl == bookUrl }
            if (task?.isCompleted == true) return
            delay(50)
        }
        fail("Download job did not complete within 30s")
    }
}