package my.noveldokusha.features.reader.features

import timber.log.Timber
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.NovelPromptData
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver.ActiveTranslatorLevel
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState

@Stable
internal data class LiveTranslationSettingData(
    val isAvailable: Boolean,
    val enable: MutableState<Boolean>,
    // Уровень, реально обеспечивающий перевод (пер-новел/плагин/глобал/выкл) —
    // отдельно от [enable], т.к. перевод может идти плагином/глобально
    // даже при выключенном пер-новел свитче.
    val activeTranslatorLevel: MutableState<ActiveTranslatorLevel> = mutableStateOf(ActiveTranslatorLevel.NONE),
    // Имя плагина-источника (только когда уровень = PLUGIN).
    val activePluginName: MutableState<String?> = mutableStateOf(null),
    val listOfAvailableModels: SnapshotStateList<TranslationModelState>,
    val source: MutableState<TranslationModelState?>,
    val target: MutableState<TranslationModelState?>,
    val onEnable: (Boolean) -> Unit,
    val onSourceChange: (TranslationModelState?) -> Unit,
    val onTargetChange: (TranslationModelState?) -> Unit,
    val onDownloadTranslationModel: (language: String) -> Unit,
    val onRedoTranslation: () -> Unit,
    val bookUrl: String = "",
    val bookTitle: String = "",
    val novelPrompt: MutableState<String>,
    val onNovelPromptChange: (String) -> Unit,
    val novelPromptAppendMode: MutableState<Boolean>,
    val onNovelPromptAppendModeChange: (Boolean) -> Unit,
    val currentProvider: MutableState<String>,
    val onProviderChange: (String) -> Unit,
    val parallelEnabled: MutableState<Boolean>,
    val onParallelEnabledChange: (Boolean) -> Unit,
    val parallelOrder: MutableState<String>,
    val onParallelOrderChange: (String) -> Unit,
    val translationGlobalMode: MutableState<Boolean>,
    val onTranslationGlobalModeChange: (Boolean) -> Unit,
    // Избранные языки и последние пары для быстрого доступа в диалоге настроек.
    // SnapshotStateList — реактивный список: мутации внутри него триггерят рекомпозицию,
    // поэтому диалог сразу отражает переключение избранного и запись новых пар.
    val favoriteLanguages: SnapshotStateList<String> = mutableStateListOf(),
    val onToggleFavorite: (String) -> Unit = {},
    val recentPairs: SnapshotStateList<TranslationLangPair> = mutableStateListOf(),
    val onApplyRecentPair: (source: String, target: String) -> Unit = { _, _ -> },
    val onRemovePair: (TranslationLangPair) -> Unit = {},
)

/**
 * Решает, нужно ли записать новую пару в список последних.
 * Запись происходит только когда оба языка заданы и пара реально изменилась
 * относительно предыдущей — это исключает частичные (source, oldTarget) пары
 * и повторную запись без изменений.
 */
internal fun shouldRecordRecentPair(
    previousPair: TranslationLangPair?,
    newSource: String?,
    newTarget: String?,
): Boolean {
    val source = newSource.orEmpty()
    val target = newTarget.orEmpty()
    if (source.isBlank() || target.isBlank()) return false
    return TranslationLangPair(source = source, target = target) != previousPair
}

