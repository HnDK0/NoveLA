package my.noveldokusha.scraper.configs

/**
 * Главный индекс репозитория источников
 */
data class RepositoryIndex(
    val version: String,
    val lastUpdated: String,
    val languages: Map<String, LanguageInfo>,
    val totalSources: Int
)

/**
 * Информация о языке в репозитории
 */
data class LanguageInfo(
    val name: String,
    val count: Int? = null,
    val url: String
)

/**
 * Индекс источников для конкретного языка
 */
data class LanguageIndex(
    val language: String,
    val name: String,
    val sources: List<SourceMetadata>
)

/**
 * Метаданные источника
 */
data class SourceMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val url: String,
    val icon: String,
    val language: String,
    val charset: String? = null
)
