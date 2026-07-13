package my.noveldokusha.feature.local_database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.DAOs.DownloadTaskDao
import my.noveldokusha.feature.local_database.DAOs.ExtensionDao
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.DAOs.NovelMigrationDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterTranslation
import my.noveldokusha.feature.local_database.tables.DownloadTaskEntity
import my.noveldokusha.feature.local_database.tables.Extension
import my.noveldokusha.feature.local_database.tables.MigrationRecord
import java.io.InputStream


interface AppDatabase {
    fun libraryDao(): LibraryDao
    fun chapterDao(): ChapterDao
    fun chapterBodyDao(): ChapterBodyDao
    fun chapterTranslationDao(): ChapterTranslationDao
    fun downloadTaskDao(): DownloadTaskDao
    fun extensionDao(): ExtensionDao
    fun novelMigrationDao(): NovelMigrationDao
    val name: String

    fun closeDatabase()
    fun clearDatabase()
    suspend fun vacuum()
    suspend fun vacuumInto(targetPath: String)
    suspend fun integrityCheck(): String

    /**
     * Execute the whole database calls as an atomic operation
     */
    suspend fun <T> transaction(block: suspend () -> T): T

    companion object {
        fun createRoom(ctx: Context, name: String): AppDatabase = Room
            .databaseBuilder(ctx, AppRoomDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.query("PRAGMA journal_size_limit = 134217728").close()
                }
            })
            .addMigrations(*databaseMigrations())
            .build()
            .also { it.name = name }

        suspend fun checkFileIntegrity(ctx: Context, filePath: String): String = withContext(Dispatchers.IO) {
            val db = Room.databaseBuilder(ctx, AppRoomDatabase::class.java, filePath)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
            db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }.also { db.close() }
        }

        fun createRoomFromStream(
            ctx: Context,
            name: String,
            inputStream: InputStream
        ): AppDatabase = Room
            .databaseBuilder(ctx, AppRoomDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .createFromInputStream { inputStream }
            .fallbackToDestructiveMigration(false) // Don't apply migrations, database is already at correct version
            .build()
            .also { it.name = name }
    }
}


@Database(
    entities = [
        Book::class,
        Chapter::class,
        ChapterBody::class,
        ChapterTranslation::class,
        DownloadTaskEntity::class,
        Extension::class,
        MigrationRecord::class
    ],
    version = 25,
    exportSchema = false
)
internal abstract class AppRoomDatabase : RoomDatabase(), AppDatabase {
    abstract override fun libraryDao(): LibraryDao
    abstract override fun chapterDao(): ChapterDao
    abstract override fun chapterBodyDao(): ChapterBodyDao
    abstract override fun chapterTranslationDao(): ChapterTranslationDao
    abstract override fun downloadTaskDao(): DownloadTaskDao
    abstract override fun extensionDao(): ExtensionDao
    abstract override fun novelMigrationDao(): NovelMigrationDao
    override lateinit var name: String

    override suspend fun <T> transaction(block: suspend () -> T): T = withTransaction(block)

    override fun closeDatabase() {
        close()
    }

    override fun clearDatabase() {
        clearAllTables()
    }

    override suspend fun vacuum() {
        withContext(Dispatchers.IO) {
            openHelper.writableDatabase.execSQL("VACUUM")
        }
    }

    override suspend fun vacuumInto(targetPath: String) {
        withContext(Dispatchers.IO) {
            openHelper.writableDatabase.execSQL("VACUUM INTO '$targetPath'")
        }
    }

    override suspend fun integrityCheck(): String {
        return withContext(Dispatchers.IO) {
            openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        }
    }
}