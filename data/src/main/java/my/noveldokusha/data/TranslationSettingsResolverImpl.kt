package my.noveldokusha.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver.ActiveTranslatorLevel
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver.TranslationSettings
import my.noveldokusha.scraper.Scraper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [TranslationSettingsResolver] поверх каскада [AppPreferences].
 *
 * Идентичность источника книги вычисляется через `scraper.getCompatibleSource(bookUrl)?.id`
 * на каждый вызов (без кэширования). `getCompatibleSource` ищет по списку ЗАГРУЖЕННЫХ
 * источников (`loadedSourcesList`), поэтому plugin-уровень применяется только к книгам,
 * чей источник сейчас загружен/установлен — ровно к тому множеству, которое конвейер
 * скачивания может получить и перевести (он сам ходит через `getCompatibleSourceCatalog`).
 * Это намеренно отличается от `getSourceId` (сырой `sourcesList`, включая CachedSource-
 * заглушки) — здесь он не используется. Без кэширования резолвер безопасен к перезагрузке
 * источников в рантайме.
 *
 * Когда источник не загружен (локальная книга или несовпадающий префикс), `sourceId = null`
 * и каскад сводится ровно к прежнему поведению per-book/global — plugin-уровень пропускается.
 */
@Singleton
class TranslationSettingsResolverImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val scraper: Scraper,
) : TranslationSettingsResolver {

    // Идентичность источника — только загруженные источники (см. KDoc класса).
    private fun sourceIdFor(bookUrl: String): String? =
        scraper.getCompatibleSource(bookUrl)?.id

    override fun translationEnabledForBook(bookUrl: String): Boolean {
        val sourceId = sourceIdFor(bookUrl)
        val pluginContained = sourceId != null && appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.value.containsKey(sourceId)
        val pluginValue = sourceId?.let { appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.value[it] }
        val bookContained = appPreferences.TRANSLATION_BOOK_ENABLED_MAP.value.containsKey(bookUrl)
        val result = appPreferences.translationEnabledForBook(
            bookUrl = bookUrl,
            sourceId = sourceId,
        )
        Timber.d(
            "resolverEnabled: book=%s sourceId=%s globalMode=%s globalEnabled=%s " +
                "bookContained=%s pluginContained=%s pluginValue=%s => enabled=%s",
            bookUrl.takeLast(40), sourceId,
            appPreferences.TRANSLATION_GLOBAL_MODE.value,
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value,
            bookContained, pluginContained, pluginValue, result,
        )
        return result
    }

    override fun translationPairForBook(bookUrl: String): TranslationLangPair {
        val sourceId = sourceIdFor(bookUrl)
        val bookPair = appPreferences.TRANSLATION_BOOK_LANG_PAIR.value[bookUrl]
        val pluginPair = sourceId?.let { appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.value[it] }
        val globalSource = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
        val globalTarget = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
        val result = appPreferences.translationPairForBook(
            bookUrl = bookUrl,
            sourceId = sourceId,
        )
        Timber.d(
            "resolverPair: book=%s sourceId=%s bookPair=%s pluginPair=%s " +
                "global=(%s,%s) globalMode=%s => pair=(%s,%s)",
            bookUrl.takeLast(40), sourceId, bookPair, pluginPair,
            globalSource, globalTarget,
            appPreferences.TRANSLATION_GLOBAL_MODE.value,
            result.source, result.target,
        )
        return result
    }

    override fun translationTargetForBook(bookUrl: String): String =
        translationPairForBook(bookUrl).target

    override fun translationScopeForBook(bookUrl: String): String {
        val sourceId = sourceIdFor(bookUrl)
        val pluginScope = sourceId?.let { appPreferences.TRANSLATION_PLUGIN_SCOPE.value[it] }
        val result = appPreferences.translationScopeForBook(
            bookUrl = bookUrl,
            sourceId = sourceId,
        )
        Timber.d(
            "resolverScope: book=%s sourceId=%s pluginScope=%s => scope=%s",
            bookUrl.takeLast(40), sourceId, pluginScope, result,
        )
        return result
    }

    override fun translationProviderForBook(bookUrl: String): String? {
        val sourceId = sourceIdFor(bookUrl)
        val pluginProvider = sourceId?.let { appPreferences.TRANSLATION_PLUGIN_PROVIDER.value[it] }
        val result = appPreferences.translationProviderForBook(
            bookUrl = bookUrl,
            sourceId = sourceId,
        )
        Timber.d(
            "resolverProvider: book=%s sourceId=%s pluginProvider=%s => provider=%s",
            bookUrl.takeLast(40), sourceId, pluginProvider, result,
        )
        return result
    }

    // Активный переводчик книги — первый по приоритету (пер-новел > плагин > глобал)
    // уровень, который реально переводит: его флаг enable включён И пара полная.
    // Уровень определяется НЕ по наличию сохранённой пары (карты enable и pair
    // независимы), а по состоянию перевода — иначе полоска «Активный переводчик XX»
    // врала бы, показывая пер-новел при выключенном пер-новел и включённом глобале.
    override fun activeTranslatorLevelForBook(bookUrl: String): ActiveTranslatorLevel {
        val sourceId = sourceIdFor(bookUrl)

        // Пер-новел: включён И пара книги полная (source/target непустые).
        val bookPair = appPreferences.TRANSLATION_BOOK_LANG_PAIR.value[bookUrl]
        if (appPreferences.TRANSLATION_BOOK_ENABLED_MAP.value[bookUrl] == true &&
            bookPair != null && bookPair.source.isNotBlank() && bookPair.target.isNotBlank()
        ) return ActiveTranslatorLevel.PER_NOVEL

        // Плагин: включён И пара плагина полная (только для загруженного источника).
        if (sourceId != null && appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.value[sourceId] == true) {
            val pluginPair = appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.value[sourceId]
            if (pluginPair != null && pluginPair.source.isNotBlank() && pluginPair.target.isNotBlank())
                return ActiveTranslatorLevel.PLUGIN
        }

        // Глобал: активен глобальный режим, глобальный перевод включён И пара полная.
        if (appPreferences.TRANSLATION_GLOBAL_MODE.value &&
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value
        ) {
            val globalSource = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
            val globalTarget = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
            if (globalSource.isNotBlank() && globalTarget.isNotBlank())
                return ActiveTranslatorLevel.GLOBAL
        }

        return ActiveTranslatorLevel.NONE
    }

    override fun pluginDisplayNameForBook(bookUrl: String): String? =
        scraper.getCompatibleSource(bookUrl)?.name?.takeIf { it.isNotBlank() }

    /**
     * Реактивный Flow: combine на все 9 преф-флоёв (per-book + per-plugin + global).
     *
     * При изменении ЛЮБОГО из них — перерезолвит настройки через существующие
     * translationEnabledForBook / translationPairForBook / translationScopeForBook /
     * translationProviderForBook. Используется ViewModel для реактивного обновления UI.
     *
     * ponytail: 9 флоёв через nested combine (coroutines combine max 5 typed params),
     * лямбда не использует значения — читает .value напрямую из преференций.
     */
    override fun settingsChangeSignal(bookUrl: String): Flow<TranslationSettings> =
        combine(
            combine(
                appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_BOOK_LANG_PAIR.flow(),
                appPreferences.GLOBAL_TRANSLATION_ENABLED.flow(),
                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow(),
                appPreferences.TRANSLATION_GLOBAL_MODE.flow(),
            ) { _, _, _, _, _ -> },
            combine(
                appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.flow(),
                appPreferences.TRANSLATION_PLUGIN_SCOPE.flow(),
                appPreferences.TRANSLATION_PLUGIN_PROVIDER.flow(),
            ) { _, _, _, _ -> }
        ) { _, _ ->
            val pair = translationPairForBook(bookUrl)
            TranslationSettings(
                enabled = translationEnabledForBook(bookUrl),
                source = pair.source,
                target = pair.target,
                scope = translationScopeForBook(bookUrl),
                provider = translationProviderForBook(bookUrl),
            )
        }.distinctUntilChanged()

    /**
     * Сигнал: эмитит [Unit] при изменении любого из 9 преф-флоёв.
     * Используется для триггера пересчёта в тех ViewModel, где нет привязки
     * к конкретной книге (например, библиотека).
     */
    override fun translationSettingsChangeSignal(): Flow<Unit> =
        combine(
            combine(
                appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_BOOK_LANG_PAIR.flow(),
                appPreferences.GLOBAL_TRANSLATION_ENABLED.flow(),
                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow(),
                appPreferences.TRANSLATION_GLOBAL_MODE.flow(),
            ) { _, _, _, _, _ -> },
            combine(
                appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.flow(),
                appPreferences.TRANSLATION_PLUGIN_SCOPE.flow(),
                appPreferences.TRANSLATION_PLUGIN_PROVIDER.flow(),
            ) { _, _, _, _ -> }
        ) { _, _ -> }
}
