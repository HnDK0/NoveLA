package my.noveldokusha.feature.local_database.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("UnusedReceiverParameter")
internal fun MigrationsList.websiteDomainChangeHelper(
    it: SupportSQLiteDatabase,
    oldDomain: String,
    newDomain: String,
) {
    // readlightnovel source changed its domain to "newDomain"
    fun replace(columnName: String) =
        """$columnName = REPLACE($columnName, "$oldDomain", "$newDomain")"""

    fun like(columnName: String) =
        """($columnName LIKE "%$oldDomain%")"""
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