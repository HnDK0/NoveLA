package my.noveldokusha.tooling.application_workers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import timber.log.Timber
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.CoverRepository
import my.noveldokusha.data.backfillCovers
import my.noveldokusha.feature.local_database.AppDatabase
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AutoBackupWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutoBackupEntryPoint {
        fun appDatabase(): AppDatabase
        fun appRepository(): AppRepository
        fun appPreferences(): AppPreferences
        fun appFileResolver(): AppFileResolver
        fun coverRepository(): CoverRepository
    }

    companion object {
        const val TAG = "AutoBackup"
        internal const val TAG_AUTO = "AutoBackup:auto"
        private const val TAG_MANUAL = "AutoBackup:manual"
        private const val MIN_INTERVAL_MINUTES = 60L
        private const val AUTO_BACKUP_PREFIX = "auto_backup_"

        fun cancelTask(context: Context) {
            Timber.d( "cancelTask: cancelling periodic work")
            WorkManager.getInstance(context).cancelUniqueWork(TAG_AUTO)
        }

        fun setupTask(context: Context, intervalMinutes: Long) {
            val effectiveInterval = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
            Timber.d( "setupTask: called with intervalMinutes=$intervalMinutes (effective=$effectiveInterval)")
            if (intervalMinutes > 0) {
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                    effectiveInterval, TimeUnit.MINUTES,
                    10, TimeUnit.MINUTES
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .setConstraints(Constraints(requiresBatteryNotLow = true))
                    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request
                )
                Timber.d( "setupTask: periodic work ENQUEUED, interval=$effectiveInterval min")
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(TAG_AUTO)
                Timber.d( "setupTask: periodic work CANCELLED")
            }
        }

        fun startNow(context: Context) {
            Timber.d( "startNow: creating one-time request")
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .addTag(TAG_MANUAL)
                .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to false))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG_MANUAL, ExistingWorkPolicy.KEEP, request
            )
            Timber.d( "startNow: one-time backup ENQUEUED")
        }

        fun isScheduled(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG_AUTO)
                .get()
            val scheduled = workInfos?.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            } ?: false
            Timber.d( "isScheduled: $scheduled (workInfos count=${workInfos?.size})")
            return scheduled
        }

        fun isManualJobRunning(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG_MANUAL)
                .get()
            val running = workInfos?.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            } ?: false
            Timber.d( "isManualJobRunning: $running")
            return running
        }

        fun isDirectoryAccessible(context: Context, directoryUri: String): Boolean {
            return try {
                val treeUri = Uri.parse(directoryUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val accessible = context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null
                )?.use { true } ?: false
                Timber.d( "isDirectoryAccessible: $accessible")
                accessible
            } catch (e: Exception) {
                Timber.e( "isDirectoryAccessible: FAILED", e)
                false
            }
        }
    }

    override suspend fun doWork(): Result {
        Timber.d( "========================================")
        Timber.d( "doWork: STARTED")
        Timber.d( "========================================")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoBackupEntryPoint::class.java
        )
        val appPreferences = entryPoint.appPreferences()
        Timber.d( "doWork: got appPreferences via EntryPoint")

        if (!appPreferences.BACKUP_AUTO_ENABLED.value) {
            Timber.d( "doWork: auto backup DISABLED, skipping")
            return Result.success()
        }

        val directoryUri = appPreferences.BACKUP_AUTO_DIRECTORY_URI.value
        if (directoryUri.isEmpty()) {
            Timber.w( "doWork: no directory selected, skipping")
            return Result.success()
        }

        if (!isDirectoryAccessible(context, directoryUri)) {
            Timber.e( "doWork: directory NOT ACCESSIBLE — disabling auto backup")
            appPreferences.BACKUP_AUTO_ENABLED.value = false
            return Result.success()
        }

        val includeImages = appPreferences.BACKUP_AUTO_INCLUDE_IMAGES.value
        val includeSettings = appPreferences.BACKUP_AUTO_INCLUDE_SETTINGS.value
        val includePlugins = appPreferences.BACKUP_AUTO_INCLUDE_PLUGINS.value
        val maxCount = appPreferences.BACKUP_AUTO_MAX_COUNT.value
        val lastTimestamp = appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.value
        val intervalMinutes = appPreferences.BACKUP_AUTO_INTERVAL_MINUTES.value

        Timber.d( "doWork: includeImages=$includeImages, includeSettings=$includeSettings, includePlugins=$includePlugins, maxCount=$maxCount, lastTimestamp=$lastTimestamp, intervalMinutes=$intervalMinutes")

        val now = System.currentTimeMillis()
        val elapsed = now - lastTimestamp
        val intervalMs = intervalMinutes * 60 * 1000L

        if (elapsed < intervalMs && lastTimestamp > 0) {
            Timber.d( "doWork: TOO SOON (elapsed=${elapsed}ms < interval=${intervalMs}ms), returning success")
            return Result.success()
        }

        Timber.d( "doWork: starting backup...")
        val success = withContext(Dispatchers.IO) {
            try {
                performAutoBackup(context, directoryUri, includeImages, maxCount, includeSettings, includePlugins)
            } catch (e: Exception) {
                Timber.e(e, "doWork: BACKUP FAILED")
                false
            }
        }

        Timber.d( "doWork: COMPLETED, success=$success")
        return if (success) Result.success() else Result.failure()
    }

    private suspend fun performAutoBackup(
        ctx: Context,
        directoryUri: String,
        backupImages: Boolean,
        maxCount: Int,
        backupSettings: Boolean = true,
        backupPlugins: Boolean = true
    ): Boolean {
        Timber.d( "performAutoBackup: STARTED")
        val entryPoint = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            AutoBackupEntryPoint::class.java
        )
        val appDatabase = entryPoint.appDatabase()
        val appRepository = entryPoint.appRepository()
        val appPreferences = entryPoint.appPreferences()
        val appFileResolver = entryPoint.appFileResolver()
        val coverRepository = entryPoint.coverRepository()
        Timber.d( "performAutoBackup: got dependencies via EntryPoint")

        val pattern = "yyyyMMdd_HHmmss"
        val timestamp = SimpleDateFormat(pattern, Locale.US).format(Date())
        val fileName = "${AUTO_BACKUP_PREFIX}$timestamp.zip"

        val treeUri = Uri.parse(directoryUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        Timber.d( "performAutoBackup: creating file '$fileName'")
        val createUri = DocumentsContract.createDocument(
            ctx.contentResolver,
            docUri,
            "application/zip",
            fileName
        ) ?: run {
            Timber.e( "performAutoBackup: FAILED to create backup file via SAF")
            return false
        }
        Timber.d( "performAutoBackup: file created successfully")

        // Step 1: clean non-library data
        Timber.d( "performAutoBackup: clearing non-library data")
        appRepository.settings.clearNonLibraryData()
        Timber.d( "performAutoBackup: non-library data cleared")

        // Step 2: verify source database integrity
        val sourceIntegrity = appDatabase.integrityCheck()
        if (sourceIntegrity != "ok") {
            Timber.e( "performAutoBackup: Source database integrity check FAILED: $sourceIntegrity")
            return false
        }
        Timber.d( "performAutoBackup: source database integrity OK")

        // Step 3: VACUUM INTO temporary file
        val tempDbFile = File(ctx.cacheDir, "auto_backup_vacuum_into_${System.currentTimeMillis()}.db")
        try {
            appDatabase.vacuumInto(tempDbFile.absolutePath)
            Timber.d( "performAutoBackup: VACUUM INTO done, temp file size: ${tempDbFile.length()}")
        } catch (e: Exception) {
            tempDbFile.delete()
            throw e
        }

        // Step 4: verify snapshot integrity
        val snapshotIntegrity = AppDatabase.checkFileIntegrity(ctx, tempDbFile.absolutePath)
        if (snapshotIntegrity != "ok") {
            tempDbFile.delete()
            throw Exception("Auto backup snapshot integrity check failed: $snapshotIntegrity")
        }
        Timber.d( "performAutoBackup: snapshot integrity OK")

        Timber.d( "performAutoBackup: writing zip...")
        try {
        ctx.contentResolver.openOutputStream(createUri)?.use { outputStream ->
            val zip = ZipOutputStream(outputStream)

            // Database
            run {
                val entry = ZipEntry("database.sqlite3")
                entry.method = ZipOutputStream.DEFLATED
                tempDbFile.inputStream().use {
                    zip.putNextEntry(entry)
                    it.copyTo(zip)
                }
                Timber.d( "performAutoBackup: Database backed up (${tempDbFile.length()} bytes)")
            }

            // Settings
            if (backupSettings) {
                run {
                    val entry = ZipEntry("settings.json")
                    entry.method = ZipOutputStream.DEFLATED
                    val settingsJson = JSONObject().apply {
                        put("TRANSLATION_GOOGLE_PA_API_KEYS", appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value)
                        put("TRANSLATION_GEMINI_API_KEY", appPreferences.TRANSLATION_GEMINI_API_KEY.value)
                        put("TRANSLATION_GEMINI_MODEL", appPreferences.TRANSLATION_GEMINI_MODEL.value)
                        put("TRANSLATION_OPENAI_BASE_URL", appPreferences.TRANSLATION_OPENAI_BASE_URL.value)
                        put("TRANSLATION_OPENAI_API_KEYS", appPreferences.TRANSLATION_OPENAI_API_KEYS.value)
                        put("TRANSLATION_OPENAI_MODEL", appPreferences.TRANSLATION_OPENAI_MODEL.value)
                        put("LIBRARY_CUSTOM_CATEGORIES", org.json.JSONArray(appPreferences.LIBRARY_CUSTOM_CATEGORIES.value))
                        put("TRANSLATION_ACTIVE_SYSTEM_PROMPT", appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value)
                        put("TRANSLATION_PROMPT_PRESETS", org.json.JSONArray(
                            appPreferences.TRANSLATION_PROMPT_PRESETS.value.map { pair ->
                                JSONObject().apply {
                                    put("name", pair.first)
                                    put("prompt", pair.second)
                                }
                            }
                        ))
                        put("TRANSLATION_NOVEL_PROMPTS", JSONObject().apply {
                            appPreferences.TRANSLATION_NOVEL_PROMPTS.value.forEach { (url, data) ->
                                put(url, JSONObject().apply {
                                    put("title", data.title)
                                    put("prompt", data.prompt)
                                    put("appendMode", data.appendMode)
                                })
                            }
                        })
                    }.toString()
                    zip.putNextEntry(entry)
                    zip.write(settingsJson.toByteArray())
                    zip.closeEntry()
                    Timber.d( "performAutoBackup: Settings backed up")
                }
            } else {
                Timber.d( "performAutoBackup: Settings excluded from backup")
            }

            // Lua extensions
            if (backupPlugins) {
                run {
                    val luaDir = File(ctx.filesDir, "lua_extensions")
                    if (luaDir.exists() && luaDir.isDirectory) {
                        val luaFiles = luaDir.listFiles()?.filter { it.isFile && it.extension == "lua" } ?: emptyList()
                        Timber.d( "performAutoBackup: ${luaFiles.size} Lua extensions found")
                        luaFiles.forEach { file ->
                            val entry = ZipEntry("lua_extensions/${file.name}")
                            entry.method = ZipOutputStream.DEFLATED
                            file.inputStream().use {
                                zip.putNextEntry(entry)
                                it.copyTo(zip)
                            }
                        }
                    } else {
                        Timber.d( "performAutoBackup: no lua_extensions directory")
                    }
                }
            } else {
                Timber.d( "performAutoBackup: Plugins excluded from backup")
            }

            // Images
            if (backupImages) {
                // Best-effort: make sure local covers exist before we back them up,
                // so the archive contains up-to-date artwork (idempotent, skips valid covers).
                // Isolated so a single book's cover-sync failure cannot abort the whole backup.
                try {
                    val books = appRepository.libraryBooks.getAllInLibrary()
                    backfillCovers(books, appFileResolver, coverRepository)
                } catch (e: Exception) {
                    Timber.e(e, "performAutoBackup: cover backfill failed, continuing backup")
                }

                Timber.d( "performAutoBackup: backing up images...")
                val libraryBooks = appRepository.libraryBooks.getAllInLibrary()
                val libraryFolderNames = libraryBooks
                    .map { appFileResolver.getLocalBookFolderName(it.url) }
                    .toSet()
                Timber.d( "performAutoBackup: ${libraryBooks.size} library books, ${libraryFolderNames.size} unique folders")

                val basePath = appRepository.settings.folderBooks.toPath().parent
                var imageCount = 0
                appRepository.settings.folderBooks.walkBottomUp()
                    .filterNot { it.isDirectory }
                    .filter { file ->
                        val relativePath = basePath.relativize(file.toPath()).toString()
                        val bookFolder = relativePath.split("/", "\\").getOrNull(1) ?: ""
                        bookFolder in libraryFolderNames
                    }
                    .forEach { file ->
                        val name = basePath.relativize(file.toPath()).toString()
                        val entry = ZipEntry(name)
                        entry.method = ZipOutputStream.DEFLATED
                        file.inputStream().use {
                            zip.putNextEntry(entry)
                            it.copyTo(zip)
                        }
                        imageCount++
                    }
                Timber.d( "performAutoBackup: $imageCount images backed up")
            } else {
                Timber.d( "performAutoBackup: images not included")
            }

            zip.close()
            Timber.d( "performAutoBackup: zip closed")
        } ?: run {
            Timber.e( "performAutoBackup: FAILED to open output stream")
            return false
        }
        } finally {
            tempDbFile.delete()
        }

        try {
            Timber.d( "performAutoBackup: rotating old backups (maxCount=$maxCount)")
            rotateAutoBackups(ctx, directoryUri, maxCount)
        } catch (e: Exception) {
            Timber.e(e, "performAutoBackup: Rotation FAILED")
        }

        appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.value = System.currentTimeMillis()
        Timber.d( "performAutoBackup: COMPLETED successfully")
        return true
    }

    private fun rotateAutoBackups(ctx: Context, directoryUri: String, maxCount: Int) {
        if (maxCount <= 0) return

        val treeUri = Uri.parse(directoryUri)
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        data class BackupFile(val documentId: String, val lastModified: Long)
        val backupFiles = mutableListOf<BackupFile>()

        ctx.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docIdChild = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                )
                val displayName = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
                val lastModified = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                )
                if (displayName.startsWith(AUTO_BACKUP_PREFIX) && displayName.endsWith(".zip")) {
                    backupFiles.add(BackupFile(docIdChild, lastModified))
                }
            }
        }

        Timber.d( "rotateAutoBackups: found ${backupFiles.size} backup files, maxCount=$maxCount")
        backupFiles.sortBy { it.lastModified }

        if (backupFiles.size > maxCount) {
            val toDelete = backupFiles.size - maxCount
            Timber.d( "rotateAutoBackups: deleting $toDelete old backups")
            for (i in 0 until toDelete) {
                try {
                    val deleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, backupFiles[i].documentId)
                    ctx.contentResolver.delete(deleteUri, null, null)
                    Timber.d( "rotateAutoBackups: deleted '${backupFiles[i].documentId}'")
                } catch (e: Exception) {
                    Timber.e(e, "rotateAutoBackups: FAILED to delete '${backupFiles[i].documentId}'")
                }
            }
        } else {
            Timber.d( "rotateAutoBackups: no rotation needed")
        }
    }
}

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup"