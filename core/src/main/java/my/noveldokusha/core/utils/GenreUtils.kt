package my.noveldokusha.core.utils

/**
 * Нормализует список сырых жанров в строку с разделителем ",".
 * - Удаляет дубли (case-insensitive)
 * - Удаляет мусорные символы (оставляет буквы, цифры, пробелы, дефис, амперсанд)
 * - Удаляет пустые/null значения
 * - Сортирует по алфавиту
 */
object GenreUtils {

    private val ALLOWED_CHARS_REGEX = Regex("[^a-zA-Zа-яА-Я0-9 \\-&]")
    private const val SEPARATOR = ","

    fun normalize(rawGenres: List<String>): String {
        if (rawGenres.isEmpty()) return ""

        return rawGenres
            .asSequence()
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .map { it.replace(ALLOWED_CHARS_REGEX, "") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
            .joinToString(SEPARATOR)
    }

    /**
     * Парсит строку жанров обратно в список.
     */
    fun parse(genres: String): List<String> {
        if (genres.isBlank()) return emptyList()
        return genres.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Проверяет, содержит ли строка жанров указанный жанр.
     */
    fun contains(genres: String, genre: String): Boolean {
        if (genres.isBlank() || genre.isBlank()) return false
        val target = genre.trim().lowercase()
        return genres.split(SEPARATOR).any { it.trim().lowercase() == target }
    }
}