internal class ReaderLiveTranslation(
    private val translationManager: TranslationManager,
    private val appPreferences: AppPreferences,
    // Резолвер каскада настроек перевода: book → sourceId (plugin-level) → book → global.
    // Через него читаем enabled/pair с учётом плагинных настроек (P3b fix).
    private val translationSettingsResolver: TranslationSettingsResolver,
    private val chapterTranslationDao: ChapterTranslationDao? = null,
    private val bookUrl: String = "",
    private val bookTitleInitial: String = "",
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("LiveTranslator")
    )
) {
    internal var bookTitle: String = bookTitleInitial
    var currentChapterUrl: String = ""

    // Провайдер через каскад (resolveTranslationProvider): плагинный/книжный уровень
    // перекрывает глобальный. Значение записывается в state.currentProvider и передаётся
    // в getTranslator/translateBatch, иначе всегда срабатывал бы глобальный TRANSLATION_PROVIDER.
    private fun resolveProvider(): String =
        translationSettingsResolver.translationProviderForBook(bookUrl)
            ?: appPreferences.TRANSLATION_PROVIDER.value

    private fun resolveNovelPrompt(): String =
        appPreferences.TRANSLATION_NOVEL_PROMPTS.value[bookUrl]?.prompt ?: ""

    private fun resolveNovelPromptAppendMode(): Boolean =
        appPreferences.TRANSLATION_NOVEL_PROMPTS.value[bookUrl]?.appendMode ?: false

    // RAW-уровень диалога: значение переключателя книги БЕЗ каскада плагина/глобала.
    // Свитч управляет только тем уровнем, который реально редактируется диалогом:
    // в глобальном режиме — GLOBAL_TRANSLATION_ENABLED, вне — пер-новел карта.
    // Каскад (плагин/глобал могут переводить) учитывается отдельно в updateTranslatorState.
    private fun resolveRawEnable(): Boolean =
        if (appPreferences.TRANSLATION_GLOBAL_MODE.value)
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value
        else appPreferences.TRANSLATION_BOOK_ENABLED_MAP.value[bookUrl] == true

    // Реактивные списки избранных языков и последних пар: мутации внутри них
    // триггерят рекомпозицию, поэтому диалог сразу отражает изменения.
    private val favoriteLanguages = mutableStateListOf<String>().apply {
        addAll(appPreferences.favoriteLanguages())
    }
    private val recentPairs = mutableStateListOf<TranslationLangPair>().apply {
        addAll(appPreferences.recentTranslationPairs())
    }

    val state = LiveTranslationSettingData(
        isAvailable = translationManager.available,
        listOfAvailableModels = translationManager.models,
        enable = mutableStateOf(resolveRawEnable()),
        activeTranslatorLevel = mutableStateOf(
            translationSettingsResolver.activeTranslatorLevelForBook(bookUrl)
        ),
        activePluginName = mutableStateOf(
            translationSettingsResolver.pluginDisplayNameForBook(bookUrl)
        ),
        source = mutableStateOf(null),
        target = mutableStateOf(null),
        onEnable = ::onEnable,
        onSourceChange = ::onSourceChange,
        onTargetChange = ::onTargetChange,
        onDownloadTranslationModel = translationManager::downloadModel,
        onRedoTranslation = ::onRedoTranslation,
        bookUrl = bookUrl,
        bookTitle = bookTitleInitial,
        novelPrompt = mutableStateOf(resolveNovelPrompt()),
        onNovelPromptChange = ::onNovelPromptChange,
        novelPromptAppendMode = mutableStateOf(resolveNovelPromptAppendMode()),
        onNovelPromptAppendModeChange = ::onNovelPromptAppendModeChange,
        currentProvider = mutableStateOf(resolveProvider()),
        onProviderChange = ::onProviderChange,
        parallelEnabled = mutableStateOf(appPreferences.TRANSLATION_PARALLEL_ENABLED.value),
        onParallelEnabledChange = ::onParallelEnabledChange,
        parallelOrder = mutableStateOf(appPreferences.TRANSLATION_PARALLEL_ORDER.value),
        onParallelOrderChange = ::onParallelOrderChange,
        translationGlobalMode = mutableStateOf(appPreferences.TRANSLATION_GLOBAL_MODE.value),
        onTranslationGlobalModeChange = ::onTranslationGlobalModeChange,
        favoriteLanguages = favoriteLanguages,
        onToggleFavorite = { code ->
            appPreferences.toggleFavoriteLanguage(code)
            // Пересобираем реактивный список из префов, чтобы диалог сразу обновился.
            favoriteLanguages.clear()
            favoriteLanguages.addAll(appPreferences.favoriteLanguages())
        },
        recentPairs = recentPairs,
        onApplyRecentPair = ::onApplyRecentPair,
        onRemovePair = { pair ->
            appPreferences.removeRecentTranslationPair(pair)
            recentPairs.clear()
            recentPairs.addAll(appPreferences.recentTranslationPairs())
        },
    )

    var translatorState: TranslatorState? = null
        private set

    private val _onTranslatorChanged = MutableSharedFlow<Unit>()
    val onTranslatorChanged = _onTranslatorChanged.asSharedFlow()

    private val _onDisplaySettingsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onDisplaySettingsChanged = _onDisplaySettingsChanged.asSharedFlow()

    suspend fun init() {
        Timber.d("init: starting")
        Timber.d("init: translationAvailable=${translationManager.available}")
        refreshFromPrefs()
        updateTranslatorState()
        Timber.d("init: complete, translatorState=${translatorState != null}")
    }

    private suspend fun refreshFromPrefs() {
        // Пара для ОТОБРАЖЕНИЯ в диалоге — ХРАНИМАЯ (stored, без гейта enable):
        // выключенный пер-новел/глобал не должен скрывать сохранённую пару языков.
        // Фактический перевод использует КАСКАДНУЮ пару (updateTranslatorState), т.к.
        // переводить может включённый плагин/глобал поверх выключенного уровня диалога.
        val pair = appPreferences.storedTranslationPairForBook(bookUrl)
        Timber.d(
            "refreshFromPrefs: pair=(%s,%s) via stored, globalMode=%s (bg flag), " +
                "book=%s",
            pair.source, pair.target,
            appPreferences.TRANSLATION_GLOBAL_MODE.value,
            bookUrl.takeLast(40),
        )

        state.source.value = getValidTranslatorOrNull(pair.source)
        state.target.value = getValidTranslatorOrNull(pair.target)
        // Свитч показывает только raw-уровень диалога — см. resolveRawEnable.
        state.enable.value = resolveRawEnable()
        state.translationGlobalMode.value = appPreferences.TRANSLATION_GLOBAL_MODE.value
        // Плагинный/книжный провайдер важнее глобального — перечитываем через каскад.
        state.currentProvider.value = resolveProvider()
        // Полоска-статус: кто реально переводит (пер-новел/плагин/глобально/null=выкл).
        state.activeTranslatorLevel.value = translationSettingsResolver.activeTranslatorLevelForBook(bookUrl)
        state.activePluginName.value = translationSettingsResolver.pluginDisplayNameForBook(bookUrl)
        Timber.d("refreshFromPrefs: sourceModel=${state.source.value?.language}, targetModel=${state.target.value?.language}, enable=${state.enable.value}, provider=${state.currentProvider.value}")
    }

    private suspend fun getValidTranslatorOrNull(language: String): TranslationModelState? {
        if (language.isBlank()) return null
        return translationManager.hasModelDownloaded(language)
    }

    /**
     * @return true if reader session needs to be updated
     */
    private fun updateTranslatorState(): Boolean {
        // Фактический перевод — по КАСКАДУ (OR): плагин или глобальный перевод могут
        // переводить, даже если пер-новел свитч (raw) выключен. state.enable — лишь
        // отображение уровня диалога и здесь не источник истины для перевода.
        val isEnabled = translationSettingsResolver.translationEnabledForBook(bookUrl)

        // Источник истины для создания ПЕРЕВОДЧИКА — каскадная пара (book>plugin>global)
        // из резолвера, а НЕ state.source/target: те отображают хранимую пару диалога
        // (refreshFromPrefs) и при выключенном уровне отличаются от реально используемой
        // (когда переводит включённый плагин/глобал своей парой).
        // Проверка hasModelDownloaded здесь намеренно опущена: переводчики онлайн,
        // а каскадная пара строится из доступных языков (выбор из listOfAvailableModels).
        val pair = translationSettingsResolver.translationPairForBook(bookUrl)
        val source = pair.source
        val target = pair.target

        Timber.d("updateTranslatorState: enabled=$isEnabled, source=$source, target=$target")

        val old = translatorState
        val new = when {
            !isEnabled -> {
                Timber.d("updateTranslatorState: translation disabled")
                null
            }
            source.isBlank() || target.isBlank() -> {
                Timber.d("updateTranslatorState: missing source or target language")
                null
            }
            source == target -> {
                Timber.d("updateTranslatorState: source and target are the same")
                null
            }
            else -> {
                try {
                    val systemPromptOverride = resolveSystemPromptOverride()
                    val provider = state.currentProvider.value
                    Timber.d("updateTranslatorState: creating translator, override='${systemPromptOverride?.take(200)}', provider=$provider")
                    translationManager.getTranslator(
                        source = source,
                        target = target,
                        systemPromptOverride = systemPromptOverride,
                        provider = provider
                    ).also {
                        Timber.d("updateTranslatorState: translator created successfully")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "updateTranslatorState: failed to create translator")
                    throw e
                }
            }
        }.also { this.translatorState = it }

        return when {
            old == null && new == null -> false
            old != null && new != null -> when {
                old.source != new.source && old.target != new.target -> true
                else -> false
            }
            old == null && new != null -> new.source != new.target
            old != null && new == null -> old.source != old.target
            else -> true
        }
    }

    private fun onEnable(it: Boolean) {
        Timber.d("onEnable: $it")
        // Блокируем включение перевода, если для книги не задана языковая пара
        // (source или target пусты) — включать перевод без пары бессмысленно.
        // Используем hasStoredTranslationPairForBook (без гейта enable), т.к. пер-новел
        // может быть выключен, но пара сохранена — включение должно быть возможно.
        if (it) {
            if (!appPreferences.hasStoredTranslationPairForBook(bookUrl)) {
                Timber.w("onEnable: blocked — no language pair for book")
                return
            }
        }
        try {
            // Запись адаптивна к режиму (как в default): в глобальном режиме тумблер
            // книги управляет глобальным включением GLOBAL_TRANSLATION_ENABLED, вне
            // глобального режима — пер-новел картой TRANSLATION_BOOK_ENABLED_MAP.
            // Выключение сбрасывает per-novel к наследованию (ключа нет) — включённый
            // плагин или глобальная настройка тогда снова могут включить перевод.
            appPreferences.setTranslationEnabledForBook(bookUrl, it)
            state.enable.value = it
            // После смены уровня мог измениться активный переводчик (полоска-статус).
            state.activeTranslatorLevel.value =
                translationSettingsResolver.activeTranslatorLevelForBook(bookUrl)
            state.activePluginName.value =
                translationSettingsResolver.pluginDisplayNameForBook(bookUrl)
            val update = updateTranslatorState()
            Timber.d("onEnable: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "onEnable: error")
            throw e
        }
    }

    private fun onApplyRecentPair(source: String, target: String) {
        Timber.d("onApplyRecentPair: $source → $target")
        // Применение недавней пары: выставляем source+target в префах,
        // включаем перевод и обновляем состояние — тот же эффект, что onEnable(true).
        if (state.translationGlobalMode.value) {
            appPreferences.setGlobalTranslationPair(source, target)
        } else {
            appPreferences.setTranslationPairForBook(bookUrl, source, target)
        }
        appPreferences.setTranslationEnabledForBook(bookUrl, true)
        state.enable.value = true
        scope.launch {
            // Обновляем реактивные source/target (null остаётся null, если модель не скачана).
            state.source.value = getValidTranslatorOrNull(source)
            state.target.value = getValidTranslatorOrNull(target)
            // Полоска-статус после применения пары (пер-новел пара теперь активна).
            state.activeTranslatorLevel.value =
                translationSettingsResolver.activeTranslatorLevelForBook(bookUrl)
            state.activePluginName.value =
                translationSettingsResolver.pluginDisplayNameForBook(bookUrl)
            // Перезаписываем пару в начало списка последних.
            appPreferences.recordRecentTranslationPair(source, target)
            recentPairs.clear()
            recentPairs.addAll(appPreferences.recentTranslationPairs())
            val update = updateTranslatorState()
            if (update) _onTranslatorChanged.emit(Unit)
        }
    }

    private fun onSourceChange(it: TranslationModelState?) {
        Timber.d("onSourceChange: ${it?.language}")
        try {
            state.source.value = it
            if (state.translationGlobalMode.value) {
                appPreferences.setGlobalTranslationPair(
                    source = it?.language ?: "",
                    target = state.target.value?.language ?: "",
                )
            } else {
                appPreferences.setTranslationPairForBook(
                    bookUrl = bookUrl,
                    source = it?.language ?: "",
                    target = state.target.value?.language ?: "",
                )
            }
            // Запись последней пары — аддитивный побочный эффект, не меняет логику выше.
            val newSource = state.source.value?.language
            val newTarget = state.target.value?.language
            if (shouldRecordRecentPair(
                    previousPair = appPreferences.recentTranslationPairs().firstOrNull(),
                    newSource = newSource,
                    newTarget = newTarget,
                )
            ) {
                appPreferences.recordRecentTranslationPair(newSource.orEmpty(), newTarget.orEmpty())
                // Пересобираем реактивный список, чтобы новая пара появилась в диалоге сразу.
                state.recentPairs.clear()
                state.recentPairs.addAll(appPreferences.recentTranslationPairs())
            }
            // Смена пары могла изменить активного переводчика.
            state.activeTranslatorLevel.value =
                translationSettingsResolver.activeTranslatorLevelForBook(bookUrl)
            state.activePluginName.value =
                translationSettingsResolver.pluginDisplayNameForBook(bookUrl)
            val update = updateTranslatorState()
            Timber.d("onSourceChange: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "onSourceChange: error")
            throw e
        }
    }

    private fun onTargetChange(it: TranslationModelState?) {
        Timber.d("onTargetChange: ${it?.language}")
        try {
            state.target.value = it
            if (state.translationGlobalMode.value) {
                appPreferences.setGlobalTranslationPair(
                    source = state.source.value?.language ?: "",
                    target = it?.language ?: "",
                )
            } else {
                appPreferences.setTranslationPairForBook(
                    bookUrl = bookUrl,
                    source = state.source.value?.language ?: "",
                    target = it?.language ?: "",
                )
            }
            // Запись последней пары — аддитивный побочный эффект, не меняет логику выше.
            val newSource = state.source.value?.language
            val newTarget = state.target.value?.language
            if (shouldRecordRecentPair(
                    previousPair = appPreferences.recentTranslationPairs().firstOrNull(),
                    newSource = newSource,
                    newTarget = newTarget,
                )
            ) {
                appPreferences.recordRecentTranslationPair(newSource.orEmpty(), newTarget.orEmpty())
                // Пересобираем реактивный список, чтобы новая пара появилась в диалоге сразу.
                state.recentPairs.clear()
                state.recentPairs.addAll(appPreferences.recentTranslationPairs())
            }
            // Смена пары могла изменить активного переводчика.
            state.activeTranslatorLevel.value =
                translationSettingsResolver.activeTranslatorLevelForBook(bookUrl)
            state.activePluginName.value =
                translationSettingsResolver.pluginDisplayNameForBook(bookUrl)
            val update = updateTranslatorState()
            Timber.d("onTargetChange: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "onTargetChange: error")
            throw e
        }
    }

    private fun onTranslationGlobalModeChange(global: Boolean) {
        Timber.d("onTranslationGlobalModeChange: $global")
        // Блокируем включение глобального режима, если глобальная пара языков не задана
        // (source или target пусты) — глобальный режим без пары бессмысленен.
        if (global) {
            val globalSource = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
            val globalTarget = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
            if (globalSource.isBlank() || globalTarget.isBlank()) {
                Timber.w(
                    "onTranslationGlobalModeChange: blocked — no global language pair (source='%s', target='%s')",
                    globalSource, globalTarget,
                )
                return
            }
        }
        try {
            appPreferences.TRANSLATION_GLOBAL_MODE.value = global
            state.translationGlobalMode.value = global
            scope.launch {
                refreshFromPrefs()
                val update = updateTranslatorState()
                Timber.d("onTranslationGlobalModeChange: updateRequired=$update")
                if (update) _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "onTranslationGlobalModeChange: error")
            throw e
        }
    }

    private fun onNovelPromptChange(prompt: String) {
        Timber.d("onNovelPromptChange: prompt='${prompt.take(200)}'")
        state.novelPrompt.value = prompt
        if (bookUrl.isNotBlank()) {
            val current = appPreferences.TRANSLATION_NOVEL_PROMPTS.value.toMutableMap()
            if (prompt.isBlank()) {
                current.remove(bookUrl)
            } else {
                current[bookUrl] = NovelPromptData(
                    title = bookTitle,
                    prompt = prompt,
                    appendMode = state.novelPromptAppendMode.value,
                )
            }
            appPreferences.TRANSLATION_NOVEL_PROMPTS.value = current
        }
        Timber.d("onNovelPromptChange: calling onRedoTranslation()")
        onRedoTranslation()
    }

    private fun onNovelPromptAppendModeChange(appendMode: Boolean) {
        Timber.d("onNovelPromptAppendModeChange: appendMode=$appendMode")
        state.novelPromptAppendMode.value = appendMode
        if (bookUrl.isNotBlank()) {
            val current = appPreferences.TRANSLATION_NOVEL_PROMPTS.value.toMutableMap()
            val existing = current[bookUrl] ?: NovelPromptData(title = bookTitle)
            current[bookUrl] = existing.copy(appendMode = appendMode)
            appPreferences.TRANSLATION_NOVEL_PROMPTS.value = current
        }
        Timber.d("onNovelPromptAppendModeChange: calling onRedoTranslation()")
        onRedoTranslation()
    }

    private fun resolveSystemPromptOverride(): String? {
        val novelPrompt = state.novelPrompt.value
        if (novelPrompt.isBlank()) return null
        return if (state.novelPromptAppendMode.value) {
            val globalPrompt = appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value
            if (globalPrompt.isNotBlank()) "$globalPrompt\n\n$novelPrompt"
            else novelPrompt
        } else {
            novelPrompt
        }
    }

    private fun onProviderChange(provider: String) {
        appPreferences.TRANSLATION_PROVIDER.value = provider
        state.currentProvider.value = provider
        val update = updateTranslatorState()
        if (update) scope.launch {
            _onTranslatorChanged.emit(Unit)
        }
    }

    fun isUsingOnlineTranslation(): Boolean {
        return translationManager.isUsingOnlineTranslation
    }

    private fun onRedoTranslation() {
        scope.launch {
            try {
                Timber.d("onRedoTranslation: starting, state.novelPrompt='${state.novelPrompt.value.take(200)}'")
                val source = state.source.value?.language ?: run {
                    Timber.w("onRedoTranslation: source is null")
                    return@launch
                }
                val target = state.target.value?.language ?: run {
                    Timber.w("onRedoTranslation: target is null")
                    return@launch
                }

                Timber.d("onRedoTranslation: source=$source, target=$target, chapter=$currentChapterUrl")

                if (currentChapterUrl.isNotEmpty()) {
                    chapterTranslationDao?.let { dao ->
                        try {
                            Timber.d("onRedoTranslation: clearing translation for current chapter $currentChapterUrl")
                            withContext(Dispatchers.IO) {
                                dao.deleteChapterTranslations(currentChapterUrl)
                            }
                            Timber.d("onRedoTranslation: chapter translation cleared")
                        } catch (e: Exception) {
                            Timber.e(e, "onRedoTranslation: failed to clear chapter translation")
                        }
                    } ?: Timber.w("onRedoTranslation: chapterTranslationDao is null")
                }

                Timber.d("onRedoTranslation: forcing translator state update")
                translatorState = null
                val update = updateTranslatorState()
                Timber.d("onRedoTranslation: translator state updated, triggering reload")

                if (update) {
                    _onTranslatorChanged.emit(Unit)
                }
                Timber.d("onRedoTranslation: complete")
            } catch (e: Exception) {
                Timber.e(e, "onRedoTranslation: error")
            }
        }
    }

    /**
     * Get batch translator if available (Gemini, OpenAI, Composite, or GoogleFree).
     * Returns null for MLKit which doesn't support batch translation.
     * For online providers (Gemini/OpenAI), null means init() hasn't completed yet —
     * ReaderChaptersLoader will throw an error instead of falling back to per-paragraph.
     */
    fun getBatchTranslator(): (suspend (List<String>) -> Map<String, String>)? {
        if (!translationManager.isUsingOnlineTranslation) {
            Timber.d("getBatchTranslator: offline provider (MLKit), batch not available")
            return null
        }
        val currentState = translatorState
        if (currentState == null) {
            Timber.w("getBatchTranslator: translatorState is null — init() may not have completed yet!")
            return null
        }
        val source = currentState.source
        val target = currentState.target
        val systemPromptOverride = resolveSystemPromptOverride()
        val provider = state.currentProvider.value
        Timber.d("getBatchTranslator: returning batch translator ($source → $target), appendMode=${state.novelPromptAppendMode.value}, hasOverride=${systemPromptOverride != null}, provider=$provider")
        return { texts ->
            translationManager.translateBatch(texts, source, target, systemPromptOverride, provider)
        }
    }

    private fun onParallelEnabledChange(it: Boolean) {
        state.parallelEnabled.value = it
        appPreferences.TRANSLATION_PARALLEL_ENABLED.value = it
        scope.launch { _onDisplaySettingsChanged.emit(Unit) }
    }

    private fun onParallelOrderChange(it: String) {
        state.parallelOrder.value = it
        appPreferences.TRANSLATION_PARALLEL_ORDER.value = it
        scope.launch { _onDisplaySettingsChanged.emit(Unit) }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
    }
}