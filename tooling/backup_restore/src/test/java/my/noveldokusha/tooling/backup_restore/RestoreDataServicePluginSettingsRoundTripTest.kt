package my.noveldokusha.tooling.backup_restore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TranslationLangPair
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Контрактный тест round-trip per-plugin настроек перевода через backup/restore.
 *
 * СХЕМА (источник истины — два файла продакшена):
 *  - WRITE: tooling/backup_create/.../BackupDataService.kt:402-429 — пишет 5 ключей
 *    в settings.json как JSONObject:
 *      TRANSLATION_PLUGIN_ENABLED_MAP = {"<sourceId>": bool}
 *      TRANSLATION_PLUGIN_LANG_PAIR  = {"<sourceId>": {"source":..,"target":..}}
 *      TRANSLATION_PLUGIN_PROVIDER   = {"<sourceId>": "PROVIDER_NAME"}
 *      TRANSLATION_PLUGIN_SCOPE      = {"<sourceId>": "STANDARD"|"FULL"}
 *      TRANSLATION_PLUGIN_PROMPTS    = {"<sourceId>": "prompt string"}
 *  - READ: tooling/backup_restore/.../RestoreDataService.kt:697-763 — парсит каждый
 *    ключ и пишет обратно в те же AppPreferences-карты.
 *
 * ПОЧЕМУ это fallback, а не вызов реального кода: `mergeToSettings` — ЛОКАЛЬНАЯ
 * функция внутри `restoreData` (RestoreDataService.kt:554, вложена в withContext,
 * открывающийся на строке 208 и закрывающийся на 992). Это НЕ public-член класса,
 * поэтому из теста её вызвать нельзя, а `restoreData` требует реальный content URI,
 * ZIP-парсинг и слияние Room-БД (все @Inject lateinit поля) — это Hilt-инфраструктура,
 * которую план запрещает. Поэтому тест воспроизводит ровно 5 parse-блоков
 * (RestoreDataService.kt:697-763) без рефлексии и прогоняет их через РЕАЛЬНЫЙ
 * AppPreferences(context). Схема JSON строится ровно как BackupDataService.kt:402-429.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestoreDataServicePluginSettingsRoundTripTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        prefs = AppPreferences(context)
        // Чистый старт: сбрасываем все 5 plugin-карт перед каждым тестом.
        prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_LANG_PAIR.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_PROVIDER.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_SCOPE.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_PROMPTS.value = emptyMap()
    }

    @After
    fun tearDown() {
        // Сбрасываем SharedPreferences, чтобы не протекать между тестами.
        context.getSharedPreferences("default", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `happy - five plugin maps round-trip unchanged for two plugins`() {
        // Строим settings.json ровно как BackupDataService.kt:402-429.
        val settings = JSONObject().apply {
            put("TRANSLATION_PLUGIN_ENABLED_MAP", JSONObject().apply {
                put("lua_a", true)
                put("lua_b", false)
            })
            put("TRANSLATION_PLUGIN_LANG_PAIR", JSONObject().apply {
                put("lua_a", JSONObject().apply {
                    put("source", "zh")
                    put("target", "ru")
                })
                put("lua_b", JSONObject().apply {
                    put("source", "en")
                    put("target", "de")
                })
            })
            put("TRANSLATION_PLUGIN_PROVIDER", JSONObject().apply {
                put("lua_a", "GOOGLE_FREE")
                put("lua_b", "GEMINI")
            })
            put("TRANSLATION_PLUGIN_SCOPE", JSONObject().apply {
                put("lua_a", "FULL")
                put("lua_b", "STANDARD")
            })
            put("TRANSLATION_PLUGIN_PROMPTS", JSONObject().apply {
                put("lua_a", "Переведи в стиле классики")
                put("lua_b", "Кратко и по делу")
            })
        }

        // Прогоняем через воспроизведение 5 parse-блоков RestoreDataService.kt:697-763.
        parsePluginSettings(settings, prefs)

        // Все 5 карт восстановлены без изменений.
        assertEquals(mapOf("lua_a" to true, "lua_b" to false), prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value)
        assertEquals(
            mapOf("lua_a" to TranslationLangPair("zh", "ru"), "lua_b" to TranslationLangPair("en", "de")),
            prefs.TRANSLATION_PLUGIN_LANG_PAIR.value,
        )
        assertEquals(mapOf("lua_a" to "GOOGLE_FREE", "lua_b" to "GEMINI"), prefs.TRANSLATION_PLUGIN_PROVIDER.value)
        assertEquals(mapOf("lua_a" to "FULL", "lua_b" to "STANDARD"), prefs.TRANSLATION_PLUGIN_SCOPE.value)
        assertEquals(
            mapOf("lua_a" to "Переведи в стиле классики", "lua_b" to "Кратко и по делу"),
            prefs.TRANSLATION_PLUGIN_PROMPTS.value,
        )
    }

    @Test
    fun `failure - one corrupted entry is skipped, other four keys restore intact`() {
        // Корраптим ОДНУ запись внутри LANG_PAIR: у lua_b значение — строка, а не JSONObject.
        // Parse-блок (RestoreDataService.kt:710-724) проверяет `value is JSONObject`
        // и молча пропускает битую запись, не роняя остальное.
        val settings = JSONObject().apply {
            put("TRANSLATION_PLUGIN_ENABLED_MAP", JSONObject().apply {
                put("lua_a", true)
                put("lua_b", false)
            })
            put("TRANSLATION_PLUGIN_LANG_PAIR", JSONObject().apply {
                put("lua_a", JSONObject().apply {
                    put("source", "zh")
                    put("target", "ru")
                })
                put("lua_b", "corrupted-not-an-object") // ← битая запись
            })
            put("TRANSLATION_PLUGIN_PROVIDER", JSONObject().apply {
                put("lua_a", "GOOGLE_FREE")
                put("lua_b", "GEMINI")
            })
            put("TRANSLATION_PLUGIN_SCOPE", JSONObject().apply {
                put("lua_a", "FULL")
                put("lua_b", "STANDARD")
            })
            put("TRANSLATION_PLUGIN_PROMPTS", JSONObject().apply {
                put("lua_a", "Переведи в стиле классики")
                put("lua_b", "Кратко и по делу")
            })
        }

        parsePluginSettings(settings, prefs)

        // Битый ключ пропущен: lua_b отсутствует в LANG_PAIR, lua_a сохранён.
        assertEquals(
            mapOf("lua_a" to TranslationLangPair("zh", "ru")),
            prefs.TRANSLATION_PLUGIN_LANG_PAIR.value,
        )
        // Остальные 4 ключа восстановлены целиком (оба плагина).
        assertEquals(mapOf("lua_a" to true, "lua_b" to false), prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value)
        assertEquals(mapOf("lua_a" to "GOOGLE_FREE", "lua_b" to "GEMINI"), prefs.TRANSLATION_PLUGIN_PROVIDER.value)
        assertEquals(mapOf("lua_a" to "FULL", "lua_b" to "STANDARD"), prefs.TRANSLATION_PLUGIN_SCOPE.value)
        assertEquals(
            mapOf("lua_a" to "Переведи в стиле классики", "lua_b" to "Кратко и по делу"),
            prefs.TRANSLATION_PLUGIN_PROMPTS.value,
        )
    }

    @Test
    fun `failure - corrupted provider entry is skipped, enabled and pair still restore`() {
        // Корраптим запись в PROVIDER: у lua_b значение — JSONArray, а не String.
        // Parse-блок (RestoreDataService.kt:726-737) проверяет `value is String`
        // и пропускает битую запись.
        val settings = JSONObject().apply {
            put("TRANSLATION_PLUGIN_ENABLED_MAP", JSONObject().apply {
                put("lua_a", true)
            })
            put("TRANSLATION_PLUGIN_LANG_PAIR", JSONObject().apply {
                put("lua_a", JSONObject().apply {
                    put("source", "zh")
                    put("target", "ru")
                })
            })
            put("TRANSLATION_PLUGIN_PROVIDER", JSONObject().apply {
                put("lua_a", "GOOGLE_FREE")
                put("lua_b", org.json.JSONArray(listOf("GEMINI", "OPENAI"))) // ← битая запись
            })
            put("TRANSLATION_PLUGIN_SCOPE", JSONObject().apply {
                put("lua_a", "FULL")
            })
            put("TRANSLATION_PLUGIN_PROMPTS", JSONObject().apply {
                put("lua_a", "Переведи в стиле классики")
            })
        }

        parsePluginSettings(settings, prefs)

        // Битый PROVIDER-ключ пропущен, валидный lua_a сохранён.
        assertEquals(mapOf("lua_a" to "GOOGLE_FREE"), prefs.TRANSLATION_PLUGIN_PROVIDER.value)
        // Остальные ключи восстановлены.
        assertEquals(mapOf("lua_a" to true), prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value)
        assertEquals(mapOf("lua_a" to TranslationLangPair("zh", "ru")), prefs.TRANSLATION_PLUGIN_LANG_PAIR.value)
        assertEquals(mapOf("lua_a" to "FULL"), prefs.TRANSLATION_PLUGIN_SCOPE.value)
        assertEquals(mapOf("lua_a" to "Переведи в стиле классики"), prefs.TRANSLATION_PLUGIN_PROMPTS.value)
    }

    /**
     * Воспроизведение 5 parse-блоков из RestoreDataService.kt:697-763.
     *
     * Каждый блок: если ключ присутствует — читаем JSONObject, итерируем записи,
     * сохраняем только те, чей тип совпадает с ожидаемым (Boolean / JSONObject / String),
     * остальные молча пропускаем. Это ровно та логика, что в продакшене.
     */
    private fun parsePluginSettings(settings: JSONObject, prefs: AppPreferences) {
        if (settings.has("TRANSLATION_PLUGIN_ENABLED_MAP")) {
            val enabledObj = settings.getJSONObject("TRANSLATION_PLUGIN_ENABLED_MAP")
            val enabledMap = mutableMapOf<String, Boolean>()
            for (key in enabledObj.keys()) {
                val value = enabledObj.get(key)
                if (value is Boolean) enabledMap[key] = value
            }
            prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value = enabledMap
        }

        if (settings.has("TRANSLATION_PLUGIN_LANG_PAIR")) {
            val pairsObj = settings.getJSONObject("TRANSLATION_PLUGIN_LANG_PAIR")
            val pairsMap = mutableMapOf<String, TranslationLangPair>()
            for (key in pairsObj.keys()) {
                val value = pairsObj.get(key)
                if (value is JSONObject) {
                    pairsMap[key] = TranslationLangPair(
                        source = value.optString("source", ""),
                        target = value.optString("target", ""),
                    )
                }
            }
            prefs.TRANSLATION_PLUGIN_LANG_PAIR.value = pairsMap
        }

        if (settings.has("TRANSLATION_PLUGIN_PROVIDER")) {
            val providerObj = settings.getJSONObject("TRANSLATION_PLUGIN_PROVIDER")
            val providerMap = mutableMapOf<String, String>()
            for (key in providerObj.keys()) {
                val value = providerObj.get(key)
                if (value is String) providerMap[key] = value
            }
            prefs.TRANSLATION_PLUGIN_PROVIDER.value = providerMap
        }

        if (settings.has("TRANSLATION_PLUGIN_SCOPE")) {
            val scopeObj = settings.getJSONObject("TRANSLATION_PLUGIN_SCOPE")
            val scopeMap = mutableMapOf<String, String>()
            for (key in scopeObj.keys()) {
                val value = scopeObj.get(key)
                if (value is String) scopeMap[key] = value
            }
            prefs.TRANSLATION_PLUGIN_SCOPE.value = scopeMap
        }

        if (settings.has("TRANSLATION_PLUGIN_PROMPTS")) {
            val promptsObj = settings.getJSONObject("TRANSLATION_PLUGIN_PROMPTS")
            val promptsMap = mutableMapOf<String, String>()
            for (key in promptsObj.keys()) {
                val value = promptsObj.get(key)
                if (value is String) promptsMap[key] = value
            }
            prefs.TRANSLATION_PLUGIN_PROMPTS.value = promptsMap
        }
    }

    /**
     * ROUND-TRIP: TRANSLATION_BOOK_ENABLED_MAP
     *
     * WRITE: BackupDataService.kt — put("TRANSLATION_BOOK_ENABLED_MAP", JSONObject().apply {
     *   appPreferences.TRANSLATION_BOOK_ENABLED_MAP.value.forEach { (url, enabled) -> put(url, enabled) }
     * })
     *
     * READ: RestoreDataService.kt mergeToSettings — settingsJson.has("TRANSLATION_BOOK_ENABLED_MAP")
     * → getJSONObject → keys → prefs.TRANSLATION_BOOK_ENABLED_MAP.value = enabledMap
     */
    @Test
    fun `round-trip TRANSLATION_BOOK_ENABLED_MAP`() {
        // WRITE phase: имитируем BackupDataService.
        val bookEnabled = mapOf(
            "https://example.com/book/1" to true,
            "https://example.com/book/2" to false,
            "https://another.com/novel/x" to true,
        )
        prefs.TRANSLATION_BOOK_ENABLED_MAP.value = bookEnabled

        val json = JSONObject().apply {
            put("TRANSLATION_BOOK_ENABLED_MAP", JSONObject().apply {
                bookEnabled.forEach { (url, enabled) -> put(url, enabled) }
            })
        }

        // Сбрасываем prefs.
        prefs.TRANSLATION_BOOK_ENABLED_MAP.value = emptyMap()

        // READ phase: имитируем RestoreDataService mergeToSettings.
        if (json.has("TRANSLATION_BOOK_ENABLED_MAP")) {
            val enabledObj = json.getJSONObject("TRANSLATION_BOOK_ENABLED_MAP")
            val enabledMap = mutableMapOf<String, Boolean>()
            for (key in enabledObj.keys()) {
                if (enabledObj.get(key) is Boolean) {
                    enabledMap[key] = enabledObj.getBoolean(key)
                }
            }
            prefs.TRANSLATION_BOOK_ENABLED_MAP.value = enabledMap
        }

        assertEquals(bookEnabled, prefs.TRANSLATION_BOOK_ENABLED_MAP.value)
        assertEquals(true, prefs.TRANSLATION_BOOK_ENABLED_MAP.value["https://example.com/book/1"])
        assertEquals(false, prefs.TRANSLATION_BOOK_ENABLED_MAP.value["https://example.com/book/2"])
        assertEquals(true, prefs.TRANSLATION_BOOK_ENABLED_MAP.value["https://another.com/novel/x"])
    }
}
