package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.BookTranslation

/**
 * Проекция перевода метаданных книги. sourceLang включён намеренно:
 * по нему нижележащие слои применяют правило выбора предпочтительной
 * пары языков и проверяют существование строки для исходного языка.
 */
data class BookTitleTranslation(
    val bookUrl: String,
    val sourceLang: String,
    val titleTranslation: String,
    val descriptionTranslation: String
)

@Dao
interface BookTranslationDao {

    @Query("""
        SELECT bookUrl, sourceLang, titleTranslation, descriptionTranslation
        FROM BookTranslation
        WHERE bookUrl = :bookUrl
        AND targetLang = :targetLang
        AND (titleTranslation != '' OR descriptionTranslation != '')
    """)
    fun getTranslatedBookFlow(
        bookUrl: String,
        targetLang: String
    ): Flow<List<BookTitleTranslation>>

    @Query("""
        SELECT * FROM BookTranslation
        WHERE bookUrl = :bookUrl
        AND sourceLang = :sourceLang
        AND targetLang = :targetLang
    """)
    suspend fun get(
        bookUrl: String,
        sourceLang: String,
        targetLang: String
    ): BookTranslation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(bookTranslation: BookTranslation)

    @Query("DELETE FROM BookTranslation WHERE bookUrl IN (:bookUrls)")
    suspend fun deleteByBookUrls(bookUrls: List<String>)

    @Query("""
        DELETE FROM BookTranslation
        WHERE sourceLang = :sourceLang
        AND targetLang = :targetLang
    """)
    suspend fun deleteByLanguagePair(sourceLang: String, targetLang: String)

    @Query("DELETE FROM BookTranslation")
    suspend fun deleteAll()
}