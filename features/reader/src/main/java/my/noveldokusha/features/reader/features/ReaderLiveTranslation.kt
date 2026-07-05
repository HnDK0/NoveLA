package my.noveldokusha.features.reader.features

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.NovelPromptData
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState

@Stable
internal data class LiveTranslationSettingData(
    val isAvailable: Boolean,
    val enable: MutableState<Boolean>,
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
)

internal class ReaderLiveTranslation(
    private val translationManager: TranslationManager,
    private val appPreferences: AppPreferences,
    private val chapterTranslationDao: ChapterTranslationDao? = null,
    private val bookUrl: String = "",
    private val bookTitleInitial: String = "",
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("LiveTranslator")
    )
) {
    internal var bookTitle: String = bookTitleInitial
    // Callback to clear chapter cache (set by ReaderSession)
    var onClearChapterCache: (() -> Unit)? = null

    private fun resolveNovelPrompt(): String =
        appPreferences.TRANSLATION_NOVEL_PROMPTS.value[bookUrl]?.prompt ?: ""

    private fun resolveNovelPromptAppendMode(): Boolean =
        appPreferences.TRANSLATION_NOVEL_PROMPTS.value[bookUrl]?.appendMode ?: false

    val state = LiveTranslationSettingData(
        isAvailable = translationManager.available,
        listOfAvailableModels = translationManager.models,
        enable = mutableStateOf(appPreferences.GLOBAL_TRANSLATION_ENABLED.value),
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
        currentProvider = mutableStateOf(appPreferences.TRANSLATION_PROVIDER.value),
        onProviderChange = ::onProviderChange,
    )

    var translatorState: TranslatorState? = null
        private set

    private val _onTranslatorChanged = MutableSharedFlow<Unit>()
    val onTranslatorChanged = _onTranslatorChanged.asSharedFlow()

    suspend fun init() {
        Log.d(TAG, "init: starting")
        val source = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
        val target = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
        Log.d(TAG, "init: source=$source, target=$target")
        Log.d(TAG, "init: translationAvailable=${translationManager.available}")

        state.source.value = getValidTranslatorOrNull(source)
        state.target.value = getValidTranslatorOrNull(target)
        Log.d(TAG, "init: sourceModel=${state.source.value?.language}, targetModel=${state.target.value?.language}")

        updateTranslatorState()
        Log.d(TAG, "init: complete, translatorState=${translatorState != null}")
    }

    private suspend fun getValidTranslatorOrNull(language: String): TranslationModelState? {
        if (language.isBlank()) return null
        return translationManager.hasModelDownloaded(language)
    }

    /**
     * @return true if reader session needs to be updated
     */
    private fun updateTranslatorState(): Boolean {
        val isEnabled = state.enable.value
        val source = state.source.value
        val target = state.target.value

        Log.d(TAG, "updateTranslatorState: enabled=$isEnabled, source=${source?.language}, target=${target?.language}")

        val old = translatorState
        val new = when {
            !isEnabled -> {
                Log.d(TAG, "updateTranslatorState: translation disabled")
                null
            }
            source == null || target == null -> {
                Log.d(TAG, "updateTranslatorState: missing source or target model")
                null
            }
            source.language == target.language -> {
                Log.d(TAG, "updateTranslatorState: source and target are the same")
                null
            }
            else -> {
                try {
                    val systemPromptOverride = resolveSystemPromptOverride()
                    Log.d(TAG, "updateTranslatorState: creating translator, override='${systemPromptOverride?.take(200)}'")
                    translationManager.getTranslator(
                        source = source.language,
                        target = target.language,
                        systemPromptOverride = systemPromptOverride
                    ).also {
                        Log.d(TAG, "updateTranslatorState: translator created successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "updateTranslatorState: failed to create translator", e)
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
        Log.d(TAG, "onEnable: $it")
        try {
            state.enable.value = it
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value = it
            val update = updateTranslatorState()
            Log.d(TAG, "onEnable: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onEnable: error", e)
            throw e
        }
    }

    private fun onSourceChange(it: TranslationModelState?) {
        Log.d(TAG, "onSourceChange: ${it?.language}")
        try {
            state.source.value = it
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = it?.language ?: ""
            val update = updateTranslatorState()
            Log.d(TAG, "onSourceChange: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onSourceChange: error", e)
            throw e
        }
    }

    private fun onTargetChange(it: TranslationModelState?) {
        Log.d(TAG, "onTargetChange: ${it?.language}")
        try {
            state.target.value = it
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = it?.language ?: ""
            val update = updateTranslatorState()
            Log.d(TAG, "onTargetChange: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onTargetChange: error", e)
            throw e
        }
    }

    private fun onNovelPromptChange(prompt: String) {
        Log.d(TAG, "onNovelPromptChange: prompt='${prompt.take(200)}'")
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
        Log.d(TAG, "onNovelPromptChange: calling onRedoTranslation()")
        onRedoTranslation()
    }

    private fun onNovelPromptAppendModeChange(appendMode: Boolean) {
        Log.d(TAG, "onNovelPromptAppendModeChange: appendMode=$appendMode")
        state.novelPromptAppendMode.value = appendMode
        if (bookUrl.isNotBlank()) {
            val current = appPreferences.TRANSLATION_NOVEL_PROMPTS.value.toMutableMap()
            val existing = current[bookUrl] ?: NovelPromptData(title = bookTitle)
            current[bookUrl] = existing.copy(appendMode = appendMode)
            appPreferences.TRANSLATION_NOVEL_PROMPTS.value = current
        }
        Log.d(TAG, "onNovelPromptAppendModeChange: calling onRedoTranslation()")
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
                Log.d(TAG, "onRedoTranslation: starting, state.novelPrompt='${state.novelPrompt.value.take(200)}'")
                val source = state.source.value?.language ?: run {
                    Log.w(TAG, "onRedoTranslation: source is null")
                    return@launch
                }
                val target = state.target.value?.language ?: run {
                    Log.w(TAG, "onRedoTranslation: target is null")
                    return@launch
                }

                Log.d(TAG, "onRedoTranslation: invalidating cache for source=$source, target=$target")

                chapterTranslationDao?.let { dao ->
                    try {
                        Log.d(TAG, "onRedoTranslation: clearing ALL database cached translations")
                        val deletedCount = withContext(Dispatchers.IO) {
                            dao.deleteAllTranslations()
                        }
                        Log.d(TAG, "onRedoTranslation: database cache cleared successfully (deleted $deletedCount translations)")
                    } catch (e: Exception) {
                        Log.e(TAG, "onRedoTranslation: failed to clear database cache", e)
                    }
                } ?: Log.w(TAG, "onRedoTranslation: chapterTranslationDao is null, skipping database clear")

                onClearChapterCache?.invoke()
                    ?.also { Log.d(TAG, "onRedoTranslation: chapter cache cleared") }
                    ?: Log.w(TAG, "onRedoTranslation: no chapter cache clear callback")

                Log.d(TAG, "onRedoTranslation: forcing translator state update")
                translatorState = null
                val update = updateTranslatorState()
                Log.d(TAG, "onRedoTranslation: translator state updated, triggering reload")

                if (update) {
                    _onTranslatorChanged.emit(Unit)
                }
                Log.d(TAG, "onRedoTranslation: complete")
            } catch (e: Exception) {
                Log.e(TAG, "onRedoTranslation: error", e)
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
            Log.d(TAG, "getBatchTranslator: offline provider (MLKit), batch not available")
            return null
        }
        val currentState = translatorState
        if (currentState == null) {
            Log.w(TAG, "getBatchTranslator: translatorState is null — init() may not have completed yet!")
            return null
        }
        val source = currentState.source
        val target = currentState.target
        val systemPromptOverride = resolveSystemPromptOverride()
        Log.d(TAG, "getBatchTranslator: returning batch translator ($source → $target), appendMode=${state.novelPromptAppendMode.value}, hasOverride=${systemPromptOverride != null}")
        return { texts ->
            translationManager.translateBatch(texts, source, target, systemPromptOverride)
        }
    }

    companion object {
        private const val TAG = "ReaderLiveTranslation"
    }
}