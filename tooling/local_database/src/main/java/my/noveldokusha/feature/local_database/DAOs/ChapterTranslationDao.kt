package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.ChapterTranslation

data class ChapterTitleTranslation(
    val chapterUrl: String,
    val translatedText: String
)

/** Пара языков перевода и число глав с непустым переводом (для диалога экспорта). */
data class TranslationGroup(
    val sourceLang: String,
    val targetLang: String,
    val count: Int
)

@Dao
interface ChapterTranslationDao {

    @Query("""
        SELECT * FROM ChapterTranslation 
        WHERE chapterUrl = :chapterUrl 
        AND sourceLang = :sourceLang 
        AND targetLang = :targetLang
    """)
    suspend fun getTranslations(
        chapterUrl: String,
        sourceLang: String,
        targetLang: String
    ): ChapterTranslation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(translation: ChapterTranslation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(translations: List<ChapterTranslation>)

    @Query("""
        DELETE FROM ChapterTranslation 
        WHERE chapterUrl NOT IN (SELECT url FROM Chapter)
    """)
    suspend fun removeOrphanedTranslations()

    @Query("DELETE FROM ChapterTranslation WHERE chapterUrl = :chapterUrl")
    suspend fun deleteChapterTranslations(chapterUrl: String)

    @Query("DELETE FROM ChapterTranslation WHERE chapterUrl IN (:chapterUrls)")
    suspend fun deleteChapterTranslationsByUrls(chapterUrls: List<String>)

    @Query("""
        DELETE FROM ChapterTranslation 
        WHERE chapterUrl IN (
            SELECT Chapter.url 
            FROM Chapter 
            WHERE Chapter.bookUrl IN (:bookUrls)
        )
    """)
    suspend fun deleteTranslationsByBookUrls(bookUrls: List<String>)

    @Query("""
        DELETE FROM ChapterTranslation 
        WHERE sourceLang = :sourceLang 
        AND targetLang = :targetLang
    """)
    suspend fun deleteTranslationsByLanguagePair(
        sourceLang: String,
        targetLang: String
    ): Int

    @Query("DELETE FROM ChapterTranslation")
    suspend fun deleteAllTranslations(): Int

    @Query("""
        SELECT ChapterTranslation.chapterUrl, ChapterTranslation.titleTranslation AS translatedText
        FROM ChapterTranslation
        INNER JOIN Chapter ON Chapter.url = ChapterTranslation.chapterUrl
        WHERE Chapter.bookUrl = :bookUrl
        AND ChapterTranslation.targetLang = :targetLang
        AND ChapterTranslation.titleTranslation != ''
    """)
    fun getTranslatedTitlesFlow(
        bookUrl: String,
        targetLang: String
    ): Flow<List<ChapterTitleTranslation>>

    @Query("SELECT COUNT(*) FROM ChapterTranslation")
    suspend fun count(): Int

    @Query("SELECT * FROM ChapterTranslation LIMIT :limit OFFSET :offset")
    suspend fun getChunk(limit: Int, offset: Int): List<ChapterTranslation>

    /**
     * Возвращает переводы глав по URL-ам. Фильтрация по паре языков выполняется
     * в SQL, а не в памяти. Пустые sourceLang/targetLang означают «все языки»
     * (используется миграцией, которая переносит переводы всех пар).
     */
    @Query("""
        SELECT * FROM ChapterTranslation 
        WHERE chapterUrl IN (:chapterUrls)
        AND (:sourceLang = '' OR sourceLang = :sourceLang)
        AND (:targetLang = '' OR targetLang = :targetLang)
    """)
    suspend fun getTranslationsByChapterUrls(
        chapterUrls: List<String>,
        sourceLang: String = "",
        targetLang: String = "",
    ): List<ChapterTranslation>

    @Query("""
        SELECT ChapterTranslation.sourceLang AS sourceLang,
               ChapterTranslation.targetLang AS targetLang,
               COUNT(*) AS count
        FROM ChapterTranslation
        INNER JOIN Chapter ON Chapter.url = ChapterTranslation.chapterUrl
        WHERE Chapter.bookUrl = :bookUrl
        AND ChapterTranslation.translatedParagraphs != ''
        GROUP BY ChapterTranslation.sourceLang, ChapterTranslation.targetLang
    """)
    suspend fun getTranslationGroups(bookUrl: String): List<TranslationGroup>
}
