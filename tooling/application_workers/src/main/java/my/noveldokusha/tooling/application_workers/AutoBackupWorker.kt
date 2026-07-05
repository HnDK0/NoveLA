package my.noveldokusha.tooling.application_workers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
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
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.AppRepository
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
    }

    companion object {
        const val TAG = "AutoBackup"
        internal const val TAG_AUTO = "AutoBackup:auto"
        private const val TAG_MANUAL = "AutoBackup:manual"
        private const val MIN_INTERVAL_MINUTES = 60L
        private const val AUTO_BACKUP_PREFIX = "auto_backup_"

        fun cancelTask(context: Context) {
            Log.d(TAG, "cancelTask: cancelling periodic work")
            WorkManager.getInstance(context).cancelUniqueWork(TAG_AUTO)
        }

        fun setupTask(context: Context, intervalMinutes: Long) {
            val effectiveInterval = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
            Log.d(TAG, "setupTask: called with intervalMinutes=$intervalMinutes (effective=$effectiveInterval)")
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
                Log.d(TAG, "setupTask: periodic work ENQUEUED, interval=$effectiveInterval min")
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(TAG_AUTO)
                Log.d(TAG, "setupTask: periodic work CANCELLED")
            }
        }

        fun startNow(context: Context) {
            Log.d(TAG, "startNow: creating one-time request")
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .addTag(TAG_MANUAL)
                .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to false))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG_MANUAL, ExistingWorkPolicy.KEEP, request
            )
            Log.d(TAG, "startNow: one-time backup ENQUEUED")
        }

        fun isScheduled(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG_AUTO)
                .get()
            val scheduled = workInfos?.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            } ?: false
            Log.d(TAG, "isScheduled: $scheduled (workInfos count=${workInfos?.size})")
            return scheduled
        }

        fun isManualJobRunning(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG_MANUAL)
                .get()
            val running = workInfos?.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            } ?: false
            Log.d(TAG, "isManualJobRunning: $running")
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
                Log.d(TAG, "isDirectoryAccessible: $accessible")
                accessible
            } catch (e: Exception) {
                Log.e(TAG, "isDirectoryAccessible: FAILED", e)
                false
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "========================================")
        Log.d(TAG, "doWork: STARTED")
        Log.d(TAG, "========================================")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoBackupEntryPoint::class.java
        )
        val appPreferences = entryPoint.appPreferences()
        Log.d(TAG, "doWork: got appPreferences via EntryPoint")

        if (!appPreferences.BACKUP_AUTO_ENABLED.value) {
            Log.d(TAG, "doWork: auto backup DISABLED, skipping")
            return Result.success()
        }

        val directoryUri = appPreferences.BACKUP_AUTO_DIRECTORY_URI.value
        if (directoryUri.isEmpty()) {
            Log.w(TAG, "doWork: no directory selected, skipping")
            return Result.success()
        }

        if (!isDirectoryAccessible(context, directoryUri)) {
            Log.e(TAG, "doWork: directory NOT ACCESSIBLE — disabling auto backup")
            appPreferences.BACKUP_AUTO_ENABLED.value = false
            return Result.success()
        }

        val includeImages = appPreferences.BACKUP_AUTO_INCLUDE_IMAGES.value
        val includeSettings = appPreferences.BACKUP_AUTO_INCLUDE_SETTINGS.value
        val includePlugins = appPreferences.BACKUP_AUTO_INCLUDE_PLUGINS.value
        val maxCount = appPreferences.BACKUP_AUTO_MAX_COUNT.value
        val lastTimestamp = appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.value
        val intervalMinutes = appPreferences.BACKUP_AUTO_INTERVAL_MINUTES.value

        Log.d(TAG, "doWork: includeImages=$includeImages, includeSettings=$includeSettings, includePlugins=$includePlugins, maxCount=$maxCount, lastTimestamp=$lastTimestamp, intervalMinutes=$intervalMinutes")

        val now = System.currentTimeMillis()
        val elapsed = now - lastTimestamp
        val intervalMs = intervalMinutes * 60 * 1000L

        if (elapsed < intervalMs && lastTimestamp > 0) {
            Log.d(TAG, "doWork: TOO SOON (elapsed=${elapsed}ms < interval=${intervalMs}ms), returning success")
            return Result.success()
        }

        Log.d(TAG, "doWork: starting backup...")
        val success = withContext(Dispatchers.IO) {
            try {
                performAutoBackup(context, directoryUri, includeImages, maxCount, includeSettings, includePlugins)
            } catch (e: Exception) {
                Log.e(TAG, "doWork: BACKUP FAILED", e)
                false
            }
        }

        Log.d(TAG, "doWork: COMPLETED, success=$success")
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
        Log.d(TAG, "performAutoBackup: STARTED")
        val entryPoint = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            AutoBackupEntryPoint::class.java
        )
        val appDatabase = entryPoint.appDatabase()
        val appRepository = entryPoint.appRepository()
        val appPreferences = entryPoint.appPreferences()
        Log.d(TAG, "performAutoBackup: got dependencies via EntryPoint")

        val pattern = "yyyyMMdd_HHmmss"
        val timestamp = SimpleDateFormat(pattern, Locale.US).format(Date())
        val fileName = "${AUTO_BACKUP_PREFIX}$timestamp.zip"

        val treeUri = Uri.parse(directoryUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        Log.d(TAG, "performAutoBackup: creating file '$fileName'")
        val createUri = DocumentsContract.createDocument(
            ctx.contentResolver,
            docUri,
            "application/zip",
            fileName
        ) ?: run {
            Log.e(TAG, "performAutoBackup: FAILED to create backup file via SAF")
            return false
        }
        Log.d(TAG, "performAutoBackup: file created successfully")

        try {
            Log.d(TAG, "performAutoBackup: clearing non-library data + vacuum")
            appRepository.settings.clearNonLibraryData()
            appRepository.vacuum()
            Log.d(TAG, "performAutoBackup: vacuum done")
        } catch (e: Exception) {
            Log.e(TAG, "performAutoBackup: clean/vacuum FAILED, continuing", e)
        }

        Log.d(TAG, "performAutoBackup: writing zip...")
        ctx.contentResolver.openOutputStream(createUri)?.use { outputStream ->
            val zip = ZipOutputStream(outputStream)

            // Database
            run {
                val entry = ZipEntry("database.sqlite3")
                val file = ctx.getDatabasePath(appDatabase.name)
                entry.method = ZipOutputStream.DEFLATED
                file.inputStream().use {
                    zip.putNextEntry(entry)
                    it.copyTo(zip)
                }
                Log.d(TAG, "performAutoBackup: Database backed up (${file.length()} bytes)")
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
                    }.toString()
                    zip.putNextEntry(entry)
                    zip.write(settingsJson.toByteArray())
                    zip.closeEntry()
                    Log.d(TAG, "performAutoBackup: Settings backed up")
                }
            } else {
                Log.d(TAG, "performAutoBackup: Settings excluded from backup")
            }

            // Lua extensions
            if (backupPlugins) {
                run {
                    val luaDir = File(ctx.filesDir, "lua_extensions")
                    if (luaDir.exists() && luaDir.isDirectory) {
                        val luaFiles = luaDir.listFiles()?.filter { it.isFile && it.extension == "lua" } ?: emptyList()
                        Log.d(TAG, "performAutoBackup: ${luaFiles.size} Lua extensions found")
                        luaFiles.forEach { file ->
                            val entry = ZipEntry("lua_extensions/${file.name}")
                            entry.method = ZipOutputStream.DEFLATED
                            file.inputStream().use {
                                zip.putNextEntry(entry)
                                it.copyTo(zip)
                            }
                        }
                    } else {
                        Log.d(TAG, "performAutoBackup: no lua_extensions directory")
                    }
                }
            } else {
                Log.d(TAG, "performAutoBackup: Plugins excluded from backup")
            }

            // Images
            if (backupImages) {
                Log.d(TAG, "performAutoBackup: backing up images...")
                val libraryBooks = appRepository.libraryBooks.getAllInLibrary()
                val libraryFolderNames = libraryBooks
                    .map { it.url.substringAfterLast("/").substringBefore("?") }
                    .toSet()
                Log.d(TAG, "performAutoBackup: ${libraryBooks.size} library books, ${libraryFolderNames.size} unique folders")

                val basePath = appRepository.settings.folderBooks.toPath().parent
                var imageCount = 0
                appRepository.settings.folderBooks.walkBottomUp()
                    .filterNot { it.isDirectory }
                    .filter { file ->
                        val relativePath = basePath.relativize(file.toPath()).toString()
                        val bookFolder = relativePath.split("/", "\\").getOrNull(1) ?: ""
                        bookFolder in libraryFolderNames || libraryFolderNames.isEmpty()
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
                Log.d(TAG, "performAutoBackup: $imageCount images backed up")
            } else {
                Log.d(TAG, "performAutoBackup: images not included")
            }

            zip.close()
            Log.d(TAG, "performAutoBackup: zip closed")
        } ?: run {
            Log.e(TAG, "performAutoBackup: FAILED to open output stream")
            return false
        }

        try {
            Log.d(TAG, "performAutoBackup: rotating old backups (maxCount=$maxCount)")
            rotateAutoBackups(ctx, directoryUri, maxCount)
        } catch (e: Exception) {
            Log.e(TAG, "performAutoBackup: Rotation FAILED", e)
        }

        appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.value = System.currentTimeMillis()
        Log.d(TAG, "performAutoBackup: COMPLETED successfully")
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

        Log.d(TAG, "rotateAutoBackups: found ${backupFiles.size} backup files, maxCount=$maxCount")
        backupFiles.sortBy { it.lastModified }

        if (backupFiles.size > maxCount) {
            val toDelete = backupFiles.size - maxCount
            Log.d(TAG, "rotateAutoBackups: deleting $toDelete old backups")
            for (i in 0 until toDelete) {
                try {
                    val deleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, backupFiles[i].documentId)
                    ctx.contentResolver.delete(deleteUri, null, null)
                    Log.d(TAG, "rotateAutoBackups: deleted '${backupFiles[i].documentId}'")
                } catch (e: Exception) {
                    Log.e(TAG, "rotateAutoBackups: FAILED to delete '${backupFiles[i].documentId}'", e)
                }
            }
        } else {
            Log.d(TAG, "rotateAutoBackups: no rotation needed")
        }
    }
}

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup"