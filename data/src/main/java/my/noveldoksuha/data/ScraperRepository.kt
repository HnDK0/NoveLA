package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.scraper.DatabaseInterface
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import javax.inject.Inject
import javax.inject.Singleton

data class LanguageItem(val language: LanguageCode, val active: Boolean)
data class CatalogItem(val catalog: SourceInterface.Catalog, val pinned: Boolean)

@Singleton
class ScraperRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    val scraper: Scraper,
) {
    fun databaseList(): List<DatabaseInterface> = scraper.databasesList.toList()

    fun sourcesCatalogListFlow(): Flow<List<CatalogItem>> =
        combine(
            scraper.sourcesCatalogListFlow,
            appPreferences.FINDER_SOURCES_PINNED.flow()
        ) { catalogs, pinnedIds ->
            catalogs
                .map { CatalogItem(catalog = it, pinned = it.id in pinnedIds) }
                .sortedByDescending { it.pinned }
        }.flowOn(Dispatchers.Default)
            .distinctUntilChanged()

    fun sourcesLanguagesListFlow(): Flow<List<LanguageItem>> =
        combine(
            scraper.sourcesLanguagesListFlow,
            appPreferences.SOURCES_LANGUAGES_ISO639_1.flow()
        ) { languages, activeLanguages ->
            languages.map { LanguageItem(it, active = it.iso639_1 in activeLanguages) }
        }.flowOn(Dispatchers.Default)
}