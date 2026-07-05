package my.noveldokusha.tooling.backup_create

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.coreui.states.removeProgressBar
import my.noveldokusha.coreui.states.text
import my.noveldokusha.coreui.states.title
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.AppRepository
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.core.utils.Extra_Boolean
import my.noveldokusha.core.utils.Extra_Int
import my.noveldokusha.core.utils.Extra_Uri
import my.noveldokusha.core.utils.Extra_String
import my.noveldokusha.core.utils.isServiceRunning
import my.noveldokusha.feature.local_database.AppDatabase
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class BackupDataService : Service() {

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var notificationsCenter: NotificationsCenter

    @Inject
    lateinit var appPreferences: AppPreferences

    private val channelName by lazy { getString(R.string.notification_channel_name_backup) }
    private val channelId = "Backup"
    private val notificationId: Int = channelId.hashCode()

    private class IntentData : Intent {
        var uri by Extra_Uri()
        var backupImages by Extra_Boolean()
        var backupSettings by Extra_Boolean()
        var backupPlugins by Extra_Boolean()
        var isAutoBackup by Extra_Boolean()
        var directoryUri by Extra_String()
        var maxCount by Extra_Int()

        constructor(intent: Intent) : super(intent)
        constructor(
            ctx: Context,
            uri: Uri,
            backupImages: Boolean,
            backupSettings: Boolean = true,
            backupPlugins: Boolean = true,
            isAutoBackup: Boolean = false,
            directoryUri: String = "",
            maxCount: Int = 5
        ) : super(
            ctx,
            BackupDataService::class.java
        ) {
            this.uri = uri
            this.backupImages = backupImages
            this.backupSettings = backupSettings
            this.backupPlugins = backupPlugins
            this.isAutoBackup = isAutoBackup
            this.directoryUri = directoryUri
            this.maxCount = maxCount
        }
    }

    companion object {
        private const val AUTO_BACKUP_PREFIX = "auto_backup_"

        fun start(
            ctx: Context,
            uri: Uri,
            backupImages: Boolean,
            backupSettings: Boolean = true,
            backupPlugins: Boolean = true
        ) {
            if (!isRunning(ctx))
                ctx.startService(IntentData(ctx, uri, backupImages, backupSettings, backupPlugins))
        }

        fun startAutoBackup(
            ctx: Context,
            directoryUri: String,
            backupImages: Boolean,
            maxCount: Int,
            backupSettings: Boolean = true,
            backupPlugins: Boolean = true
        ) {
            if (!isRunning(ctx)) {
                // Generate filename with timestamp
                val pattern = "yyyyMMdd_HHmmss"
                val timestamp = SimpleDateFormat(pattern, Locale.US).format(Date())
                val fileName = "${AUTO_BACKUP_PREFIX}$timestamp.zip"

                // Create file in the directory using SAF
                val treeUri = Uri.parse(directoryUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val createUri = DocumentsContract.createDocument(
                    ctx.contentResolver,
                    docUri,
                    "application/zip",
                    fileName
                )

                if (createUri != null) {
                    ctx.startService(IntentData(
                        ctx = ctx,
                        uri = createUri,
                        backupImages = backupImages,
                        backupSettings = backupSettings,
                        backupPlugins = backupPlugins,
                        isAutoBackup = true,
                        directoryUri = directoryUri,
                        maxCount = maxCount
                    ))
                } else {
                    Timber.e("BackupDataService: Failed to create auto backup file")
                }
            }
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(BackupDataService::class.java)
    }

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationBuilder = notificationsCenter.showNotification(
            channelId = channelId,
            channelName = channelName,
            notificationId = notificationId
        )
        startForeground(notificationId, notificationBuilder.build())
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val intentData = IntentData(intent)

        job = CoroutineScope(Dispatchers.IO).launch {
            tryAsResponse {
                backupData(intentData.uri, intentData.backupImages, intentData.backupSettings, intentData.backupPlugins)
            }.onError {
                Timber.e(it.exception)
            }

            // If auto-backup, run rotation after successful backup
            if (intentData.isAutoBackup && intentData.directoryUri.isNotEmpty()) {
                try {
                    rotateAutoBackups(intentData.directoryUri, intentData.maxCount)
                } catch (e: Exception) {
                    Timber.e(e, "BackupDataService: Failed to rotate auto backups")
                }
            }

            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Remove old auto-backup files when count exceeds maxCount.
     * Keeps only the [maxCount] most recent files.
     */
    private suspend fun rotateAutoBackups(directoryUri: String, maxCount: Int) = withContext(Dispatchers.IO) {
        if (maxCount <= 0) return@withContext
        Timber.d("BackupDataService: Rotating auto backups, maxCount=$maxCount")

        val treeUri = Uri.parse(directoryUri)
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val backupFiles = mutableListOf<Pair<String, Long>>() // (documentId, lastModified)

        try {
            contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docIdChild = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    val mimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    )
                    val displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    )
                    val lastModified = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    )

                    if (displayName.startsWith(AUTO_BACKUP_PREFIX) && displayName.endsWith(".zip")) {
                        backupFiles.add(docIdChild to lastModified)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "BackupDataService: Failed to list auto backup files")
            return@withContext
        }

        // Sort by last modified ascending (oldest first)
        backupFiles.sortBy { it.second }

        Timber.d("BackupDataService: Found ${backupFiles.size} auto backup files (max $maxCount)")

        // Delete oldest files if count > maxCount
        if (backupFiles.size > maxCount) {
            val toDelete = backupFiles.size - maxCount
            for (i in 0 until toDelete) {
                val docIdToDelete = backupFiles[i].first
                try {
                    val deleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docIdToDelete)
                    if (DocumentsContract.deleteDocument(contentResolver, deleteUri)) {
                        Timber.d("BackupDataService: Deleted old backup $docIdToDelete")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "BackupDataService: Failed to delete old backup $docIdToDelete")
                }
            }
        }
    }

    /**
     * Backup data function. Backups the library and images data given an uri.
     *
     * IMPORTANT: Before backing up, we:
     * 1. Remove all non-library data (books with inLibrary=false and their orphan data)
     * 2. Run VACUUM to shrink the database file
     *
     * This ensures the backup only contains books that are actually in the library.
     */
    private suspend fun backupData(uri: Uri, backupImages: Boolean, backupSettings: Boolean = true, backupPlugins: Boolean = true) = withContext(Dispatchers.IO) {

        notificationsCenter.showNotification(
            notificationId = notificationId,
            channelName = channelName,
            channelId = channelId
        ) {
            title = getString(R.string.backup)
            text = getString(R.string.creating_backup)
            setProgress(100, 0, true)
        }

        // Step 1: Clean up non-library data before backup
        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            text = getString(R.string.cleaning_database)
        }

        try {
            Timber.d("BackupDataService: Cleaning non-library data before backup")
            appRepository.settings.clearNonLibraryData()
            Timber.d("BackupDataService: Running VACUUM")
            appRepository.vacuum()
            Timber.d("BackupDataService: Database ready for backup")
        } catch (e: Exception) {
            Timber.e(e, "BackupDataService: Failed to clean database before backup, continuing anyway")
        }

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            val zip = ZipOutputStream(outputStream)

            notificationsCenter.modifyNotification(
                notificationBuilder,
                notificationId = notificationId
            ) {
                text = getString(R.string.copying_database)
            }

            // Save database — now contains only library books
            run {
                val entry = ZipEntry("database.sqlite3")
                val file = this@BackupDataService.getDatabasePath(appDatabase.name)
                entry.method = ZipOutputStream.DEFLATED
                file.inputStream().use {
                    zip.putNextEntry(entry)
                    it.copyTo(zip)
                }
                Timber.d("BackupDataService: Database backed up (${file.length()} bytes)")
            }

            // Save settings (API keys + categories)
            if (backupSettings) {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.backup_saving_settings)
                }

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
                            appPreferences.TRANSLATION_PROMPT_PRESETS.value.map { (name, prompt) ->
                                org.json.JSONObject().apply {
                                    put("name", name)
                                    put("prompt", prompt)
                                }
                            }
                        ))
                        put("TRANSLATION_NOVEL_PROMPTS", org.json.JSONObject().apply {
                            appPreferences.TRANSLATION_NOVEL_PROMPTS.value.forEach { (url, data) ->
                                put(url, org.json.JSONObject().apply {
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
                    Timber.d("BackupDataService: Settings backed up")
                }
            } else {
                Timber.d("BackupDataService: Settings excluded from backup")
            }

            // Save lua extension scripts
            if (backupPlugins) {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.copying_plugins)
                }

                run {
                    val luaDir = File(this@BackupDataService.filesDir, "lua_extensions")
                    if (luaDir.exists() && luaDir.isDirectory) {
                        luaDir.listFiles()?.filter { it.isFile && it.extension == "lua" }?.forEach { file ->
                            val entry = ZipEntry("lua_extensions/${file.name}")
                            entry.method = ZipOutputStream.DEFLATED
                            file.inputStream().use {
                                zip.putNextEntry(entry)
                                it.copyTo(zip)
                            }
                        }
                        Timber.d("BackupDataService: Lua extensions backed up (${luaDir.listFiles()?.size} files)")
                    } else {
                        Timber.d("BackupDataService: No lua_extensions directory found")
                    }
                }
            } else {
                Timber.d("BackupDataService: Plugins excluded from backup")
            }

            // Save books extra data (like images) — only for library books
            if (backupImages) {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.copying_images)
                }

                // Get library book folder names to filter images
                val libraryBooks = appRepository.libraryBooks.getAllInLibrary()
                val libraryFolderNames = libraryBooks
                    .map { book ->
                        // Extract folder name from URL (same logic as AppFileResolver)
                        book.url.substringAfterLast("/").substringBefore("?")
                    }
                    .toSet()

                val basePath = appRepository.settings.folderBooks.toPath().parent
                appRepository.settings.folderBooks.walkBottomUp()
                    .filterNot { it.isDirectory }
                    .filter { file ->
                        // Only include images from library book folders
                        val relativePath = basePath.relativize(file.toPath()).toString()
                        // "books" is the root folder, second segment is the book folder
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
                    }
            }

            zip.closeQuietly()
            notificationsCenter.showNotification(
                notificationId = "Backup saved success".hashCode(),
                channelId = channelId,
                channelName = channelName
            ) {
                title = getString(R.string.backup_saved)
            }

            // Update last auto-backup timestamp
            appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.value = System.currentTimeMillis()
        } ?: notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            removeProgressBar()
            text = getString(R.string.failed_to_make_backup)
        }
    }
}