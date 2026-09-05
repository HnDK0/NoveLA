package my.noveldokusha.globalsourcesearch

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.utils.normalizeBookUrl
import my.noveldokusha.coreui.components.LibraryBadgeMaps
import my.noveldokusha.coreui.components.LibraryBadgeState
import my.noveldokusha.coreui.components.getLibraryBadgeState
import my.noveldokusha.coreui.states.PagedListIteratorState
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.CatalogItem
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.asMutableStateOf
import my.noveldokusha.feature.local_database.DAOs.BookTranslationDao
import my.noveldokusha.feature.local_database.tables.BookTranslation
import my.noveldokusha.network.interceptors.CloudflareBypassSignal
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.text_translator.domain.TranslationManager
import timber.log.Timber
import javax.inject.Inject

internal interface GlobalSourceSearchStateBundle {
    val initialInput: String
}

@HiltViewModel
internal class GlobalSourceSearchViewModel @Inject constructor(
    state: SavedStateHandle,
    private val appRepository: AppRepository,
    private val scraperRepository: ScraperRepository,
    private val appPreferences: AppPreferences,
    private val translationSettingsResolver: TranslationSettingsResolver,
    private val bookTranslationDao: BookTranslationDao,
    private val translationManager: TranslationManager,
) : ViewModel(), GlobalSourceSearchStateBundle {
    override val initialInput by StateExtra_String(state)

    @Volatile
    private var searchJob: Job? = null

    val searchInput = state.asMutableStateOf("searchInput") { initialInput }
    val sourcesResults = mutableStateListOf<SourceResults>()

    // In-library badge data: normalized URL -> count, title -> count
    private val _libraryBadgeData = mutableStateOf(LibraryBadgeMaps())
    val libraryBadgeData: State<LibraryBadgeMaps> = _libraryBadgeData

    fun getLibraryBadge(bookUrl: String, bookTitle: String): LibraryBadgeState? {
        val data = _libraryBadgeData.value
        return getLibraryBadgeState(bookUrl, bookTitle, data.urls, data.titles)
    }

    init {
        search(text = searchInput.value)

        viewModelScope.launch {
            appRepository.libraryBooks.booksInLibraryFlow
                .map { books ->
                    val urls = mutableMapOf<String, Int>()
                    val titles = mutableMapOf<String, Int>()
                    for (book in books) {
                        val normalizedUrl = normalizeBookUrl(book.url)
                        urls[normalizedUrl] = (urls[normalizedUrl] ?: 0) + 1
                        titles[book.title] = (titles[book.title] ?: 0) + 1
                    }
                    LibraryBadgeMaps(urls, titles)
                }
                .collect { _libraryBadgeData.value = it }
        }

        // После обхода CF перезапускаем поиск только для источников
        // чей домен совпадает с пройденным хостом.
        // SharedFlow — сигнал получают все подписчики одновременно.
        viewModelScope.launch {
            CloudflareBypassSignal.bypassCompleted.collect { bypassedHost ->
                sourcesResults
                    .filter { result ->
                        runCatching {
                            android.net.Uri.parse(result.source.catalog.baseUrl).host == bypassedHost
                        }.getOrDefault(false)
                    }
                    .forEach { result ->
                        result.fetchIterator.reset()
                        result.fetchIterator.fetchNext()
                    }
            }
        }
    }

    fun search(text: String) {
        if (text.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            sourcesResults.clear()
            scraperRepository.sourcesCatalogListFlow()
                .take(1)
                .collect { sources ->
                    sources.map { source ->
                        SourceResults(
                            source = source,
                            searchInput = text,
                            coroutineScope = this@launch,
                            appPreferences = appPreferences,
                            translationSettingsResolver = translationSettingsResolver,
                            bookTranslationDao = bookTranslationDao,
                            translationManager = translationManager,
                        )
                    }.let(sourcesResults::addAll)
                }
        }
    }
}

