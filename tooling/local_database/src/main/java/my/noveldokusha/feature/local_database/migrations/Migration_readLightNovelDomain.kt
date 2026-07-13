@file:Suppress("FunctionName")

package my.noveldokusha.feature.local_database.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("UnusedReceiverParameter")
internal fun MigrationsList.readLightNovelDomainChange_1_today(
    it: SupportSQLiteDatabase
) {
    // readlightnovel source changed its domain to "today"
    val old1 = "www.readlightnovel.org"
    val old2 = "www.readlightnovel.me"
    val new = "www.readlightnovel.today"
    fun replace(columnName: String) =
        """$columnName = REPLACE(REPLACE($columnName, "$old1", "$new"), "$old2", "$new")"""

    fun like(columnName: String) =
        """($columnName LIKE "%$old1%" OR $columnName LIKE "%$old2%")"""
    it.execSQL(
        """
            UPDATE Book
            SET ${replace("url")},
                ${replace("coverImageUrl")}
            WHERE
                ${like("url")};
        """.trimIndent()
    )
    it.execSQL(
        """
            UPDATE Chapter
            SET ${replace("url")},
                ${replace("bookUrl")}
            WHERE
                ${like("bookUrl")};
        """.trimIndent()
    )
    it.execSQL(
        """
            UPDATE ChapterBody
            SET ${replace("url")}
            WHERE
                ${like("url")};
        """.trimIndent()
    )
}

internal fun MigrationsList.readLightNovelDomainChange_2_meme(
    it: SupportSQLiteDatabase,
) = this.websiteDomainChangeHelper(
    it,
    oldDomain = "www.readlightnovel.today",
    newDomain = "www.readlightnovel.meme",
)
