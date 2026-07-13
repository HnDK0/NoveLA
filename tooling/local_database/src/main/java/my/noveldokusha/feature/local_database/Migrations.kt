package my.noveldokusha.feature.local_database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import my.noveldokusha.feature.local_database.migrations.MigrationsList
import my.noveldokusha.feature.local_database.migrations._1stKissNovelDomainChange_1_org
import my.noveldokusha.feature.local_database.migrations.readLightNovelDomainChange_1_today
import my.noveldokusha.feature.local_database.migrations.readLightNovelDomainChange_2_meme

// Helper function to check if a column exists in a table
private fun SupportSQLiteDatabase.columnExists(tableName: String, columnName: String): Boolean {
    val cursor = query("PRAGMA table_info($tableName)")
    cursor.use {
        val nameIndex = it.getColumnIndex("name")
        while (it.moveToNext()) {
            if (it.getString(nameIndex) == columnName) {
                return true
            }
        }
    }
    return false
}

// Helper function to safely add a column if it doesn't exist
private fun SupportSQLiteDatabase.addColumnIfNotExists(tableName: String, columnName: String, columnDef: String) {
    if (!columnExists(tableName, columnName)) {
        execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDef")
    }
}

internal fun databaseMigrations() = arrayOf(
    migration(1) {
        it.addColumnIfNotExists("Chapter", "position", "INTEGER NOT NULL DEFAULT 0")
    },
    migration(2) {
        it.addColumnIfNotExists("Book", "inLibrary", "INTEGER NOT NULL DEFAULT 0")
        it.execSQL("UPDATE Book SET inLibrary = 1")
    },
    migration(3) {
        it.addColumnIfNotExists("Book", "coverImageUrl", "TEXT NOT NULL DEFAULT ''")
        it.addColumnIfNotExists("Book", "description", "TEXT NOT NULL DEFAULT ''")
    },
    migration(4) {
        it.addColumnIfNotExists("Book", "lastReadEpochTimeMilli", "INTEGER NOT NULL DEFAULT 0")
    },
    migration(5, MigrationsList::readLightNovelDomainChange_1_today),
    migration(6, MigrationsList::readLightNovelDomainChange_2_meme),
    migration(7, MigrationsList::_1stKissNovelDomainChange_1_org),
    migration(8) {
        it.execSQL("""
            CREATE TABLE ChapterTranslation (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chapterUrl TEXT NOT NULL,
                sourceLang TEXT NOT NULL,
                targetLang TEXT NOT NULL,
                originalText TEXT NOT NULL,
                translatedText TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        it.execSQL("""
            CREATE INDEX index_ChapterTranslation_chapterUrl_sourceLang_targetLang
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang)
        """)
    },
    migration(9) {
        it.addColumnIfNotExists("Book", "addedToLibraryEpochTimeMilli", "INTEGER NOT NULL DEFAULT 0")
        it.addColumnIfNotExists("Book", "lastUpdateEpochTimeMilli", "INTEGER NOT NULL DEFAULT 0")
    },
    migration(10) {
        it.addColumnIfNotExists("Book", "category", "TEXT NOT NULL DEFAULT ''")
    },
    migration(11) {
        it.execSQL("""
            CREATE TABLE Extension (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                fileName TEXT NOT NULL,
                imageURL TEXT NOT NULL,
                language TEXT NOT NULL,
                version TEXT NOT NULL,
                md5 TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                installed INTEGER NOT NULL,
                chapterType TEXT NOT NULL,
                settings TEXT NOT NULL
            )
        """)
    },
    migration(12) {
        it.execSQL("ALTER TABLE Extension RENAME TO Extension_old")
        it.execSQL("""
            CREATE TABLE Extension (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                fileName TEXT NOT NULL,
                imageURL TEXT NOT NULL,
                language TEXT NOT NULL,
                version TEXT NOT NULL,
                md5 TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                installed INTEGER NOT NULL,
                chapterType TEXT NOT NULL,
                settings TEXT NOT NULL
            )
        """)
        it.execSQL("""
            INSERT INTO Extension (id, name, fileName, imageURL, language, version, md5, enabled, installed, chapterType, settings)
            SELECT name, name, fileName, imageURL, language, version, md5, enabled, installed, chapterType, settings
            FROM Extension_old
        """)
        it.execSQL("DROP TABLE Extension_old")
    },
    migration(13) {
        it.addColumnIfNotExists("Book", "chaptersListHash", "TEXT")
    },
    migration(14) {
        // Пересоздаём ChapterTranslation с составным primary key
        // вместо autoGenerate id. Это устраняет дубли при insertReplace
        // и гарантирует что на каждый originalText — одна запись.
        // Старые данные переносим, дубли по (chapterUrl, sourceLang, targetLang, originalText)
        // удаляем, оставляя самый свежий (MAX timestamp).
        it.execSQL("""
            CREATE TABLE ChapterTranslation_new (
                chapterUrl TEXT NOT NULL,
                sourceLang TEXT NOT NULL,
                targetLang TEXT NOT NULL,
                originalText TEXT NOT NULL,
                translatedText TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                PRIMARY KEY (chapterUrl, sourceLang, targetLang, originalText)
            )
        """)
        it.execSQL("""
            INSERT INTO ChapterTranslation_new (chapterUrl, sourceLang, targetLang, originalText, translatedText, timestamp)
            SELECT chapterUrl, sourceLang, targetLang, originalText, translatedText, MAX(timestamp)
            FROM ChapterTranslation
            GROUP BY chapterUrl, sourceLang, targetLang, originalText
        """)
        it.execSQL("DROP TABLE ChapterTranslation")
        it.execSQL("ALTER TABLE ChapterTranslation_new RENAME TO ChapterTranslation")
        it.execSQL("""
            CREATE INDEX index_ChapterTranslation_chapterUrl_sourceLang_targetLang
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang)
        """)
    },
    migration(15) {
        it.execSQL("""
            CREATE TABLE BookGenre (
                bookUrl TEXT NOT NULL,
                genre TEXT NOT NULL,
                PRIMARY KEY (bookUrl, genre)
            )
        """)
        it.execSQL("CREATE INDEX index_BookGenre_bookUrl ON BookGenre (bookUrl)")
        it.execSQL("CREATE INDEX index_BookGenre_genre ON BookGenre (genre)")
    },
    migration(16) {
        // parsePage support: store the last known page of chapters list per book.
        // null = plugin does not support parsePage (uses legacy getChapterList).
        if (!it.columnExists("Book", "chaptersLastPage")) {
            it.execSQL("ALTER TABLE Book ADD COLUMN chaptersLastPage INTEGER")
        }
    },
    migration(17) {
        // Optimize ChapterTranslation: replace composite PK (chapterUrl,sourceLang,targetLang,originalText)
        // with auto-generated Long id + paragraphIndex + unique index on (chapterUrl,sourceLang,targetLang,paragraphIndex).
        // This drastically reduces index size since originalText (~500 chars) is no longer part of the PK.
        // Also adds index on Chapter(bookUrl) for faster chapter lookups.
        it.execSQL("""
            CREATE TABLE ChapterTranslation_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chapterUrl TEXT NOT NULL,
                sourceLang TEXT NOT NULL,
                targetLang TEXT NOT NULL,
                paragraphIndex INTEGER NOT NULL,
                originalText TEXT NOT NULL,
                translatedText TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        // Migrate data with row_number partition to assign paragraphIndex
        it.execSQL("""
            INSERT INTO ChapterTranslation_new (chapterUrl, sourceLang, targetLang, paragraphIndex, originalText, translatedText, timestamp)
            SELECT chapterUrl, sourceLang, targetLang,
                ROW_NUMBER() OVER (
                    PARTITION BY chapterUrl, sourceLang, targetLang
                    ORDER BY timestamp
                ) - 1 AS paragraphIndex,
                originalText, translatedText, timestamp
            FROM ChapterTranslation
        """)
        it.execSQL("DROP TABLE ChapterTranslation")
        it.execSQL("ALTER TABLE ChapterTranslation_new RENAME TO ChapterTranslation")
        it.execSQL("""
            CREATE UNIQUE INDEX index_ChapterTranslation_chapterUrl_sourceLang_targetLang_paragraphIndex
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang, paragraphIndex)
        """)
        it.execSQL("""
            CREATE INDEX index_ChapterTranslation_chapterUrl_sourceLang_targetLang
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang)
        """)
        // Add index on Chapter(bookUrl) for faster queries
        it.execSQL("CREATE INDEX index_Chapter_bookUrl ON Chapter (bookUrl)")
    },
    migration(18) {
        // Index on Book.inLibrary for faster library queries (getBooksInLibraryWithContextFlow)
        it.execSQL("CREATE INDEX index_Book_inLibrary ON Book (inLibrary)")
    },
    migration(19) {
        // Перенос жанров из отдельной таблицы BookGenre в поле Book.genres (через запятую)
        // 1. Добавляем колонку genres
        it.addColumnIfNotExists("Book", "genres", "TEXT NOT NULL DEFAULT ''")
        // 2. Переносим данные из BookGenre в Book.genres, группируя по bookUrl
        it.execSQL("""
            UPDATE Book SET genres = (
                SELECT GROUP_CONCAT(genre, ',') FROM (
                    SELECT DISTINCT genre FROM BookGenre WHERE BookGenre.bookUrl = Book.url ORDER BY genre
                )
            ) WHERE url IN (SELECT DISTINCT bookUrl FROM BookGenre)
        """)
        // 3. Удаляем таблицу BookGenre
        it.execSQL("DROP TABLE IF EXISTS BookGenre")
    },
    migration(20) {
        // Персистентное хранение очереди загрузок DownloadManager
        it.execSQL("""
            CREATE TABLE DownloadTask (
                bookUrl TEXT NOT NULL PRIMARY KEY,
                bookTitle TEXT NOT NULL,
                chapterUrlsJson TEXT NOT NULL,
                currentIndex INTEGER NOT NULL DEFAULT 0,
                totalCount INTEGER NOT NULL DEFAULT 0,
                isPaused INTEGER NOT NULL DEFAULT 0,
                isCancelled INTEGER NOT NULL DEFAULT 0,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                errorCount INTEGER NOT NULL DEFAULT 0,
                successCount INTEGER NOT NULL DEFAULT 0,
                consecutiveErrors INTEGER NOT NULL DEFAULT 0,
                skippedCount INTEGER NOT NULL DEFAULT 0,
                translationErrorCount INTEGER NOT NULL DEFAULT 0
            )
        """)
    },
    migration(21) {
        // isWaitingForNetwork: показывает что задача ждёт восстановления сети (DNS/соединение)
        it.addColumnIfNotExists("DownloadTask", "isWaitingForNetwork", "INTEGER NOT NULL DEFAULT 0")
    },
    migration(22) { db ->
        // Migrate ChapterTranslation from per-paragraph to per-chapter storage
        // Migrate ChapterTranslation from per-paragraph to per-chapter storage:
        //   (chapterUrl, sourceLang, targetLang, paragraphIndex, originalText, translatedText, timestamp)
        //   →
        //   (chapterUrl, sourceLang, targetLang, translatedParagraphs JSON, titleTranslation, timestamp)
        db.execSQL("""
            CREATE TABLE ChapterTranslation_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chapterUrl TEXT NOT NULL,
                sourceLang TEXT NOT NULL,
                targetLang TEXT NOT NULL,
                translatedParagraphs TEXT NOT NULL DEFAULT '[]',
                titleTranslation TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL
            )
        """)

        // Group old rows by (chapterUrl, sourceLang, targetLang) and build per-chapter rows
        val cursor = db.query("""
            SELECT DISTINCT chapterUrl, sourceLang, targetLang
            FROM ChapterTranslation
        """)
        cursor.use { c ->
            while (c.moveToNext()) {
                val chapterUrl = c.getString(0)
                val sourceLang = c.getString(1)
                val targetLang = c.getString(2)

                // Collect body paragraph translations ordered by paragraphIndex
                val bodyCursor = db.query("""
                    SELECT translatedText FROM ChapterTranslation
                    WHERE chapterUrl = ? AND sourceLang = ? AND targetLang = ? AND paragraphIndex >= 0
                    ORDER BY paragraphIndex
                """, arrayOf(chapterUrl, sourceLang, targetLang))
                val translatedParagraphs = org.json.JSONArray()
                bodyCursor.use { body ->
                    while (body.moveToNext()) {
                        translatedParagraphs.put(body.getString(0))
                    }
                }

                // Get title translation (paragraphIndex = -1)
                val titleCursor = db.query("""
                    SELECT translatedText FROM ChapterTranslation
                    WHERE chapterUrl = ? AND sourceLang = ? AND targetLang = ? AND paragraphIndex = -1
                    LIMIT 1
                """, arrayOf(chapterUrl, sourceLang, targetLang))
                var titleTranslation = ""
                titleCursor.use { title ->
                    if (title.moveToNext()) {
                        titleTranslation = title.getString(0)
                    }
                }

                // Get max timestamp
                val tsCursor = db.query("""
                    SELECT MAX(timestamp) FROM ChapterTranslation
                    WHERE chapterUrl = ? AND sourceLang = ? AND targetLang = ?
                """, arrayOf(chapterUrl, sourceLang, targetLang))
                var timestamp = System.currentTimeMillis()
                tsCursor.use { ts ->
                    if (ts.moveToNext()) {
                        timestamp = ts.getLong(0)
                    }
                }

                db.execSQL("""
                    INSERT INTO ChapterTranslation_new (chapterUrl, sourceLang, targetLang, translatedParagraphs, titleTranslation, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, arrayOf(chapterUrl, sourceLang, targetLang, translatedParagraphs.toString(), titleTranslation, timestamp))
            }
        }

        db.execSQL("DROP TABLE ChapterTranslation")
        db.execSQL("ALTER TABLE ChapterTranslation_new RENAME TO ChapterTranslation")
        db.execSQL("""
            CREATE UNIQUE INDEX index_ChapterTranslation_chapterUrl_sourceLang_targetLang
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang)
        """)
    },
    migration(23) { db ->
        db.execSQL("""
            CREATE TABLE migration_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                oldBookUrl TEXT NOT NULL,
                newBookUrl TEXT NOT NULL,
                oldSourceId TEXT NOT NULL,
                newSourceId TEXT NOT NULL,
                status TEXT NOT NULL,
                chaptersTotal INTEGER NOT NULL,
                chaptersMatched INTEGER NOT NULL,
                chaptersWithProgress INTEGER NOT NULL,
                chaptersWithBody INTEGER NOT NULL,
                migratedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX index_migration_records_oldBookUrl ON migration_records (oldBookUrl)")
        db.execSQL("CREATE INDEX index_migration_records_newBookUrl ON migration_records (newBookUrl)")
    },
    migration(24) {
        // Empty migration to recalculate Room identity hash
        // (entity annotations changed from initial v24 release)
    },
)

internal fun migration(vi: Int, migrate: (SupportSQLiteDatabase) -> Unit) =
    object : Migration(vi, vi + 1) {
        override fun migrate(db: SupportSQLiteDatabase) = migrate(db)
    }