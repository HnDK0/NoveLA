package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity

/**
 * Хранит перевод метаданных книги (заголовок и описание) для пары языков
 * как одну строку. Пустая строка в titleTranslation/descriptionTranslation
 * означает «перевод отсутствует» — такие строки не возвращаются в flow.
 * Составной PK (bookUrl, sourceLang, targetLang) гарантирует отсутствие
 * дублей при insertReplace.
 */
@Entity(
    primaryKeys = ["bookUrl", "sourceLang", "targetLang"]
)
data class BookTranslation(
    val bookUrl: String,
    val sourceLang: String,
    val targetLang: String,
    val titleTranslation: String = "",
    val descriptionTranslation: String = ""
)