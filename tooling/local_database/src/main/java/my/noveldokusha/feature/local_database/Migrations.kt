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
            CREATE TABLE IF NOT EXISTS ChapterTranslation (
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
            CREATE INDEX IF NOT EXISTS index_ChapterTranslation_chapterUrl_sourceLang_targetLang
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
            CREATE TABLE IF NOT EXISTS Extension (
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
            CREATE TABLE IF NOT EXISTS ChapterTranslation_new (
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
            CREATE INDEX IF NOT EXISTS index_ChapterTranslation_chapterUrl_sourceLang_targetLang
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang)
        """)
    },
    migration(15) {
        it.execSQL("""
            CREATE TABLE IF NOT EXISTS BookGenre (
                bookUrl TEXT NOT NULL,
                genre TEXT NOT NULL,
                PRIMARY KEY (bookUrl, genre)
            )
        """)
        it.execSQL("CREATE INDEX IF NOT EXISTS index_BookGenre_bookUrl ON BookGenre (bookUrl)")
        it.execSQL("CREATE INDEX IF NOT EXISTS index_BookGenre_genre ON BookGenre (genre)")
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
            CREATE TABLE IF NOT EXISTS ChapterTranslation_new (
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
            CREATE UNIQUE INDEX IF NOT EXISTS index_ChapterTranslation_chapterUrl_sourceLang_targetLang_paragraphIndex
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang, paragraphIndex)
        """)
        it.execSQL("""
            CREATE INDEX IF NOT EXISTS index_ChapterTranslation_chapterUrl_sourceLang_targetLang
            ON ChapterTranslation (chapterUrl, sourceLang, targetLang)
        """)
        // Add index on Chapter(bookUrl) for faster queries
        it.execSQL("CREATE INDEX IF NOT EXISTS index_Chapter_bookUrl ON Chapter (bookUrl)")
    },
    migration(18) {
        // Index on Book.inLibrary for faster library queries (getBooksInLibraryWithContextFlow)
        it.execSQL("CREATE INDEX IF NOT EXISTS index_Book_inLibrary ON Book (inLibrary)")
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
            CREATE TABLE IF NOT EXISTS DownloadTask (
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
)

internal fun migration(vi: Int, migrate: (SupportSQLiteDatabase) -> Unit) =
    object : Migration(vi, vi + 1) {
        override fun migrate(db: SupportSQLiteDatabase) = migrate(db)
    }