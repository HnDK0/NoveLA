package my.noveldokusha.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.feature.local_database.DAOs.DownloadTaskDao
import my.noveldokusha.feature.local_database.tables.DownloadTaskEntity
import my.noveldokusha.text_translator.domain.TranslationManager
import org.json.JSONArray
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Состояние одной задачи на скачивание.
 */
data class DownloadTaskState(
    val bookTitle: String,
    val bookUrl: String,
    val chapterUrls: List<String>,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val isCompleted: Boolean = false,
    val errorCount: Int = 0,
    val successCount: Int = 0,
    val consecutiveErrors: Int = 0,
    val skippedCount: Int = 0,
    val translationErrorCount: Int = 0,
) {
    val progressText: String
        get() = "$currentIndex / $totalCount"
}

/**
 * Менеджер очереди загрузок глав.
 * Позволяет ставить в очередь, приостанавливать, возобновлять и отменять загрузки.
 * Каждая задача обрабатывается последовательно с задержкой между главами.
 * Прогресс отображается в notification в шторке уведомлений.
 *
 * Per-domain throttling: на один домен может выполняться только 1 задача одновременно.
 * Это предотвращает бан источника из-за слишком частых запросов.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val appRepository: my.noveldokusha.data.AppRepository,
    private val chapterBodyRepository: ChapterBodyRepository,
    private val translationManager: TranslationManager,
    private val chapterTranslationDao: my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao,
    private val notificationsCenter: NotificationsCenter,
    private val downloadTaskDao: DownloadTaskDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tasks = MutableStateFlow<List<DownloadTaskState>>(emptyList())
    val tasks: StateFlow<List<DownloadTaskState>> = _tasks.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val notificationBuilders = mutableMapOf<String, NotificationCompat.Builder>()

    // Per-domain throttling: 1 задача на домен
    private val domainSemaphores = ConcurrentHashMap<String, Semaphore>()

    companion object {
        private const val TAG = "DownloadManager"
        private const val CHANNEL_ID = "chapter_downloads"
        private const val CHANNEL_NAME = "Chapter Downloads"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        // Восстанавливаем незавершённые задачи из БД при старте
        scope.launch {
            restoreTasksFromDatabase()
        }
    }

    /**
     * Восстанавливает незавершённые задачи из БД после перезапуска приложения.
     */
    private suspend fun restoreTasksFromDatabase() {
        val savedTasks = withContext(Dispatchers.IO) {
            downloadTaskDao.getAll()
        }
        for (entity in savedTasks) {
            if (entity.isCompleted || entity.isCancelled) {
                // Очищаем завершённые задачи
                withContext(Dispatchers.IO) {
                    downloadTaskDao.delete(entity.bookUrl)
                }
                continue
            }
            val chapterUrls = parseChapterUrlsJson(entity.chapterUrlsJson)
            val task = DownloadTaskState(
                bookTitle = entity.bookTitle,
                bookUrl = entity.bookUrl,
                chapterUrls = chapterUrls,
                currentIndex = entity.currentIndex,
                totalCount = entity.totalCount,
                isPaused = entity.isPaused,
                isCancelled = entity.isCancelled,
                isCompleted = entity.isCompleted,
                errorCount = entity.errorCount,
                successCount = entity.successCount,
                consecutiveErrors = entity.consecutiveErrors,
                skippedCount = entity.skippedCount,
                translationErrorCount = entity.translationErrorCount,
            )
            _tasks.value = _tasks.value + task
            if (!task.isPaused && !task.isCompleted) {
                startTask(task)
            } else if (task.isPaused) {
                // Показываем notification для приостановленных задач,
                // чтобы пользователь мог нажать Resume
                showNotification(task)
            }
        }
    }

    private fun toChapterUrlsJson(urls: List<String>): String {
        return JSONArray(urls).toString()
    }

    private fun parseChapterUrlsJson(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Сохраняет текущее состояние задачи в БД.
     */
    private suspend fun saveTaskToDatabase(task: DownloadTaskState) {
        withContext(Dispatchers.IO) {
            downloadTaskDao.insert(
                DownloadTaskEntity(
                    bookUrl = task.bookUrl,
                    bookTitle = task.bookTitle,
                    chapterUrlsJson = toChapterUrlsJson(task.chapterUrls),
                    currentIndex = task.currentIndex,
                    totalCount = task.totalCount,
                    isPaused = task.isPaused,
                    isCancelled = task.isCancelled,
                    isCompleted = task.isCompleted,
                    errorCount = task.errorCount,
                    successCount = task.successCount,
                    consecutiveErrors = task.consecutiveErrors,
                    skippedCount = task.skippedCount,
                    translationErrorCount = task.translationErrorCount,
                )
            )
        }
    }

    /**
     * Извлекает домен из URL. Для локальных URL возвращает "local".
     */
    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: "local"
        } catch (_: Exception) {
            "local"
        }
    }

    /**
     * Добавить задачу на скачивание.
     * Если книга с таким bookUrl уже есть в очереди — главы добавляются к существующей задаче.
     */
    fun enqueue(
        bookTitle: String,
        bookUrl: String,
        chapterUrls: List<String>,
    ) {
        // Фильтруем дубликаты внутри переданного списка
        val uniqueUrls = chapterUrls.distinct()
        if (uniqueUrls.isEmpty()) return

        // Фильтруем уже скачанные главы в корутине
        scope.launch {
            val notDownloadedUrls = withContext(Dispatchers.IO) {
                uniqueUrls.filter { url ->
                    chapterBodyRepository.getCachedBody(url) == null
                }
            }

            // Ищем задачу по bookUrl, даже если она завершена — чтобы перезапустить
            val existingIndex = _tasks.value.indexOfFirst { it.bookUrl == bookUrl }
            if (existingIndex >= 0) {
                val existing = _tasks.value[existingIndex]

                // Если задача отменена или завершена — удаляем её и создаём новую с нуля
                if (existing.isCancelled || existing.isCompleted) {
                    _tasks.value = _tasks.value.toMutableList().also { it.removeAt(existingIndex) }
                    // Удаляем старый notification
                    notificationsCenter.close(getNotificationId(bookUrl))
                    notificationBuilders.remove(bookUrl)
                    // Удаляем из БД
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            downloadTaskDao.delete(bookUrl)
                        }
                    }
                    // Падаем в код создания новой задачи ниже
                } else {
                    val newUrls = notDownloadedUrls.filter { it !in existing.chapterUrls }
                    if (newUrls.isEmpty()) {
                        // Все главы уже есть в задаче. Если задача на паузе — снимаем с паузы и перезапускаем.
                        if (existing.isPaused) {
                            updateTask(bookUrl) { it.copy(isPaused = false) }
                            val updated = _tasks.value.find { it.bookUrl == bookUrl } ?: return@launch
                            showNotification(updated)
                            startTask(updated)
                        }
                        return@launch
                    }
                    val updated = existing.copy(
                        chapterUrls = existing.chapterUrls + newUrls,
                        totalCount = existing.totalCount + newUrls.size,
                        // Не сбрасываем currentIndex — продолжаем с того места, где остановились
                        isCompleted = false,
                        isCancelled = false,
                        isPaused = false,
                        errorCount = 0,
                        successCount = 0,
                        translationErrorCount = 0,
                    )
                    _tasks.value = _tasks.value.toMutableList().also { it[existingIndex] = updated }
                    showNotification(updated)
                    startTask(updated)
                    return@launch
                }
            }

            if (notDownloadedUrls.isEmpty()) return@launch

            val task = DownloadTaskState(
                bookTitle = bookTitle,
                bookUrl = bookUrl,
                chapterUrls = notDownloadedUrls,
                totalCount = notDownloadedUrls.size,
            )
            _tasks.value = _tasks.value + task
            // Сохраняем новую задачу в БД
            saveTaskToDatabase(task)
            showNotification(task)
            startTask(task)
        }
    }

    fun pause(bookUrl: String) {
        // Отменяем job, чтобы освободить семафор домена
        activeJobs[bookUrl]?.cancel()
        activeJobs.remove(bookUrl)
        releaseDomainPermit(bookUrl)
        updateTask(bookUrl) { it.copy(isPaused = true) }
        val task = _tasks.value.find { it.bookUrl == bookUrl } ?: return
        // Пересоздаём notification целиком, чтобы обновить actions (Pause -> Resume)
        showNotification(task)
    }

    fun resume(bookUrl: String) {
        val task = _tasks.value.find { it.bookUrl == bookUrl } ?: return
        if (!task.isPaused) return
        updateTask(bookUrl) { it.copy(isPaused = false) }
        val updated = _tasks.value.find { it.bookUrl == bookUrl } ?: return
        // Создаём новый job, который захватит семафор и продолжит загрузку
        startTask(updated)
        // Пересоздаём notification целиком, чтобы обновить actions (Resume -> Pause)
        showNotification(updated)
    }

    fun cancel(bookUrl: String) {
        activeJobs[bookUrl]?.cancel()
        activeJobs.remove(bookUrl)
        updateTask(bookUrl) { it.copy(isCancelled = true, isCompleted = true) }
        val task = _tasks.value.find { it.bookUrl == bookUrl } ?: return
        cancelNotification(task)
        releaseDomainPermit(task.bookUrl)
    }

    fun dismiss(bookUrl: String) {
        _tasks.value = _tasks.value.filter { it.bookUrl != bookUrl }
        notificationBuilders.remove(bookUrl)
        notificationsCenter.close(getNotificationId(bookUrl))
        // Удаляем задачу из БД
        scope.launch {
            withContext(Dispatchers.IO) {
                downloadTaskDao.delete(bookUrl)
            }
        }
    }

    /**
     * Захватывает permit для домена. Если permit занят — ждёт освобождения.
     */
    private suspend fun acquireDomainPermit(bookUrl: String) {
        val domain = extractDomain(bookUrl)
        val semaphore = domainSemaphores.computeIfAbsent(domain) { Semaphore(1) }
        android.util.Log.d(TAG, "acquireDomainPermit: waiting for domain $domain (bookUrl=$bookUrl)")
        semaphore.acquire()
        android.util.Log.d(TAG, "acquireDomainPermit: acquired for domain $domain (bookUrl=$bookUrl)")
    }

    /**
     * Освобождает permit для домена.
     */
    private fun releaseDomainPermit(bookUrl: String) {
        val domain = extractDomain(bookUrl)
        val semaphore = domainSemaphores[domain]
        if (semaphore != null) {
            semaphore.release()
            android.util.Log.d(TAG, "releaseDomainPermit: released for domain $domain (bookUrl=$bookUrl)")
        }
    }

    private fun startTask(task: DownloadTaskState) {
        if (activeJobs.containsKey(task.bookUrl)) return

        val job = scope.launch {
            // Показываем уведомление сразу после старта job, чтобы задача была видна в шторке,
            // даже если она будет ждать освобождения семафора домена
            showNotification(task)

            // Per-domain throttling: ждём, пока освободится слот для этого домена
            acquireDomainPermit(task.bookUrl)

            try {
                val delayMs = appPreferences.DOWNLOAD_DELAY_MS.value

                var i = _tasks.value.find { it.bookUrl == task.bookUrl }?.currentIndex ?: 0

                while (true) {
                    // Перечитываем актуальное состояние задачи на каждой итерации
                    val currentTask = _tasks.value.find { it.bookUrl == task.bookUrl } ?: return@launch

                    if (currentTask.isCancelled) return@launch
                    if (currentTask.isPaused) return@launch

                    if (i >= currentTask.chapterUrls.size) break

                    val chapterUrl = currentTask.chapterUrls[i]

                    updateTask(task.bookUrl) { it.copy(currentIndex = i + 1) }
                    val updated = _tasks.value.find { it.bookUrl == task.bookUrl } ?: return@launch
                    showNotification(updated)

                    // Пропускаем уже загруженные главы
                    val existingBody = chapterBodyRepository.getCachedBody(chapterUrl)
                    if (existingBody != null) {
                        android.util.Log.d(TAG, "startTask: chapter $chapterUrl already downloaded, skipping")
                        updateTask(task.bookUrl) {
                            it.copy(
                                successCount = it.successCount + 1,
                                skippedCount = it.skippedCount + 1,
                                consecutiveErrors = 0,
                            )
                        }
                        i++
                        if (i < currentTask.chapterUrls.size) delay(delayMs)
                        continue
                    }

                    // Загружаем главу с retry
                    // attempt 0: сразу, attempt 1: 60s, attempt 2: 300s (5 мин), attempt 3: 600s (10 мин)
                    var chapterBody: String? = null
                    var loadSuccess = false
                    for (attempt in 0 until 4) {
                        if (attempt > 0) {
                            val backoffMs = when (attempt) {
                                1 -> 60_000L
                                2 -> 300_000L
                                3 -> 600_000L
                                else -> 600_000L
                            }
                            android.util.Log.d(TAG, "startTask: retry $attempt for $chapterUrl, waiting ${backoffMs}ms")
                            delay(backoffMs)
                        }

                        val result = chapterBodyRepository.fetchBody(chapterUrl)
                        when (result) {
                            is my.noveldokusha.core.Response.Success -> {
                                chapterBody = result.data
                                if (chapterBody.isNullOrBlank()) {
                                    android.util.Log.w(TAG, "startTask: empty body for $chapterUrl, retrying...")
                                    continue
                                }
                                loadSuccess = true
                                break
                            }
                            is my.noveldokusha.core.Response.Error -> {
                                android.util.Log.w(TAG, "startTask: error loading $chapterUrl: ${result.message}")
                            }
                        }
                    }

                    if (loadSuccess && !chapterBody.isNullOrBlank()) {
                        val translationSuccess = translateAndSave(chapterUrl, chapterBody)
                        updateTask(task.bookUrl) {
                            it.copy(
                                successCount = it.successCount + 1,
                                consecutiveErrors = 0,
                                translationErrorCount = if (translationSuccess) it.translationErrorCount else it.translationErrorCount + 1,
                            )
                        }
                        i++
                        if (i < currentTask.chapterUrls.size) delay(delayMs)
                    } else {
                        // После 4 неудачных попыток — ставим задачу на паузу и завершаем job
                        updateTask(task.bookUrl) {
                            it.copy(
                                errorCount = it.errorCount + 1,
                                consecutiveErrors = it.consecutiveErrors + 1,
                                isPaused = true,
                            )
                        }
                        android.util.Log.w(TAG, "startTask: failed to load $chapterUrl after 4 attempts, pausing task")
                        val pausedTask = _tasks.value.find { it.bookUrl == task.bookUrl } ?: return@launch
                        showNotification(pausedTask)
                        // Завершаем job — семафор освободится в finally,
                        // и другая задача с того же домена сможет начать загрузку
                        return@launch
                    }
                }

                updateTask(task.bookUrl) { it.copy(isCompleted = true) }
                val finalTask = _tasks.value.find { it.bookUrl == task.bookUrl } ?: return@launch
                completeNotification(finalTask)
            } finally {
                activeJobs.remove(task.bookUrl)
                releaseDomainPermit(task.bookUrl)
            }
        }

        activeJobs[task.bookUrl] = job
    }

    private fun getNotificationId(bookUrl: String): Int =
        NOTIFICATION_ID_BASE + Math.abs(bookUrl.hashCode())

    private fun showNotification(task: DownloadTaskState) {
        val notificationId = getNotificationId(task.bookUrl)
        val builder = notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            notificationId = notificationId,
            importance = android.app.NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(task.bookTitle)
            setContentText(
                when {
                    task.isPaused -> "Paused: ${task.progressText}"
                    task.isCompleted -> "Download completed: ${task.successCount}/${task.totalCount}"
                    else -> "Downloading: ${task.progressText}"
                }
            )
            setOngoing(!task.isPaused && !task.isCompleted)
            setProgress(task.totalCount, task.currentIndex, false)

            if (task.isPaused) {
                addAction(android.R.drawable.ic_media_play, "Resume", createPendingIntent(task.bookUrl, DownloadNotificationReceiver.ACTION_RESUME))
            } else if (!task.isCompleted) {
                addAction(android.R.drawable.ic_media_pause, "Pause", createPendingIntent(task.bookUrl, DownloadNotificationReceiver.ACTION_PAUSE))
            }
            if (!task.isCompleted) {
                addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", createPendingIntent(task.bookUrl, DownloadNotificationReceiver.ACTION_CANCEL))
            }
        }
        notificationBuilders[task.bookUrl] = builder
    }

    private fun completeNotification(task: DownloadTaskState) {
        val builder = notificationBuilders[task.bookUrl] ?: return
        val notificationId = getNotificationId(task.bookUrl)
        notificationsCenter.modifyNotification(builder, notificationId) {
            setContentTitle(task.bookTitle)
            setContentText("Download completed: ${task.successCount}/${task.totalCount}")
            setOngoing(false)
            setProgress(0, 0, false)
        }
        notificationBuilders.remove(task.bookUrl)
    }

    private fun cancelNotification(task: DownloadTaskState) {
        notificationBuilders.remove(task.bookUrl)
        notificationsCenter.close(getNotificationId(task.bookUrl))
    }

    private fun createPendingIntent(bookUrl: String, action: String): PendingIntent {
        val intent = Intent(context, DownloadNotificationReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadNotificationReceiver.EXTRA_BOOK_URL, bookUrl)
        }
        // Используем уникальный requestCode на основе bookUrl и action,
        // чтобы избежать коллизий между разными книгами
        val requestCode = (bookUrl.hashCode() * 31 + action.hashCode()).coerceIn(0, Int.MAX_VALUE)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Переводит и сохраняет главу.
     * @return true если перевод успешен или не требуется, false если была ошибка
     */
    private suspend fun translateAndSave(chapterUrl: String, body: String): Boolean {
        val sourceLang = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
        val targetLang = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
        val isEnabled = appPreferences.GLOBAL_TRANSLATION_ENABLED.value
        if (!isEnabled || sourceLang.isBlank() || targetLang.isBlank()) {
            android.util.Log.d(TAG, "translateAndSave: skipped (enabled=$isEnabled, source='$sourceLang', target='$targetLang')")
            return true
        }

        try {
            android.util.Log.d(TAG, "translateAndSave: translating chapter $chapterUrl ($sourceLang -> $targetLang)")

            val paragraphs = body
                .replace(Regex("<(?!(imgEntry|/imgEntry))[^>]*>"), "")
                .replace("\r\n", "\n")
                .replace("\u00A0", " ")
                .replace(Regex("[ ]+"), " ")
                .let { cleanText ->
                    var splitResult = cleanText.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
                    if (splitResult.size <= 1 && cleanText.contains("\n")) {
                        splitResult = cleanText.split("\n").filter { it.isNotBlank() }
                    }
                    splitResult.map { it.trim() }.filter { it.isNotBlank() }
                }

            if (paragraphs.isEmpty()) {
                android.util.Log.w(TAG, "translateAndSave: no paragraphs to translate")
                return true
            }

            val translations = translationManager.translateBatch(paragraphs, sourceLang, targetLang)

            val entities = paragraphs.mapIndexed { index, original ->
                my.noveldokusha.feature.local_database.tables.ChapterTranslation(
                    chapterUrl = chapterUrl,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    paragraphIndex = index,
                    originalText = original,
                    translatedText = translations[original] ?: original
                )
            }.toMutableList()

            // Переводим и сохраняем заголовок главы (отдельный try-catch,
            // чтобы ошибка перевода заголовка не сломала весь батч)
            try {
                val chapter = appRepository.bookChapters.get(chapterUrl)
                if (chapter != null && chapter.title.isNotBlank()) {
                    val titleTranslated = translationManager.translateTitle(
                        chapter.title, sourceLang, targetLang
                    )
                    if (titleTranslated != null) {
                        entities.add(
                            my.noveldokusha.feature.local_database.tables.ChapterTranslation(
                                chapterUrl = chapterUrl,
                                sourceLang = sourceLang,
                                targetLang = targetLang,
                                paragraphIndex = -1,
                                originalText = chapter.title,
                                translatedText = titleTranslated
                            )
                        )
                        android.util.Log.d(TAG, "translateAndSave: saved title translation: '${chapter.title}' -> '$titleTranslated'")
                    }
                }
            } catch (e: Exception) {
                // Ошибка перевода заголовка не критична — логируем и продолжаем
                android.util.Log.e(TAG, "translateAndSave: title translation failed", e)
            }

            chapterTranslationDao.insertReplace(entities)
            android.util.Log.d(TAG, "translateAndSave: saved ${entities.size} translations to DB")
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "translateAndSave: failed", e)
            return false
        }
    }

    private fun updateTask(bookUrl: String, transform: (DownloadTaskState) -> DownloadTaskState) {
        _tasks.value = _tasks.value.map {
            if (it.bookUrl == bookUrl) transform(it) else it
        }
        // Сохраняем обновлённое состояние в БД
        val updated = _tasks.value.find { it.bookUrl == bookUrl }
        if (updated != null) {
            scope.launch {
                saveTaskToDatabase(updated)
            }
        }
    }
}