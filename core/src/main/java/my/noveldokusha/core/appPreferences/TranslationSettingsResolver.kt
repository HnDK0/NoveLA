package my.noveldokusha.core.appPreferences

import kotlinx.coroutines.flow.Flow

/**
 * Единственная точка чтения эффективных per-book настроек перевода.
 *
 * Каскад разрешения (per-book > per-plugin > global) реализован в [AppPreferences];
 * этот интерфейс лишь подставляет идентификатор источника книги в каскад.
 *
 * Идентичность источника: [sourceId] вычисляется через `getCompatibleSource(bookUrl)`,
 * который ищет по списку ЗАГРУЖЕННЫХ источников (`loadedSourcesList`). Поэтому
 * plugin-уровень применяется только к книгам, чей источник сейчас загружен/установлен —
 * ровно к тому множеству, которое конвейер скачивания может получить и перевести
 * (он сам ходит через `getCompatibleSourceCatalog`). Это намеренно отличается от
 * `getSourceId` (сырой `sourcesList`, включая CachedSource-заглушки) — здесь не
 * используется. Идентичность вычисляется на каждый вызов (без кэширования), что
 * делает резолвер безопасным к перезагрузке источников в рантайме.
 */
interface TranslationSettingsResolver {

    /** Резолвленные effective настройки перевода для книги. */
    data class TranslationSettings(
        val enabled: Boolean,
        val source: String,
        val target: String,
        val scope: String,
        val provider: String?,
    )

    fun translationEnabledForBook(bookUrl: String): Boolean
    fun translationPairForBook(bookUrl: String): TranslationLangPair
    fun translationTargetForBook(bookUrl: String): String
    fun translationScopeForBook(bookUrl: String): String
    fun translationProviderForBook(bookUrl: String): String?

    /**
     * Реактивный Flow эффективных настроек перевода для книги.
     *
     * Подписывается на ВСЕ 9 преф-флоёв (per-book, per-plugin, global) через combine.
     * При изменении ЛЮБОГО из них — перерезолвит настройки и эмитит свежий [TranslationSettings].
     * Используется ViewModel для реактивного обновления UI без ручных подписок на отдельные флои.
     */
    fun settingsChangeSignal(bookUrl: String): Flow<TranslationSettings>

    /**
     * Сигнал изменения ЛЮБОГО из 9 настроек перевода (per-book, per-plugin, global).
     *
     * Эмитит [Unit] при изменении любого преф-флоа. Используется когда нужен триггер
     * пересчёта (например, в библиотеке где `flatMapLatest` по `baseLibraryFlow`
     * не перезапускается при смене настроек перевода).
     */
    fun translationSettingsChangeSignal(): Flow<Unit>

    /**
     * Уровень, который реально обеспечивает перевод книги,
     * по приоритету пары (пер-новел > плагин > глобал,
     * см. [AppPreferences.resolveTranslationPair]).
     */
    enum class ActiveTranslatorLevel { PER_NOVEL, PLUGIN, GLOBAL, NONE }

    fun activeTranslatorLevelForBook(bookUrl: String): ActiveTranslatorLevel

    /**
     * Имя источника плагина для книги — только когда уровень = [ActiveTranslatorLevel.PLUGIN].
     * Иначе null. Используется UI для отображения имени плагина в полоске-статусе.
     */
    fun pluginDisplayNameForBook(bookUrl: String): String?
}
