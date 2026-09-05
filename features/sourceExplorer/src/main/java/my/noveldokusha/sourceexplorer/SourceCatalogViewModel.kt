package my.noveldokusha.sourceexplorer

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.states.PagedListIteratorState
import my.noveldokusha.data.AppRepository
import my.noveldokusha.mappers.mapToBookMetadata
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.asMutableStateOf
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.feature.local_database.DAOs.BookTranslationDao
import my.noveldokusha.feature.local_database.tables.BookTranslation
import my.noveldokusha.network.interceptors.CloudflareBypassSignal
import my.noveldokusha.scraper.ActiveFilters
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.text_translator.domain.TranslationManager
import timber.log.Timber
import javax.inject.Inject

interface SourceCatalogStateBundle {
    var sourceBaseUrl: String
}

@HiltViewModel
internal class SourceCatalogViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val toasty: Toasty,
    private val bookTranslationDao: BookTranslationDao,
    private val translationManager: TranslationManager,
    stateHandle: SavedStateHandle,
    private val appPreferences: AppPreferences,
    scraper: Scraper,
) : ViewModel(), SourceCatalogStateBundle {

    override var sourceBaseUrl by StateExtra_String(stateHandle)
    private val source = scraper.getCompatibleSourceCatalog(sourceBaseUrl)!!
    private val filterableSource = source as? SourceInterface.FilterableCatalog
    private var lastBookmarkClickMs = 0L

    private val _filterList = mutableStateOf(emptyList<my.noveldokusha.scraper.LuaFilter>())
    private val _activeFilters = mutableStateOf(ActiveFilters())

    // Переводы названий книг каталога (url -> translatedTitle).
    // Отдельный реактивный map вместо мутации list[i] = copy(...): LazyGrid с
    // key={it.url} НЕ перекомпозирует item при замене объекта с тем же ключом,
    // поэтому title должен читаться из реактивного источника ВНУТРИ item-контента.
    private val _translatedTitles = mutableStateMapOf<String, String>()
    val translatedTitles: Map<String, String> = _translatedTitles

    val state = SourceCatalogScreenState(
        sourceCatalogNameStrId = mutableIntStateOf(source.nameStrId),
        sourceCatalogName      = mutableStateOf(source.name),
        searchTextInput        = stateHandle.asMutableStateOf("searchTextInput") { "" },
        toolbarMode            = stateHandle.asMutableStateOf("toolbarMode") { ToolbarMode.MAIN },
        fetchIterator          = PagedListIteratorState(viewModelScope) {
            source.getCatalogList(it).mapToBookMetadata()
        },
        listLayoutMode         = appPreferences.BOOKS_LIST_LAYOUT_MODE.state(viewModelScope),
        sortOrder              = appPreferences.SOURCE_SORT_ORDER.state(viewModelScope),
        gridColumns            = appPreferences.BOOKS_GRID_COLUMNS.state(viewModelScope),
        hasFilters             = filterableSource != null,
        filterList             = _filterList,
        activeFilters          = _activeFilters,
        isFilterSheetOpen      = mutableStateOf(false),
        translatedTitles       = translatedTitles,
    )

    init {
        onSearchCatalog()

        if (filterableSource != null) {
            viewModelScope.launch {
                filterableSource.getFilterList()
                    .onSuccess { _filterList.value = it }
                    .onError { Timber.e(it.exception, "Failed to load filter list") }
            }
        }

        // Перезагружаем текущий список после успешного обхода CF.
        // SharedFlow — сигнал получают все подписчики одновременно.
        // Сравниваем host источника чтобы не трогать каталоги других сайтов.
        viewModelScope.launch {
            val sourceHost = runCatching {
                android.net.Uri.parse(sourceBaseUrl).host
            }.getOrNull()

            CloudflareBypassSignal.bypassCompleted.collect { bypassedHost ->
                Timber.d("bypassCompleted received: $bypassedHost, sourceHost: $sourceHost")
                if (sourceHost != null && sourceHost == bypassedHost) {
                    Timber.d("Reloading catalog for $sourceHost")
                    state.fetchIterator.reset()
                    state.fetchIterator.fetchNext()
                }
            }
        }

        // FULL-scope: автоматически переводим названия книг каталога/поиска по мере
        // их появления (включая подгрузку следующих страниц). Всё выполняем одним
        // translateBatch-запросом на страницу (а не по одному названию), чтобы не
        // плодить N сетевых вызовов и не лагать скролл.
        viewModelScope.launch {
            val inflight = mutableSetOf<String>()
            snapshotFlow { state.fetchIterator.list.map { it.url }.toSet() }
                .collect { urls ->
                    // Убираем переводы url, которых больше нет в списке (поиск/фильтр/смена
                    // страницы) — иначе призрачные названия наложатся на новые книги.
                    _translatedTitles.keys.retainAll(urls)
                    // Активация по настройкам плагина (source.id): только если scope == FULL.
                    val extId = source.id
                    val enabled = appPreferences.translationEnabledForPlugin(extId)
                    val pair = appPreferences.translationPairForPlugin(extId)
                    val sourceLang = pair.source
                    val targetLang = pair.target
                    val scope = appPreferences.translationScopeForPlugin(extId)
                    val provider = appPreferences.translationProviderForPlugin(extId)
                    // Диагностика: почему названия в каталоге переводятся/нет.
                    Timber.d(
                        "catalogTitles: extId=%s enabled=%s source=%s target=%s scope=%s provider=%s",
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

                                val title = state.fetchIterator.list.find { it.url == url }?.title
                                if (title.isNullOrBlank()) return@forEach

                                val stored = bookTranslationDao
                                    .get(url, sourceLang, targetLang)
                                    ?.titleTranslation
                                    // Уже применённый к списку перевод (stored == title)
                                    // пропускаем — не шлём его повторно в translateBatch.
                                    ?.takeIf { it.isNotBlank() && it != title }
                                if (stored != null) toApply[url] = stored
                                else toTranslate[url] = title
                            }
                            // Диагностика: дошли ли до перевода и сколько чего набралось.
                            Timber.d(
                                "catalogClaims: urls=%d claimed=%d toApply=%d toTranslate=%d inflight=%d scope=%s",
                                urls.size, claimed.size, toApply.size, toTranslate.size, inflight.size, scope
                            )
                            if (claimed.isEmpty()) {
                                Timber.d("catalogClaims: empty claimed — skip batch (all in-flight or no titles)")
                                return@launch
                            }

                            // 1) Применяем уже сохранённые переводы к списку — без сети.
                            toApply.forEach { (url, translated) ->
                                _translatedTitles[url] = translated
                                Timber.d(
                                    "catalogApply: url=%s stored='%s' size=%d",
                                    url.takeLast(40), translated.take(40), _translatedTitles.size
                                )
                            }

                            // 2) Остальное — один запрос на страницу + запись в БД.
                            if (toTranslate.isNotEmpty()) {
                                Timber.d(
                                    "catalogTrans: translating %d titles source=%s target=%s provider=%s",
                                    toTranslate.size, sourceLang, targetLang, provider
                                )
                                val translatedByTitle = withContext(Dispatchers.Default) {
                                    translationManager.translateBatch(
                                        toTranslate.values.toList(),
                                        sourceLang,
                                        targetLang,
                                        null,
                                        provider
                                    )
                                }
                                toTranslate.forEach { (url, title) ->
                                    // Чистый батч: все названия страницы уходят в translateBatch
                                    // одним запросом. Per-title fallback НЕ используем — для
                                    // коротких одиночных названий обе гугловые схемы возвращают
                                    // оригинал (эхо). Если переводчик вернул эхо/пусто для
                                    // конкретного названия — просто пропускаем его (оставляем
                                    // оригинал), но не дробим запросы по одному.
                                    val translated = translatedByTitle[title]
                                    if (translated.isNullOrBlank() || translated == title) return@forEach
                                    bookTranslationDao.insertReplace(
                                        BookTranslation(
                                            bookUrl = url,
                                            sourceLang = sourceLang,
                                            targetLang = targetLang,
                                            titleTranslation = translated,
                                            descriptionTranslation = "",
                                        )
                                    )
                                    // Реактивно обновляем название в списке каталога через
                                    // _translatedTitles: запись в SnapshotStateMap внутри
                                    // snapshotFlow-коллектора триггерит recomposition item'а,
                                    // читающего title из map.
                                    _translatedTitles[url] = translated
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

    fun onSearchCatalog() {
        state.fetchIterator.setFunction { source.getCatalogList(it).mapToBookMetadata() }
        state.fetchIterator.reset()
        state.fetchIterator.fetchNext()
    }

    fun onSearchText(input: String) {
        state.fetchIterator.setFunction { source.getCatalogSearch(it, input).mapToBookMetadata() }
        state.fetchIterator.reset()
        state.fetchIterator.fetchNext()
    }

    fun onApplyFilters(filters: ActiveFilters) {
        _activeFilters.value = filters
        state.isFilterSheetOpen.value = false

        if (filterableSource != null && !filters.isEmpty) {
            state.fetchIterator.setFunction {
                filterableSource.getCatalogFiltered(it, filters).mapToBookMetadata()
            }
        } else {
            state.fetchIterator.setFunction { source.getCatalogList(it).mapToBookMetadata() }
        }
        state.fetchIterator.reset()
        state.fetchIterator.fetchNext()
    }

    fun onResetFilters() {
        onApplyFilters(ActiveFilters())
    }

    fun addToLibraryToggle(book: BookMetadata) {
        val now = System.currentTimeMillis()
        if (now - lastBookmarkClickMs < 300L) return
        lastBookmarkClickMs = now
        viewModelScope.launch {
            val isInLibrary =
                appRepository.toggleBookmark(
                    bookUrl = book.url,
                    bookTitle = book.title,
                    rating = book.rating,
                    contentType = book.contentType
                )
            val res = if (isInLibrary) R.string.added_to_library else R.string.removed_from_library
            toasty.show(res)
        }
    }
}