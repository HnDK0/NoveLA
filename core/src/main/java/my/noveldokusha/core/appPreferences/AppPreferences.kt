@file:Suppress("PropertyName")

package my.noveldokusha.core.appPreferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.SharedPreference_Boolean
import my.noveldokusha.core.SharedPreference_Enum
import my.noveldokusha.core.SharedPreference_Float
import my.noveldokusha.core.SharedPreference_Int
import my.noveldokusha.core.SharedPreference_Serializable
import my.noveldokusha.core.SharedPreference_String
import my.noveldokusha.core.SharedPreference_StringSet
import my.noveldokusha.core.models.RegexRule
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class NovelPromptData(
    val title: String = "",
    val prompt: String = "",
    val appendMode: Boolean = false,
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext val context: Context,
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val preferencesChangeListeners =
        java.util.Collections.synchronizedSet(
            mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        )

    val APP_LANGUAGE_CODE = object : Preference<String>("APP_LANGUAGE_CODE") {
        override var value by SharedPreference_String(name, preferences, "en")
    }

    val IS_FIRST_LAUNCH_DONE = object : Preference<Boolean>("IS_FIRST_LAUNCH_DONE") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    val IS_FOLLOW_SYSTEM_LANGUAGE = object : Preference<Boolean>("IS_FOLLOW_SYSTEM_LANGUAGE") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    init {
        migrateAppLanguage()
    }

    private fun migrateAppLanguage() {
        if (preferences.contains("APP_LANGUAGE") && !preferences.contains("APP_LANGUAGE_CODE")) {
            val oldValue = preferences.getString("APP_LANGUAGE", null)
            if (oldValue != null) {
                val code = when (oldValue) {
                    "ENGLISH" -> "en"
                    "RUSSIAN" -> "ru"
                    else -> null
                }
                if (code != null) {
                    APP_LANGUAGE_CODE.value = code
                }
            }
            IS_FIRST_LAUNCH_DONE.value = true
        }
    }

    val APP_THEME = object : Preference<String>("APP_THEME") {
        override var value by SharedPreference_String(name, preferences, "DEFAULT")
    }
    val THEME_DARK_MODE = object : Preference<String>("THEME_DARK_MODE") {
        override var value by SharedPreference_String(name, preferences, "SYSTEM")
    }
    val READER_FONT_SIZE = object : Preference<Float>("READER_FONT_SIZE") {
        override var value by SharedPreference_Float(name, preferences, 14f)
    }
    val READER_FONT_FAMILY = object : Preference<String>("READER_FONT_FAMILY") {
        override var value by SharedPreference_String(name, preferences, "serif")
    }
    val READER_LINE_HEIGHT = object : Preference<Float>("READER_LINE_HEIGHT") {
        override var value by SharedPreference_Float(name, preferences, 1.35f)
    }
    val READER_PARAGRAPH_SPACING = object : Preference<Float>("READER_PARAGRAPH_SPACING") {
        override var value by SharedPreference_Float(name, preferences, 8f)
    }
    val READER_TEXT_TO_SPEECH_VOICE_ID =
        object : Preference<String>("READER_TEXT_TO_SPEECH_VOICE_ID") {
            override var value by SharedPreference_String(name, preferences, "")
        }
    val READER_TEXT_TO_SPEECH_VOICE_ID_ORIGINAL =
        object : Preference<String>("READER_TEXT_TO_SPEECH_VOICE_ID_ORIGINAL") {
            override var value by SharedPreference_String(name, preferences, "")
        }
    // Пакет TTS-движка, которому принадлежит сохранённый голос (например "com.rhvoice.android")
    val READER_TEXT_TO_SPEECH_VOICE_ENGINE =
        object : Preference<String>("READER_TEXT_TO_SPEECH_VOICE_ENGINE") {
            override var value by SharedPreference_String(name, preferences, "")
        }
    val READER_TEXT_TO_SPEECH_VOICE_SPEED =
        object : Preference<Float>("READER_TEXT_TO_SPEECH_VOICE_SPEED") {
            override var value by SharedPreference_Float(name, preferences, 1f)
        }
    val READER_TEXT_TO_SPEECH_VOICE_PITCH =
        object : Preference<Float>("READER_TEXT_TO_SPEECH_VOICE_PITCH") {
            override var value by SharedPreference_Float(name, preferences, 1f)
        }

    val READER_TEXT_TO_SPEECH_SAVED_PREDEFINED_LIST =
        object : Preference<List<VoicePredefineState>>(
            "READER_TEXT_TO_SPEECH_SAVED_PREDEFINED_LIST"
        ) {
            override var value by SharedPreference_Serializable<List<VoicePredefineState>>(
                name = name,
                sharedPreferences = preferences,
                defaultValue = listOf(),
                encode = { Json.encodeToString(it) },
                decode = { Json.decodeFromString(it) }
            )
        }

    val FLOATING_TTS_ENABLED = object : Preference<Boolean>("FLOATING_TTS_ENABLED") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    val FLOATING_TTS_SHOW_OUTSIDE_APP = object : Preference<Boolean>("FLOATING_TTS_SHOW_OUTSIDE_APP") {
        override var value by SharedPreference_Boolean(name, preferences, true)
    }

    val FLOATING_TTS_OPACITY = object : Preference<Float>("FLOATING_TTS_OPACITY") {
        override var value by SharedPreference_Float(name, preferences, 0.95f)
    }

    val FLOATING_TTS_POS_X = object : Preference<Float>("FLOATING_TTS_POS_X") {
        override var value by SharedPreference_Float(name, preferences, -1f)
    }

    val FLOATING_TTS_POS_Y = object : Preference<Float>("FLOATING_TTS_POS_Y") {
        override var value by SharedPreference_Float(name, preferences, -1f)
    }

    val FLOATING_TTS_PANEL_WIDTH = object : Preference<Float>("FLOATING_TTS_PANEL_WIDTH") {
        override var value by SharedPreference_Float(name, preferences, 300f)
    }

    val FLOATING_TTS_PANEL_POS_X = object : Preference<Float>("FLOATING_TTS_PANEL_POS_X") {
        override var value by SharedPreference_Float(name, preferences, -1f)
    }

    val FLOATING_TTS_PANEL_POS_Y = object : Preference<Float>("FLOATING_TTS_PANEL_POS_Y") {
        override var value by SharedPreference_Float(name, preferences, -1f)
    }

    val FLOATING_TTS_PARAGRAPH_MODE = object : Preference<String>("FLOATING_TTS_PARAGRAPH_MODE") {
        override var value by SharedPreference_String(name, preferences, "tts")
    }

    val READER_SELECTABLE_TEXT = object : Preference<Boolean>("READER_SELECTABLE_TEXT") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    val READER_KEEP_SCREEN_ON = object : Preference<Boolean>("READER_KEEP_SCREEN_ON") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    val READER_FULL_SCREEN = object : Preference<Boolean>("READER_FULL_SCREEN") {
        override var value by SharedPreference_Boolean(name, preferences, true)
    }

    val READER_SINGLE_TAP_TO_OPEN_SETTINGS = object : Preference<Boolean>("READER_SINGLE_TAP_TO_OPEN_SETTINGS") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    val CHAPTERS_SORT_ASCENDING = object : Preference<TernaryState>("CHAPTERS_SORT_ASCENDING") {
        override var value by SharedPreference_Enum(
            name,
            preferences,
            TernaryState.Active
        ) { enumValueOf(it) }
    }
    val SOURCES_LANGUAGES_ISO639_1 = object : Preference<Set<String>>("SOURCES_LANGUAGES") {
        override var value by SharedPreference_StringSet(
            name,
            preferences,
            setOf(LanguageCode.ENGLISH.iso639_1)
        )
    }
    val EXTENSIONS_LANGUAGES_FILTER = object : Preference<Set<String>>("EXTENSIONS_LANGUAGES_FILTER") {
        override var value by SharedPreference_StringSet(
            name,
            preferences,
            setOf()
        )
    }
    val EXTENSIONS_REPOSITORY_URL = object : Preference<String>("EXTENSIONS_REPOSITORY_URL") {
        override var value by SharedPreference_String(
            name,
            preferences,
            "https://raw.githubusercontent.com/HnDK0/external-sources/refs/heads/main/index.yaml"
        )
    }
    val EXTENSIONS_AVAILABLE_CACHE = object : Preference<List<ExtensionInfoCached>>("EXTENSIONS_AVAILABLE_CACHE") {
        override var value by SharedPreference_Serializable<List<ExtensionInfoCached>>(
            name = name,
            sharedPreferences = preferences,
            defaultValue = listOf(),
            encode = { Json.encodeToString(it) },
            decode = { Json.decodeFromString(it) }
        )
    }
    val FINDER_SOURCES_PINNED = object : Preference<Set<String>>("FINDER_SOURCES_PINNED") {
        override var value by SharedPreference_StringSet(name, preferences, setOf())
    }
    val LIBRARY_FILTER_READ = object : Preference<TernaryState>("LIBRARY_FILTER_READ") {
        override var value by SharedPreference_Enum(
            name,
            preferences,
            TernaryState.Inactive
        ) { enumValueOf(it) }
    }
    val LIBRARY_SORT_CONFIG = object : Preference<SortConfig>("LIBRARY_SORT_CONFIG") {
        override var value by SharedPreference_Serializable(
            name,
            preferences,
            SortConfig.DEFAULT,
            encode = { Json.encodeToString(it) },
            decode = { Json.decodeFromString(it) }
        )
    }

    val BOOKS_LIST_LAYOUT_MODE = object : Preference<ListLayoutMode>("BOOKS_LIST_LAYOUT_MODE") {
        override var value by SharedPreference_Enum(
            name,
            preferences,
            ListLayoutMode.VerticalGrid
        ) { enumValueOf(it) }
    }
    // Количество колонок в сетке — общее для библиотеки и каталога плагинов (2..6, дефолт 3)
    val BOOKS_GRID_COLUMNS = object : Preference<Int>("BOOKS_GRID_COLUMNS") {
        override var value by SharedPreference_Int(name, preferences, 3)
    }
    val SOURCE_SORT_ORDER = object : Preference<SortOrder>("SOURCE_SORT_ORDER") {
        override var value by SharedPreference_Enum(
            name,
            preferences,
            SortOrder.ASCENDING
        ) { enumValueOf(it) }
    }
    val GLOBAL_TRANSLATION_ENABLED = object : Preference<Boolean>("GLOBAL_TRANSLATION_ENABLED") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }
    val GLOBAL_TRANSLATION_PREFERRED_SOURCE =
        object : Preference<String>("GLOBAL_TRANSLATIOR_PREFERRED_SOURCE") {
            override var value by SharedPreference_String(name, preferences, "en")
        }
    val GLOBAL_TRANSLATION_PREFERRED_TARGET =
        object : Preference<String>("GLOBAL_TRANSLATION_PREFERRED_TARGET") {
            override var value by SharedPreference_String(name, preferences, "")
        }

    val GLOBAL_APP_UPDATER_CHECKER_ENABLED =
        object : Preference<Boolean>("GLOBAL_APP_UPDATER_CHECKER_ENABLED") {
            override var value by SharedPreference_Boolean(name, preferences, true)
        }

    val GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED =
        object : Preference<Boolean>("GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED") {
            override var value by SharedPreference_Boolean(name, preferences, true)
        }

    val GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS =
        object : Preference<Int>("GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS") {
            override var value by SharedPreference_Int(name, preferences, 24)
        }

    val GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_LAST_TIMESTAMP =
        object : Preference<Long>("GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_LAST_TIMESTAMP") {
            override var value by SharedPreference_Serializable(
                name = name,
                sharedPreferences = preferences,
                defaultValue = 0L,
                encode = { it.toString() },
                decode = { it.toLongOrNull() ?: 0L }
            )
        }

    val TRANSLATION_GEMINI_API_KEY =
        object : Preference<String>("TRANSLATION_GEMINI_API_KEY") {
            override var value by SharedPreference_String(name, preferences, "")
        }

    val TRANSLATION_GEMINI_MODEL =
        object : Preference<String>("TRANSLATION_GEMINI_MODEL") {
            override var value by SharedPreference_String(name, preferences, "gemini-2.5-flash-lite")
        }

    val TRANSLATION_PREFER_ONLINE =
        object : Preference<Boolean>("TRANSLATION_PREFER_ONLINE") {
            override var value by SharedPreference_Boolean(name, preferences, false)
        }

    val TRANSLATION_PROVIDER =
        object : Preference<String>("TRANSLATION_PROVIDER") {
            override var value by SharedPreference_String(name, preferences, "GOOGLE_PA")
        }

    // Список Google PA API ключей (каждый на новой строке)
    // Первый ключ — захардкоженный фолбэк, остальные добавляются пользователем или с wtr-lab
    val TRANSLATION_GOOGLE_PA_API_KEYS =
        object : Preference<String>("TRANSLATION_GOOGLE_PA_API_KEYS") {
            override var value by SharedPreference_String(
                name, preferences,
                "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520"
            )
        }

    // Последний проверенный рабочий ключ
    val TRANSLATION_GOOGLE_PA_CACHED_KEY =
        object : Preference<String>("TRANSLATION_GOOGLE_PA_CACHED_KEY") {
            override var value by SharedPreference_String(name, preferences, "")
        }

    // Unix timestamp (мс) последней успешной проверки ключа
    val TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED =
        object : Preference<Long>("TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED") {
            override var value by SharedPreference_Serializable(
                name = name,
                sharedPreferences = preferences,
                defaultValue = 0L,
                encode = { it.toString() },
                decode = { it.toLongOrNull() ?: 0L }
            )
        }

    // ── OpenAI-compatible translation ─────────────────────────────────────────

    val TRANSLATION_OPENAI_BASE_URL =
        object : Preference<String>("TRANSLATION_OPENAI_BASE_URL") {
            override var value by SharedPreference_String(name, preferences, "https://api.openai.com")
        }

    // Список API-ключей, каждый на новой строке (поддерживается ротация)
    val TRANSLATION_OPENAI_API_KEYS =
        object : Preference<String>("TRANSLATION_OPENAI_API_KEYS") {
            override var value by SharedPreference_String(name, preferences, "")
        }

    val TRANSLATION_OPENAI_MODEL =
        object : Preference<String>("TRANSLATION_OPENAI_MODEL") {
            override var value by SharedPreference_String(name, preferences, "gpt-4o-mini")
        }

    // ── Unified prompt manager (shared by Gemini and OpenAI-compatible) ──────────

    // Текущий активный системный промпт. Пустая строка = использовать DEFAULT_TRANSLATION_PROMPT
    val TRANSLATION_ACTIVE_SYSTEM_PROMPT =
        object : Preference<String>("TRANSLATION_ACTIVE_SYSTEM_PROMPT") {
            override var value by SharedPreference_String(name, preferences, "")
        }

    // Пользовательские пресеты промптов: List<Pair<name, prompt>>.
    // Намеренно хранится как Pair — core-модуль не зависит от text_translator.
    // Конвертация в PromptPreset происходит в SettingsViewModel.
    val TRANSLATION_PROMPT_PRESETS =
        object : Preference<List<Pair<String, String>>>("TRANSLATION_PROMPT_PRESETS") {
            override var value by SharedPreference_Serializable<List<Pair<String, String>>>(
                name = name,
                sharedPreferences = preferences,
                defaultValue = listOf(),
                encode = { list ->
                    val arr = org.json.JSONArray()
                    list.forEach { (n, p) -> arr.put(org.json.JSONObject().put("n", n).put("p", p)) }
                    arr.toString()
                },
                decode = { raw ->
                    try {
                        val arr = org.json.JSONArray(raw)
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            obj.getString("n") to obj.getString("p")
                        }
                    } catch (_: Exception) { listOf() }
                }
            )
        }

    // Использовать английские названия языков в плейсхолдерах промпта.
    // true  → {source_language} = "Chinese", {target_language} = "Russian"
    // false → названия на языке интерфейса устройства
    val TRANSLATION_PROMPT_USE_ENGLISH_LOCALE =
        object : Preference<Boolean>("TRANSLATION_PROMPT_USE_ENGLISH_LOCALE") {
            override var value by SharedPreference_Boolean(name, preferences, true)
        }

    // Персональные промпты для новелл: Map<bookUrl, NovelPromptData>.
    // Сериализуется как JSON-объект { "bookUrl1": {"title":"...","prompt":"..."}, ... }.
    // Обратная совместимость: старый формат { "bookUrl1": "prompt1" } тоже читается.
    val TRANSLATION_NOVEL_PROMPTS =
        object : Preference<Map<String, NovelPromptData>>("TRANSLATION_NOVEL_PROMPTS") {
            override var value by SharedPreference_Serializable<Map<String, NovelPromptData>>(
                name = name,
                sharedPreferences = preferences,
                defaultValue = emptyMap(),
                encode = { map ->
                    val obj = org.json.JSONObject()
                    map.forEach { (url, data) ->
                        obj.put(url, org.json.JSONObject().apply {
                            put("title", data.title)
                            put("prompt", data.prompt)
                            put("appendMode", data.appendMode)
                        })
                    }
                    obj.toString()
                },
                decode = { raw ->
                    try {
                        val obj = org.json.JSONObject(raw)
                        val result = mutableMapOf<String, NovelPromptData>()
                        for (key in obj.keys()) {
                            val value = obj.get(key)
                            result[key] = when (value) {
                                is String -> NovelPromptData(prompt = value)
                                is org.json.JSONObject -> NovelPromptData(
                                    title = value.optString("title", ""),
                                    prompt = value.optString("prompt", ""),
                                    appendMode = value.optBoolean("appendMode", false),
                                )
                                else -> NovelPromptData(prompt = value.toString())
                            }
                        }
                        result
                    } catch (_: Exception) { emptyMap() }
                }
            )
        }

    // Количество параграфов в одном LLM-запросе (только Gemini и OpenAI).
    // Google PA и Free используют символьный лимит и не читают это значение.
    val TRANSLATION_PARALLEL_ENABLED = object : Preference<Boolean>("TRANSLATION_PARALLEL_ENABLED") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    val TRANSLATION_PARALLEL_ORDER = object : Preference<String>("TRANSLATION_PARALLEL_ORDER") {
        override var value by SharedPreference_String(name, preferences, "TRANSLATION_FIRST")
    }

    val TRANSLATION_BATCH_SIZE =
        object : Preference<Int>("TRANSLATION_BATCH_SIZE") {
            override var value by SharedPreference_Int(name, preferences, 60)
        }

    // Жёсткий лимит токенов в ответе LLM. 0 = не передавать поле, модель решает сама.
    val TRANSLATION_MAX_OUTPUT_TOKENS =
        object : Preference<Int>("TRANSLATION_MAX_OUTPUT_TOKENS") {
            override var value by SharedPreference_Int(name, preferences, 0)
        }

    val MASS_ADD_DELAY_MS = object : Preference<Long>("MASS_ADD_DELAY_MS") {
        override var value by SharedPreference_Serializable(
            name = name,
            sharedPreferences = preferences,
            defaultValue = 2000L,
            encode = { it.toString() },
            decode = { it.toLongOrNull() ?: 2000L }
        )
    }

    val DOWNLOAD_DELAY_MS = object : Preference<Long>("DOWNLOAD_DELAY_MS") {
        override var value by SharedPreference_Serializable(
            name = name,
            sharedPreferences = preferences,
            defaultValue = 2000L,
            encode = { it.toString() },
            decode = { it.toLongOrNull() ?: 2000L }
        )
    }

    val SCRAPER_USER_AGENT = object : Preference<String>("SCRAPER_USER_AGENT") {
        override var value by SharedPreference_String(name, preferences, "")
    }

    val CLOUDFLARE_BYPASS_ENABLED = object : Preference<Boolean>("CLOUDFLARE_BYPASS_ENABLED") {
        override var value by SharedPreference_Boolean(name, preferences, true)
    }

    val CLOUDFLARE_CHALLENGE_TIMEOUT_SECONDS = object : Preference<Int>("CLOUDFLARE_CHALLENGE_TIMEOUT_SECONDS") {
        override var value by SharedPreference_Int(name, preferences, 120)
    }

    val WTR_LAB_LANGUAGE = object : Preference<String>("WTR_LAB_LANGUAGE") {
        override var value by SharedPreference_String(name, preferences, "en")
    }

    val WTR_LAB_MODE = object : Preference<String>("WTR_LAB_MODE") {
        override var value by SharedPreference_String(name, preferences, "ai")
    }

    // ── Библиотека: сохранение состояния фильтров (чтобы не сбрасывалось после перезапуска) ──

    val LIBRARY_SELECTED_CATEGORIES = object : Preference<Set<String>>("LIBRARY_SELECTED_CATEGORIES") {
        override var value by SharedPreference_StringSet(name, preferences, setOf())
    }

    val LIBRARY_SELECTED_GENRES = object : Preference<Set<String>>("LIBRARY_SELECTED_GENRES") {
        override var value by SharedPreference_StringSet(name, preferences, setOf())
    }

    val LIBRARY_SELECTED_SOURCES = object : Preference<Set<String>>("LIBRARY_SELECTED_SOURCES") {
        override var value by SharedPreference_StringSet(name, preferences, setOf())
    }

    val LIBRARY_CUSTOM_CATEGORIES = object : Preference<List<String>>("LIBRARY_CUSTOM_CATEGORIES") {
        override var value by SharedPreference_Serializable<List<String>>(
            name = name,
            sharedPreferences = preferences,
            defaultValue = listOf(),
            encode = { Json.encodeToString(it) },
            decode = { Json.decodeFromString(it) }
        )
    }

    val USER_REGEX_CLEANUP_RULES = object : Preference<List<RegexRule>>(
        "USER_REGEX_CLEANUP_RULES"
    ) {
        override var value by SharedPreference_Serializable<List<RegexRule>>(
            name = name,
            sharedPreferences = preferences,
            defaultValue = listOf(),
            encode = { Json.encodeToString(it) },
            decode = { Json.decodeFromString(it) }
        )
    }

    // ── Auto Backup Preferences ─────────────────────────────────────────────

    // Включён ли автоматический бекап
    val BACKUP_AUTO_ENABLED = object : Preference<Boolean>("BACKUP_AUTO_ENABLED") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    // URI папки (tree URI) для автобекапов, выбранный через SAF
    val BACKUP_AUTO_DIRECTORY_URI = object : Preference<String>("BACKUP_AUTO_DIRECTORY_URI") {
        override var value by SharedPreference_String(name, preferences, "")
    }

    // Максимальное количество хранимых файлов автобекапа
    val BACKUP_AUTO_MAX_COUNT = object : Preference<Int>("BACKUP_AUTO_MAX_COUNT") {
        override var value by SharedPreference_Int(name, preferences, 5)
    }

    // Интервал между автобекапами в минутах (по умолчанию 1440 = 1 день)
    val BACKUP_AUTO_INTERVAL_MINUTES = object : Preference<Long>("BACKUP_AUTO_INTERVAL_MINUTES") {
        override var value by SharedPreference_Serializable(
            name = name,
            sharedPreferences = preferences,
            defaultValue = 1440L,
            encode = { it.toString() },
            decode = { it.toLongOrNull() ?: 1440L }
        )
    }

    // Включать ли изображения в автобекап
    val BACKUP_AUTO_INCLUDE_IMAGES = object : Preference<Boolean>("BACKUP_AUTO_INCLUDE_IMAGES") {
        override var value by SharedPreference_Boolean(name, preferences, false)
    }

    // Включать ли настройки в автобекап
    val BACKUP_AUTO_INCLUDE_SETTINGS = object : Preference<Boolean>("BACKUP_AUTO_INCLUDE_SETTINGS") {
        override var value by SharedPreference_Boolean(name, preferences, true)
    }

    // Включать ли плагины в автобекап
    val BACKUP_AUTO_INCLUDE_PLUGINS = object : Preference<Boolean>("BACKUP_AUTO_INCLUDE_PLUGINS") {
        override var value by SharedPreference_Boolean(name, preferences, true)
    }

    // Unix timestamp (мс) последнего успешного автобекапа
    val BACKUP_AUTO_LAST_TIMESTAMP = object : Preference<Long>("BACKUP_AUTO_LAST_TIMESTAMP") {
        override var value by SharedPreference_Serializable(
            name = name,
            sharedPreferences = preferences,
            defaultValue = 0L,
            encode = { it.toString() },
            decode = { it.toLongOrNull() ?: 0L }
        )
    }


    abstract inner class Preference<T>(val name: String) {
        abstract var value: T
        fun flow() = toFlow(name) { value }.flowOn(Dispatchers.IO)
        fun state(scope: CoroutineScope) = toState(
            scope = scope, key = name, mapper = { value }, setter = { value = it }
        )
    }

    private fun <T> toFlow(key: String, mapper: (String) -> T): Flow<T> {
        val flow = MutableStateFlow(mapper(key))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, vkey ->
            if (key == vkey)
                flow.value = mapper(vkey)
        }

        return flow
            .onSubscription {
                preferencesChangeListeners.add(listener)
                preferences.registerOnSharedPreferenceChangeListener(listener)
            }.onCompletion {
                preferencesChangeListeners.remove(listener)
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }.flowOn(Dispatchers.Default)
    }

    private fun <T> toState(
        scope: CoroutineScope,
        key: String,
        mapper: (String) -> T,
        setter: (T) -> Unit
    ): MutableState<T> = object : MutableState<T> {

        private val internalValue = mutableStateOf(mapper(key))
        override var value: T
            get() = internalValue.value
            set(newValue) {
                if (internalValue.value != newValue) {
                    internalValue.value = newValue
                    setter(newValue)
                }
            }

        init {
            scope.launch(Dispatchers.IO) {
                toFlow(key, mapper).collect {
                    withContext(Dispatchers.Main) {
                        internalValue.value = it
                    }
                }
            }
        }

        override fun component1(): T = value
        override fun component2() = ::value::set
    }
}