internal data class SourceResults(
    val source: CatalogItem,
    val searchInput: String,
    val coroutineScope: CoroutineScope,
    private val appPreferences: AppPreferences? = null,
    private val translationSettingsResolver: TranslationSettingsResolver? = null,
    private val bookTranslationDao: BookTranslationDao? = null,
    private val translationManager: TranslationManager? = null,
) {
    val fetchIterator = PagedListIteratorState<BookResult>(coroutineScope) {
        source.catalog.getCatalogSearch(it, searchInput)
    }

    init {
        fetchIterator.fetchNext()

        // FULL-scope: автоматически переводим названия результатов поиска по мере
        // их появления (включая подгрузку следующих страниц). Всё выполняем одним
        // translateBatch-запросом на страницу (а не по одному названию), чтобы не
        // плодить N сетевых вызовов и не лагать скролл. Механизм — как в
        // SourceCatalogViewModel, но для глобального поиска.
        // В preview (без DI) зависимости null — перевод не запускаем.
        val prefs = appPreferences
        val dao = bookTranslationDao
        val manager = translationManager
        if (prefs != null && dao != null && manager != null) {
            coroutineScope.launch {
                val inflight = mutableSetOf<String>()
                snapshotFlow { fetchIterator.list.map { it.url }.toSet() }
                    .collect { urls ->
                        // Активация по настройкам плагина (source.catalog.id): только если scope == FULL.
                        val extId = source.catalog.id
                        val enabled = prefs.translationEnabledForPlugin(extId)
                        val pair = prefs.translationPairForPlugin(extId)
                        val sourceLang = pair.source
                        val targetLang = pair.target
                        val scope = prefs.translationScopeForPlugin(extId)
                        val provider = prefs.translationProviderForPlugin(extId)
                        // Диагностика: почему названия в поиске переводятся/нет.
                        Timber.d(
                            "searchTitles: extId=%s enabled=%s source=%s target=%s scope=%s provider=%s",
                            extId, enabled, sourceLang, targetLang, scope, provider
                        )
                        if (!enabled) return@collect
                        if (sourceLang.isBlank() || targetLang.isBlank()) return@collect
                        if (scope != AppPreferences.TRANSLATION_SCOPE_FULL) return@collect

                        launch {
                            val claimed = mutableListOf<String>()
                            val toApply = linkedMapOf<String, String>()   // url -> сохранённый перевод
                            val toTranslate = linkedMapOf<String, String>() // url -> исходный title
                            try {
                                urls.forEach { url ->
                                    if (!inflight.add(url)) return@forEach
                                    claimed += url

                                    val title = fetchIterator.list.find { it.url == url }?.title
                                    if (title.isNullOrBlank()) return@forEach

                                    val stored = dao
                                        .get(url, sourceLang, targetLang)
                                        ?.titleTranslation
                                        ?.takeIf { it.isNotBlank() && it != title }
                                    if (stored != null) toApply[url] = stored
                                    else toTranslate[url] = title
                                }
                                if (claimed.isEmpty()) return@launch

                                // 1) Применяем уже сохранённые переводы к списку — без сети.
                                toApply.forEach { (url, translated) ->
                                    val idx = fetchIterator.list.indexOfFirst { it.url == url }
                                    if (idx >= 0) {
                                        fetchIterator.list[idx] = fetchIterator.list[idx].copy(title = translated)
                                    }
                                }

                                // 2) Остальное — один запрос на страницу + запись в БД.
                                if (toTranslate.isNotEmpty()) {
                                    val translatedByTitle = withContext(Dispatchers.Default) {
                                        manager.translateBatch(
                                            toTranslate.values.toList(),
                                            sourceLang,
                                            targetLang,
                                            null,
                                            provider
                                        )
                                    }
                                    toTranslate.forEach { (url, title) ->
                                        var translated = translatedByTitle[title]
                                        // Google PA/Free для коротких одиночных названий возвращает
                                        // оригинал (эхо) — тогда повторяем через translateTitle, у
                                        // которого есть контекстный fallback (обёртка-заполнитель)
                                        // для таких заголовков.
                                        if (translated.isNullOrBlank() || translated == title) {
                                            translated = manager.translateTitle(
                                                title, sourceLang, targetLang, provider
                                            )
                                        }
                                        if (translated.isNullOrBlank() || translated == title) return@forEach
                                        dao.insertReplace(
                                            BookTranslation(
                                                bookUrl = url,
                                                sourceLang = sourceLang,
                                                targetLang = targetLang,
                                                titleTranslation = translated,
                                                descriptionTranslation = "",
                                            )
                                        )
                                        // Реактивно обновляем название в списке поиска.
                                        val idx = fetchIterator.list.indexOfFirst { it.url == url }
                                        if (idx >= 0) {
                                            fetchIterator.list[idx] = fetchIterator.list[idx].copy(title = translated)
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "auto title translation failed")
                            } finally {
                                claimed.forEach { inflight.remove(it) }
                            }
                        }
                    }
            }
        }
    }
}
