package my.noveldokusha.tooling.backup_restore

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.coreui.states.removeProgressBar
import my.noveldokusha.coreui.states.text
import my.noveldokusha.coreui.states.title
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.NovelPromptData
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.ChapterBodyRepository
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.core.utils.Extra_Boolean
import my.noveldokusha.core.utils.Extra_Uri
import my.noveldokusha.core.utils.isServiceRunning
import my.noveldokusha.feature.local_database.AppDatabase
import okhttp3.internal.closeQuietly
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

@AndroidEntryPoint
class RestoreDataService : Service() {
    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var appFileResolver: AppFileResolver

    @Inject
    lateinit var notificationsCenter: NotificationsCenter

    @Inject
    lateinit var appCoroutineScope: AppCoroutineScope

    @Inject
    lateinit var downloaderRepository: DownloaderRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    private class IntentData : Intent {
        var uri by Extra_Uri()
        var overwritePlugins by Extra_Boolean()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, uri: Uri, overwritePlugins: Boolean) : super(ctx, RestoreDataService::class.java) {
            this.uri = uri
            this.overwritePlugins = overwritePlugins
        }
    }

    private fun getHeapUsedPercent(): Double {
        val max = Runtime.getRuntime().maxMemory()
        val used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        return used.toDouble() / max * 100
    }

    private suspend fun <T> processInChunks(
        total: Int,
        initialChunkSize: Int,
        label: String,
        fetchChunk: suspend (limit: Int, offset: Int) -> List<T>,
        processChunk: suspend (List<T>) -> Unit,
    ) {
        var chunkSize = initialChunkSize
        var offset = 0
        while (offset < total) {
            val actualSize = minOf(chunkSize, total - offset)
            val chunk = fetchChunk(actualSize, offset)
            processChunk(chunk)
            offset += chunk.size

            val usedPercent = getHeapUsedPercent()
            when {
                usedPercent > 85 -> {
                    System.gc()
                    delay(500)
                    val newPercent = getHeapUsedPercent()
                    if (newPercent > 85 && chunkSize > 50) {
                        chunkSize = maxOf(50, chunkSize / 2)
                        Timber.w("processInChunks($label): heap ${"%.1f".format(newPercent)}%, chunk reduced to $chunkSize")
                    }
                }
                usedPercent > 70 -> {
                    System.gc()
                    delay(200)
                }
            }
        }
    }

    companion object {
        fun start(ctx: Context, uri: Uri, overwritePlugins: Boolean = true) {
            if (!isRunning(ctx))
                ContextCompat.startForegroundService(ctx, IntentData(ctx, uri, overwritePlugins))
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(RestoreDataService::class.java)
    }

    private val channelName by lazy { getString(R.string.notification_channel_name_restore_backup) }
    private val channelId = "Restore backup"
    private val notificationId = channelId.hashCode()

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationBuilder = notificationsCenter.showNotification(
            notificationId = notificationId,
            channelId = channelId,
            channelName = channelName
        )
        startForeground(notificationId, notificationBuilder.build())
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Timber.e("RestoreDataService: intent is null")
            return START_NOT_STICKY
        }
        val intentData = IntentData(intent)

        if (job?.isActive == true) {
            Timber.w("RestoreDataService: job already active")
            return START_NOT_STICKY
        }
        job = CoroutineScope(Dispatchers.IO).launch {
            tryAsResponse {
                Timber.d("RestoreDataService: Starting restore from URI: ${intentData.uri}")
                restoreData(intentData.uri, intentData.overwritePlugins)
                appRepository.eventDataRestored.emit(Unit)
                Timber.d("RestoreDataService: Restore completed successfully")
            }.onError {
                Timber.e(it.exception, "RestoreDataService: Restore failed with error")
                notificationsCenter.showNotification(
                    channelName = channelName,
                    channelId = channelId,
                    notificationId = "Backup restore failure".hashCode()
                ) {
                    removeProgressBar()
                    title = getString(R.string.failed_to_restore_cant_access_file)
                    text = "Error: ${it.exception.message?.take(100) ?: "Unknown error"}"
                }
            }
            stopSelf(startId)
        }
        return START_STICKY
    }

    /**
     * Restore data function. Restores the library, images, plugins, and settings.
     */
    private suspend fun restoreData(uri: Uri, overwritePlugins: Boolean = true) = withContext(Dispatchers.IO) {

        Timber.d("restoreData: Starting restore process (overwritePlugins=$overwritePlugins)")
        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            title = getString(R.string.restore_data)
            text = getString(R.string.loading_data)
            setProgress(100, 0, true)
        }

        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Timber.e("restoreData: Failed to open input stream from URI: $uri")
            notificationsCenter.showNotification(
                channelName = channelName,
                channelId = channelId,
                notificationId = "Backup restore failure".hashCode()
            ) {
                text = getString(R.string.failed_to_restore_cant_access_file)
            }
            return@withContext
        }

        // Read ZIP header to verify file format
        val bufferedStream = inputStream.buffered()
        bufferedStream.mark(8192)
        val header = ByteArray(4)
        val bytesRead = try { bufferedStream.read(header) } catch (e: Exception) { -1 }

        if (bytesRead == 4) {
            val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    (header[2] == 0x03.toByte() || header[2] == 0x05.toByte())
            if (!isZip) {
                Timber.e("restoreData: File is not a valid ZIP file")
                notificationsCenter.showNotification(
                    channelName = channelName,
                    channelId = channelId,
                    notificationId = "Backup restore failure - invalid format".hashCode()
                ) {
                    removeProgressBar()
                    text = "Invalid backup file format. Expected ZIP file."
                }
                return@withContext
            }
        }

        try { bufferedStream.reset() } catch (e: Exception) {
            Timber.e(e, "restoreData: Failed to reset stream")
        }

        suspend fun mergeToDatabase(dbInputStream: InputStream) {
            tryAsResponse {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.loading_database)
                }

                val tempDbFile = File(context.cacheDir, "temp_restore_database.db")
                try {
                    tempDbFile.outputStream().use { output -> dbInputStream.copyTo(output) }
                    Timber.d("mergeToDatabase: Wrote database to temp file, size: ${tempDbFile.length()}")
                } catch (e: Exception) {
                    Timber.e(e, "mergeToDatabase: Failed to write temp database file")
                    throw e
                }

                val backupDatabase = object {
                    val newDatabase = try {
                        AppDatabase.createRoom(context, tempDbFile.absolutePath)
                    } catch (e: Exception) {
                        Timber.e(e, "mergeToDatabase: Failed to open Room database")
                        tempDbFile.delete()
                        throw e
                    }
                    val bookChapters = BookChaptersRepository(chapterDao = newDatabase.chapterDao())
                    val chapterBody = ChapterBodyRepository(
                        chapterBodyDao = newDatabase.chapterBodyDao(),
                        appDatabase = newDatabase,
                        chapterTranslationDao = newDatabase.chapterTranslationDao(),
                        bookChaptersRepository = bookChapters,
                        downloaderRepository = downloaderRepository
                    )
                    val libraryBooks = LibraryBooksRepository(
                        libraryDao = newDatabase.libraryDao(),
                        chapterDao = newDatabase.chapterDao(),
                        chapterBodyDao = newDatabase.chapterBodyDao(),
                        chapterTranslationDao = newDatabase.chapterTranslationDao(),
                        appDatabase = newDatabase,
                        context = context,
                        appFileResolver = appFileResolver,
                        appCoroutineScope = appCoroutineScope
                    )
                    fun close() = newDatabase.closeDatabase()
                    fun delete() {
                        try {
                            tempDbFile.delete()
                            File(tempDbFile.absolutePath + "-shm").delete()
                            File(tempDbFile.absolutePath + "-wal").delete()
                        } catch (e: Exception) {
                            Timber.e(e, "mergeToDatabase: Error cleaning up temp database")
                        }
                    }
                }

                // Restore library books — chunked
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.adding_books)
                }
                val restoredBookUrls = mutableSetOf<String>()
                val totalBooks = backupDatabase.libraryBooks.count()
                Timber.d("mergeToDatabase: Backup contains $totalBooks total books")
                processInChunks(
                    total = totalBooks, initialChunkSize = 500, label = "books",
                    fetchChunk = { limit, offset -> backupDatabase.libraryBooks.getChunk(limit, offset) },
                    processChunk = { chunk ->
                        val valid = chunk
                            .filter { it.inLibrary }
                            .filter { it.url.matches("""^(https?|local)://.*""".toRegex()) }
                        if (valid.isNotEmpty()) {
                            try {
                                appRepository.libraryBooks.insertReplace(valid)
                            } catch (e: Exception) {
                                Timber.e(e, "mergeToDatabase: Bulk book insert failed, trying individual")
                                valid.forEach { book ->
                                    try { appRepository.libraryBooks.insertReplace(listOf(book)) }
                                    catch (bookError: Exception) {
                                        Timber.w(bookError, "Failed to insert book: ${book.title}")
                                    }
                                }
                            }
                            valid.forEach { restoredBookUrls.add(it.url) }
                        }
                    }
                )

                // Restore chapters — chunked
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.adding_chapters)
                }
                val restoredChapterUrls = mutableSetOf<String>()
                val totalChapters = backupDatabase.bookChapters.count()
                Timber.d("mergeToDatabase: Backup contains $totalChapters total chapters")
                processInChunks(
                    total = totalChapters, initialChunkSize = 5000, label = "chapters",
                    fetchChunk = { limit, offset -> backupDatabase.bookChapters.getChunk(limit, offset) },
                    processChunk = { chunk ->
                        val valid = chunk.filter { chapter ->
                            chapter.bookUrl in restoredBookUrls &&
                            chapter.url.matches("""^(https?|local)://.*""".toRegex())
                        }
                        if (valid.isNotEmpty()) {
                            try {
                                appRepository.bookChapters.insert(valid)
                            } catch (e: Exception) {
                                valid.forEach { chapter ->
                                    try { appRepository.bookChapters.insert(listOf(chapter)) }
                                    catch (chapterError: Exception) {
                                        Timber.w(chapterError, "Failed to insert chapter: ${chapter.title}")
                                    }
                                }
                            }
                            valid.forEach { restoredChapterUrls.add(it.url) }
                        }
                    }
                )

                // Restore chapter bodies — chunked
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.adding_chapters_text)
                }
                val totalBodies = backupDatabase.chapterBody.count()
                Timber.d("mergeToDatabase: Backup contains $totalBodies total chapter bodies")
                processInChunks(
                    total = totalBodies, initialChunkSize = 500, label = "bodies",
                    fetchChunk = { limit, offset -> backupDatabase.chapterBody.getChunk(limit, offset) },
                    processChunk = { chunk ->
                        val valid = chunk.filter { it.url in restoredChapterUrls }
                        if (valid.isNotEmpty()) {
                            try {
                                appRepository.chapterBody.insertReplace(valid)
                            } catch (e: Exception) {
                                valid.forEach { body ->
                                    try { appRepository.chapterBody.insertReplace(listOf(body)) }
                                    catch (bodyError: Exception) {
                                        Timber.w(bodyError, "Failed to insert chapter body")
                                    }
                                }
                            }
                        }
                    }
                )

                // Restore chapter translations — chunked
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.adding_chapters_text)
                }
                val totalTranslations = backupDatabase.newDatabase.chapterTranslationDao().count()
                Timber.d("mergeToDatabase: Backup contains $totalTranslations total chapter translations")
                processInChunks(
                    total = totalTranslations, initialChunkSize = 500, label = "translations",
                    fetchChunk = { limit, offset ->
                        backupDatabase.newDatabase.chapterTranslationDao().getChunk(limit, offset)
                    },
                    processChunk = { chunk ->
                        val valid = chunk.filter { it.chapterUrl in restoredChapterUrls }
                        if (valid.isNotEmpty()) {
                            try {
                                appDatabase.chapterTranslationDao().insertReplace(valid)
                            } catch (e: Exception) {
                                valid.forEach { translation ->
                                    try { appDatabase.chapterTranslationDao().insertReplace(translation) }
                                    catch (translationError: Exception) {
                                        Timber.w(translationError, "Failed to insert chapter translation")
                                    }
                                }
                            }
                        }
                    }
                )

                // Restore extensions (plugins)
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.restoring_plugins)
                }

                val backupExtensions = backupDatabase.newDatabase.extensionDao().getAll()
                if (backupExtensions.isNotEmpty()) {
                    Timber.d("mergeToDatabase: Found ${backupExtensions.size} extensions in backup, overwritePlugins=$overwritePlugins")
                    if (overwritePlugins) {
                        backupExtensions.forEach { backupExt ->
                            val current = appDatabase.extensionDao().get(backupExt.id)
                            if (current == null || compareVersions(backupExt.version, current.version) > 0) {
                                appDatabase.extensionDao().insert(backupExt)
                            }
                        }
                        Timber.d("mergeToDatabase: Processed ${backupExtensions.size} extensions with version check")
                    } else {
                        Timber.d("mergeToDatabase: Skipping all extensions (overwritePlugins=false)")
                    }
                } else {
                    Timber.d("mergeToDatabase: No extensions in backup")
                }

                backupDatabase.close()
                backupDatabase.delete()
                Timber.d("mergeToDatabase: Database merge completed successfully")

            }.onError {
                Timber.e(it.exception, "mergeToDatabase: Failed to merge database")
                notificationsCenter.showNotification(
                    channelName = channelName,
                    channelId = channelId,
                    notificationId = "Backup restore failure - invalid database".hashCode()
                ) {
                    removeProgressBar()
                    text = getString(R.string.failed_to_restore_invalid_backup_database) +
                            ": ${it.exception.message?.take(100)}"
                }
            }.onSuccess {
                notificationsCenter.showNotification(
                    channelName = channelName,
                    channelId = channelId,
                    notificationId = "Backup restore success".hashCode()
                ) {
                    title = getString(R.string.backup_restored)
                }
            }
        }

        suspend fun mergeToSettings(settingsInputStream: InputStream) {
            tryAsResponse {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.restoring_settings)
                }

                val settingsString = settingsInputStream.bufferedReader().readText()
                val settingsJson = JSONObject(settingsString)

                // Restore API keys
                if (settingsJson.has("TRANSLATION_GOOGLE_PA_API_KEYS")) {
                    val backupValue = settingsJson.getString("TRANSLATION_GOOGLE_PA_API_KEYS")
                    if (backupValue.isNotEmpty()) {
                        val currentLines = appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value
                            .split("\n", "\r\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toMutableSet()
                        val backupLines = backupValue.split("\n", "\r\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        currentLines.addAll(backupLines)
                        appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value = currentLines.joinToString("\n")
                        Timber.d("mergeToSettings: Restored TRANSLATION_GOOGLE_PA_API_KEYS (merged ${backupLines.size} keys)")
                    }
                }

                if (settingsJson.has("TRANSLATION_GEMINI_API_KEY")) {
                    val backupValue = settingsJson.getString("TRANSLATION_GEMINI_API_KEY")
                    if (backupValue.isNotEmpty()) {
                        appPreferences.TRANSLATION_GEMINI_API_KEY.value = backupValue
                        Timber.d("mergeToSettings: Restored TRANSLATION_GEMINI_API_KEY")
                    }
                }

                if (settingsJson.has("TRANSLATION_GEMINI_MODEL")) {
                    val backupValue = settingsJson.getString("TRANSLATION_GEMINI_MODEL")
                    if (backupValue.isNotEmpty()) {
                        appPreferences.TRANSLATION_GEMINI_MODEL.value = backupValue
                        Timber.d("mergeToSettings: Restored TRANSLATION_GEMINI_MODEL")
                    }
                }

                if (settingsJson.has("TRANSLATION_OPENAI_BASE_URL")) {
                    val backupValue = settingsJson.getString("TRANSLATION_OPENAI_BASE_URL")
                    if (backupValue.isNotEmpty()) {
                        appPreferences.TRANSLATION_OPENAI_BASE_URL.value = backupValue
                        Timber.d("mergeToSettings: Restored TRANSLATION_OPENAI_BASE_URL")
                    }
                }

                if (settingsJson.has("TRANSLATION_OPENAI_API_KEYS")) {
                    val backupValue = settingsJson.getString("TRANSLATION_OPENAI_API_KEYS")
                    if (backupValue.isNotEmpty()) {
                        val currentLines = appPreferences.TRANSLATION_OPENAI_API_KEYS.value
                            .split("\n", "\r\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toMutableSet()
                        val backupLines = backupValue.split("\n", "\r\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        currentLines.addAll(backupLines)
                        appPreferences.TRANSLATION_OPENAI_API_KEYS.value = currentLines.joinToString("\n")
                        Timber.d("mergeToSettings: Restored TRANSLATION_OPENAI_API_KEYS (merged ${backupLines.size} keys)")
                    }
                }

                if (settingsJson.has("TRANSLATION_OPENAI_MODEL")) {
                    val backupValue = settingsJson.getString("TRANSLATION_OPENAI_MODEL")
                    if (backupValue.isNotEmpty()) {
                        appPreferences.TRANSLATION_OPENAI_MODEL.value = backupValue
                        Timber.d("mergeToSettings: Restored TRANSLATION_OPENAI_MODEL")
                    }
                }

                // Restore categories
                if (settingsJson.has("LIBRARY_CUSTOM_CATEGORIES")) {
                    val categoriesArray = settingsJson.getJSONArray("LIBRARY_CUSTOM_CATEGORIES")
                    val categoriesList = (0 until categoriesArray.length()).map { categoriesArray.getString(it) }
                    appPreferences.LIBRARY_CUSTOM_CATEGORIES.value = categoriesList
                    Timber.d("mergeToSettings: Restored ${categoriesList.size} categories")
                }

                // Restore system prompt
                if (settingsJson.has("TRANSLATION_ACTIVE_SYSTEM_PROMPT")) {
                    val backupValue = settingsJson.getString("TRANSLATION_ACTIVE_SYSTEM_PROMPT")
                    if (backupValue.isNotEmpty()) {
                        appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value = backupValue
                        Timber.d("mergeToSettings: Restored TRANSLATION_ACTIVE_SYSTEM_PROMPT")
                    }
                }

                // Restore prompt presets
                if (settingsJson.has("TRANSLATION_PROMPT_PRESETS")) {
                    val presetsArray = settingsJson.getJSONArray("TRANSLATION_PROMPT_PRESETS")
                    val presetsList = (0 until presetsArray.length()).map { i ->
                        val obj = presetsArray.getJSONObject(i)
                        obj.getString("name") to obj.getString("prompt")
                    }
                    appPreferences.TRANSLATION_PROMPT_PRESETS.value = presetsList
                    Timber.d("mergeToSettings: Restored ${presetsList.size} prompt presets")
                }

                // Restore per-novel prompts (старый формат: строка, новый: {"title":"...","prompt":"..."})
                if (settingsJson.has("TRANSLATION_NOVEL_PROMPTS")) {
                    val promptsObj = settingsJson.getJSONObject("TRANSLATION_NOVEL_PROMPTS")
                    val promptsMap = mutableMapOf<String, NovelPromptData>()
                    for (key in promptsObj.keys()) {
                        val value = promptsObj.get(key)
                        promptsMap[key] = when (value) {
                            is String -> NovelPromptData(prompt = value)
                            is JSONObject -> NovelPromptData(
                                title = value.optString("title", ""),
                                prompt = value.optString("prompt", ""),
                                appendMode = value.optBoolean("appendMode", false),
                            )
                            else -> NovelPromptData(prompt = value.toString())
                        }
                    }
                    appPreferences.TRANSLATION_NOVEL_PROMPTS.value = promptsMap
                    Timber.d("mergeToSettings: Restored ${promptsMap.size} novel prompts")
                }

                Timber.d("mergeToSettings: Settings merge completed")

            }.onError {
                Timber.e(it.exception, "mergeToSettings: Failed to merge settings")
            }
        }

        fun mergeToBookFolder(entry: ZipEntry, entryInputStream: InputStream) {
            try {
                val file = File(appRepository.settings.folderBooks.parentFile, entry.name)
                if (file.isDirectory) return
                file.parentFile?.mkdirs()
                if (file.parentFile?.exists() != true) {
                    Timber.w("mergeToBookFolder: Cannot create parent dir for ${entry.name}")
                    return
                }
                file.outputStream().use { output ->
                    entryInputStream.copyTo(output)
                }
            } catch (e: Exception) {
                Timber.e(e, "mergeToBookFolder: Error processing entry: ${entry.name}")
            }
        }

        suspend fun mergeToLuaExtensions(entry: ZipEntry, entryInputStream: InputStream) {
            try {
                val luaDir = File(context.filesDir, "lua_extensions")
                luaDir.mkdirs()
                val fileName = entry.name.removePrefix("lua_extensions/")
                if (fileName.isNotEmpty() && !fileName.contains("/")) {
                    val file = File(luaDir, fileName)
                    file.outputStream().use { output ->
                        entryInputStream.copyTo(output)
                    }
                    Timber.d("mergeToLuaExtensions: Restored $fileName")
                }
            } catch (e: Exception) {
                Timber.e(e, "mergeToLuaExtensions: Error processing entry ${entry.name}")
            }
        }

        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            text = getString(R.string.adding_images)
        }

        try {
            ZipInputStream(bufferedStream).use { zipStream ->
                generateSequence {
                    try { zipStream.nextEntry } catch (e: Exception) { null }
                }
                    .filterNotNull()
                    .filterNot { it.isDirectory }
                    .forEach { entry ->
                        try {
                            when {
                                entry.name == "database.sqlite3" -> mergeToDatabase(zipStream)
                                entry.name == "settings.json" -> mergeToSettings(zipStream)
                                entry.name.startsWith("lua_extensions/") && overwritePlugins -> mergeToLuaExtensions(entry, zipStream)
                                entry.name.startsWith("lua_extensions/") -> Timber.d("restoreData: Skipping plugin (overwritePlugins=false): ${entry.name}")
                                entry.name.startsWith("books/") -> mergeToBookFolder(entry, zipStream)
                                else -> Timber.w("restoreData: Skipping unknown entry: ${entry.name}")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "restoreData: Error processing entry ${entry.name}")
                        }
                        zipStream.closeEntry()
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "restoreData: Failed to read ZIP file")
            notificationsCenter.showNotification(
                channelName = channelName,
                channelId = channelId,
                notificationId = "Backup restore failure - invalid zip".hashCode()
            ) {
                removeProgressBar()
                text = "Failed to read backup file: ${e.message}"
            }
            return@withContext
        }

        inputStream.closeQuietly()

        // Clear source cache to force LuaSourceProvider to reload from restored lua_extensions/
        try {
            val sourceCacheFile = File(context.filesDir, "source_cache.json")
            if (sourceCacheFile.exists()) {
                sourceCacheFile.delete()
                Timber.d("restoreData: Cleared source_cache.json")
            }
        } catch (e: Exception) {
            Timber.e(e, "restoreData: Failed to clear source cache")
        }

        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            removeProgressBar()
            text = getString(R.string.data_restored)
        }

        // Restart Activity to apply all changes (sources, settings, preferences)
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Timber.d("restoreData: Restarting Activity to apply changes")
            }
        } catch (e: Exception) {
            Timber.e(e, "restoreData: Failed to restart Activity")
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val cmp = (parts1.getOrElse(i) { 0 }).compareTo(parts2.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}