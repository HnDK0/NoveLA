package my.noveldokusha.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import my.noveldokusha.data.DownloadForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.feature.local_database.DAOs.DownloadTaskDao
import my.noveldokusha.feature.local_database.tables.DownloadTaskEntity
import my.noveldokusha.scraper.utils.normalizeBookUrl
import my.noveldokusha.text_translator.domain.TranslationManager
import org.json.JSONArray
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

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
    val isWaitingForNetwork: Boolean = false,
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
 * Per-domain очередь загрузок.
 *
 * Один домен = один worker-корутин, обрабатывающий книги последовательно.
 * Разные домены работают параллельно и независимо.
 *
 * Пауза/отмена освобождают домен для следующей книги в очереди.
 * Это предотвращает бан источника из-за параллельных запросов.
 */
private class DomainQueue {
    /** FIFO-очередь bookUrl для этого домена. */
    val queue: ArrayDeque<String> = ArrayDeque()

    /** Job активного worker'а. null или завершённый = worker не запущен. */
    var workerJob: Job? = null
}

/**
 * Результат вызова [DownloadManager.enqueue].
 *
 * [Added]       — новая задача создана, [count] глав добавлено в очередь.
 * [ChaptersAdded] — задача уже существует, [count] новых глав добавлено.
 * [Resumed]     — задача была паузнута, возобновлена.
 * [AlreadyQueued] — все главы уже в очереди или скачаны, ничего не изменилось.
 * [AllCached]   — все главы уже скачаны, нечего качать.
 */
sealed class EnqueueResult {
    data class Added(val count: Int) : EnqueueResult()
    data class ChaptersAdded(val count: Int) : EnqueueResult()
    object Resumed : EnqueueResult()
    object AlreadyQueued : EnqueueResult()
    object AllCached : EnqueueResult()
}

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

    // Mutex — suspend-friendly, не блокирует поток (замена synchronized)
    private val tasksMutex = Mutex()

    // Внутреннее состояние: Map<bookUrl, Task> для O(1) доступа
    private val _tasks = MutableStateFlow<Map<String, DownloadTaskState>>(emptyMap())

    // Публичный API: List для обратной совместимости
    val tasks: StateFlow<List<DownloadTaskState>> = _tasks
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // activeJobs: bookUrl -> Job скачивания. Доступ только под tasksMutex.
    private val activeJobs = HashMap<String, Job>()

    // Per-domain очереди. Доступ только под tasksMutex.
    private val domainQueues = HashMap<String, DomainQueue>()

    // Уведомления: bookUrl -> BookDownloadNotification.
    // ConcurrentHashMap — не требует tasksMutex, операции атомарны.
    private val notifications = ConcurrentHashMap<String, BookDownloadNotification>()

    // Sequential writer для БД — гарантирует FIFO-порядок записи (fix H2/M5)
    private val dbWriteChannel = Channel<DownloadTaskState>(capacity = Channel.UNLIMITED)

    // Сигнал готовности restoreTasksFromDatabase (fix H3)
    private val restoredSignal = CompletableDeferred<Unit>()

    // WakeLock для предотвращения Doze mode во время активных загрузок.
    // Удерживает CPU активным, чтобы delay() в retry срабатывал даже при выключенном экране.
    // Не reference-counted — acquire() и release() вызываются сбалансированно,
    // только при переходе между "есть активные загрузки" и "нет активных загрузок".
    private val wakeLock: PowerManager.WakeLock? = run {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG:download_wakelock"
        )
    }

    companion object {
        private const val TAG = "DownloadManager"

        // Константы retry/backoff
        private const val MAX_FETCH_RETRIES = 4
        private const val NETWORK_RETRY_INTERVAL_MS = 15_000L
        private const val NETWORK_RETRY_FAST_INTERVAL_MS = 5_000L
        private const val TRANSLATION_MAX_RETRIES = 2
    }

    init {
        // Sequential writer — гарантирует FIFO порядок записи в БД
        scope.launch {
            for (task in dbWriteChannel) {
                saveTaskToDatabase(task)
            }
        }
        scope.launch {
            restoreTasksFromDatabase()
            restoredSignal.complete(Unit)
        }

        // Следим за активными задачами и управляем foreground service
        // Foreground service — единственный способ гарантировать сетевой доступ в Doze mode
        scope.launch {
            _tasks.map { tasks ->
                tasks.values.any { !it.isCompleted && !it.isCancelled && !it.isPaused }
            }.distinctUntilChanged().collect { hasActiveTasks ->
                updateWakeLock(hasActiveTasks)
                if (hasActiveTasks) {
                    DownloadForegroundService.start(context)
                } else {
                    DownloadForegroundService.stop(context)
                }
            }
        }
    }

    private fun updateWakeLock(hasActiveTasks: Boolean) {
        val lock = wakeLock ?: return
        if (hasActiveTasks) {
            if (!lock.isHeld) {
                android.util.Log.d(TAG, "acquiring wake lock (active downloads)")
                lock.acquire()
            }
        } else {
            if (lock.isHeld) {
                android.util.Log.d(TAG, "releasing wake lock (no active downloads)")
                lock.release()
            }
        }
    }

    // ── Sequential writer для БД ──────────────────────────────────────────────

    /** Отправляет задачу в sequential writer. Гарантирует порядок FIFO. */
    private fun scheduleSave(task: DownloadTaskState) {
        dbWriteChannel.trySend(task)
    }

    // ── Восстановление из БД ─────────────────────────────────────────────────

    private suspend fun restoreTasksFromDatabase() {
        val savedTasks = try {
            withContext(Dispatchers.IO) { downloadTaskDao.getAll() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "restoreTasksFromDatabase: failed to read DB", e)
            return
        }

        android.util.Log.d(TAG, "restoreTasksFromDatabase: found ${savedTasks.size} tasks")

        // Deduplicate by normalized URL in case the database has legacy duplicates
        // (e.g. same book saved with and without trailing slash).
        val seenUrls = mutableSetOf<String>()
        for (entity in savedTasks) {
            val normalizedUrl = normalizeBookUrl(entity.bookUrl)
            if (normalizedUrl in seenUrls) {
                android.util.Log.w(TAG, "restoreTasksFromDatabase: duplicate found, " +
                        "removing $normalizedUrl (original=${entity.bookUrl})")
                withContext(Dispatchers.IO) { downloadTaskDao.delete(entity.bookUrl) }
                continue
            }
            seenUrls.add(normalizedUrl)
            try {
                if (entity.isCompleted || entity.isCancelled) {
                    withContext(Dispatchers.IO) { downloadTaskDao.delete(entity.bookUrl) }
                    continue
                }
                val task = entity.toState()
                android.util.Log.d(
                    TAG,
                    "restoreTasksFromDatabase: restoring ${entity.bookUrl} " +
                            "isPaused=${entity.isPaused} isWaitingForNetwork=${entity.isWaitingForNetwork} " +
                            "index=${entity.currentIndex}/${entity.totalCount}"
                )
                val notif = createNotification(task)

                tasksMutex.withLock {
                    _tasks.value = _tasks.value + (task.bookUrl to task)
                    if (!task.isPaused) enqueueToWorker(task.bookUrl)
                }

                if (task.isPaused) notif.showPaused(task) else notif.showQueued(task)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "restoreTasksFromDatabase: failed to restore task $entity", e)
            }
        }
    }

    // ── Сериализация ─────────────────────────────────────────────────────────

    private fun toChapterUrlsJson(urls: List<String>): String = JSONArray(urls).toString()

    private fun parseChapterUrlsJson(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun DownloadTaskEntity.toState() = DownloadTaskState(
        bookTitle = bookTitle,
        bookUrl = bookUrl,
        chapterUrls = parseChapterUrlsJson(chapterUrlsJson),
        currentIndex = currentIndex,
        totalCount = totalCount,
        isPaused = isPaused,
        isCancelled = isCancelled,
        isCompleted = isCompleted,
        isWaitingForNetwork = isWaitingForNetwork,
        errorCount = errorCount,
        successCount = successCount,
        consecutiveErrors = consecutiveErrors,
        skippedCount = skippedCount,
        translationErrorCount = translationErrorCount,
    )

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
                    isWaitingForNetwork = task.isWaitingForNetwork,
                    errorCount = task.errorCount,
                    successCount = task.successCount,
                    consecutiveErrors = task.consecutiveErrors,
                    skippedCount = task.skippedCount,
                    translationErrorCount = task.translationErrorCount,
                )
            )
        }
    }

    // ── Домены ───────────────────────────────────────────────────────────────

    private fun extractDomain(url: String): String = try {
        URI(url).host ?: "local"
    } catch (_: Exception) {
        "local"
    }

    /**
     * Ставит bookUrl в очередь домена и запускает worker если он не активен.
     * [prepend] = true — вставить в начало (Resume).
     * Вызывать только под [tasksMutex].
     */
    private fun enqueueToWorker(bookUrl: String, prepend: Boolean = false) {
        val domain = extractDomain(bookUrl)
        val dq = domainQueues.getOrPut(domain) { DomainQueue() }

        if (bookUrl !in dq.queue) {
            if (prepend) dq.queue.addFirst(bookUrl) else dq.queue.addLast(bookUrl)
        }

        if (dq.workerJob?.isActive != true) {
            dq.workerJob = scope.launch { runDomainWorker(domain) }
        }
    }

    /**
     * Worker домена: последовательно обрабатывает книги из очереди.
     * Между книгами одного домена нет параллелизма.
     */
    private suspend fun runDomainWorker(domain: String) {
        android.util.Log.d(TAG, "worker started: domain=$domain")
        while (true) {
            val bookUrl = tasksMutex.withLock {
                domainQueues[domain]?.queue?.removeFirstOrNull()
            } ?: run {
                // M2: очищаем пустой домен из очередей
                tasksMutex.withLock { domainQueues.remove(domain) }
                null
            } ?: break

            val task = tasksMutex.withLock {
                _tasks.value[bookUrl]
            }

            if (task == null || task.isCancelled || task.isCompleted || task.isPaused) {
                android.util.Log.d(TAG, "worker skip $bookUrl: null/cancelled/completed/paused")
                continue
            }

            android.util.Log.d(TAG, "worker processing $bookUrl")
            downloadBook(task)
            android.util.Log.d(TAG, "worker finished $bookUrl")
        }
        android.util.Log.d(TAG, "worker stopped: domain=$domain (queue empty)")
    }

    // ── Публичный API ────────────────────────────────────────────────────────

    /**
     * Добавляет главы книги в очередь загрузки.
     *
     * Suspend-функция — возвращает [EnqueueResult] который ViewModel использует
     * для показа правильного тоста. Вызывать из viewModelScope.
     */
    suspend fun enqueue(
        bookTitle: String,
        bookUrl: String,
        chapterUrls: List<String>,
    ): EnqueueResult {
        val normalizedUrl = normalizeBookUrl(bookUrl)
        val uniqueUrls = chapterUrls.distinct()
        if (uniqueUrls.isEmpty()) return EnqueueResult.AllCached

        // Фильтруем уже скачанные до lock — IO-операция
        val notDownloadedUrls = withContext(Dispatchers.IO) {
            uniqueUrls.filter { url -> chapterBodyRepository.getCachedBody(url) == null }
        }

        if (notDownloadedUrls.isEmpty()) return EnqueueResult.AllCached

        data class SyncResult(
            val shouldCreateNew: Boolean,
            val result: EnqueueResult?,
        )

        val syncResult = tasksMutex.withLock {
            val existing = _tasks.value[normalizedUrl]

            if (existing != null) {
                if (existing.isCancelled || existing.isCompleted) {
                    // Старая завершена/отменена — убираем, создадим новую
                    _tasks.value = _tasks.value - normalizedUrl
                    notifications.remove(normalizedUrl)?.close()
                    SyncResult(shouldCreateNew = true, result = null)
                } else if (existing.isPaused) {
                    // Паузнутая задача — добавляем новые главы если есть, потом resume
                    val newUrls = notDownloadedUrls.filter { it !in existing.chapterUrls }
                    val base = if (newUrls.isNotEmpty()) {
                        val updated = existing.copy(
                            chapterUrls = existing.chapterUrls + newUrls,
                            totalCount = existing.totalCount + newUrls.size,
                            isCompleted = false,
                            isCancelled = false,
                        )
                        _tasks.value = _tasks.value + (normalizedUrl to updated)
                        scheduleSave(updated)
                        updated
                    } else existing

                    val resumed = base.copy(isPaused = false, isWaitingForNetwork = false)
                    _tasks.value = _tasks.value + (normalizedUrl to resumed)
                    scheduleSave(resumed)
                    notifications[normalizedUrl]?.showDownloading(resumed)
                    enqueueToWorker(normalizedUrl, prepend = true)
                    SyncResult(shouldCreateNew = false, result = EnqueueResult.Resumed)
                } else {
                    // Задача активно качается — добавляем только новые главы
                    val newUrls = notDownloadedUrls.filter { it !in existing.chapterUrls }
                    if (newUrls.isNotEmpty()) {
                        val updated = existing.copy(
                            chapterUrls = existing.chapterUrls + newUrls,
                            totalCount = existing.totalCount + newUrls.size,
                            isCompleted = false,
                            isCancelled = false,
                        )
                        _tasks.value = _tasks.value + (normalizedUrl to updated)
                        scheduleSave(updated)
                        notifications[normalizedUrl]?.updateProgress(updated)
                        SyncResult(shouldCreateNew = false, result = EnqueueResult.ChaptersAdded(newUrls.size))
                    } else {
                        SyncResult(shouldCreateNew = false, result = EnqueueResult.AlreadyQueued)
                    }
                }
            } else {
                SyncResult(shouldCreateNew = true, result = null)
            }
        }

        // Результат уже известен — выходим без создания новой задачи
        syncResult.result?.let { return it }
        if (!syncResult.shouldCreateNew) return EnqueueResult.AlreadyQueued

        val task = DownloadTaskState(
            bookTitle = bookTitle,
            bookUrl = normalizedUrl,
            chapterUrls = notDownloadedUrls,
            totalCount = notDownloadedUrls.size,
        )
        val notif = createNotification(task)
        tasksMutex.withLock {
            _tasks.value = _tasks.value + (task.bookUrl to task)
            enqueueToWorker(normalizedUrl)
        }
        scheduleSave(task)
        notif.showQueued(task)
        return EnqueueResult.Added(notDownloadedUrls.size)
    }

    /**
     * Поставить задачу на паузу.
     *
     * Fire-and-forget: метод асинхронный. Вызов из BroadcastReceiver.onReceive
     * безопасен — тяжёлая работа уходит в scope.launch.
     * После переключения в ACT MODE дожидается готовности restore-сигнала.
     */
    fun pause(bookUrl: String) {
        scope.launch {
            restoredSignal.await()
            tasksMutex.withLock {
                val task = _tasks.value[bookUrl] ?: return@withLock
                if (task.isPaused || task.isCompleted || task.isCancelled) return@withLock

                val paused = task.copy(isPaused = true, isWaitingForNetwork = false)
                _tasks.value = _tasks.value + (bookUrl to paused)

                activeJobs.remove(bookUrl)?.cancel()
                domainQueues[extractDomain(bookUrl)]?.queue?.remove(bookUrl)

                scheduleSave(paused)
                notifications[bookUrl]?.showPaused(paused)
            }
        }
    }

    /**
     * Возобновить задачу из паузы.
     *
     * Fire-and-forget: метод асинхронный. Вызов из BroadcastReceiver.onReceive
     * безопасен — тяжёлая работа уходит в scope.launch.
     */
    fun resume(bookUrl: String) {
        scope.launch {
            restoredSignal.await()
            tasksMutex.withLock {
                val task = _tasks.value[bookUrl] ?: return@withLock
                if (!task.isPaused) return@withLock

                val resumed = task.copy(isPaused = false, isWaitingForNetwork = false)
                _tasks.value = _tasks.value + (bookUrl to resumed)
                enqueueToWorker(bookUrl, prepend = true)

                scheduleSave(resumed)
                notifications[bookUrl]?.showDownloading(resumed)
            }
        }
    }

    /**
     * Отменить задачу.
     *
     * Fire-and-forget: метод асинхронный. Вызов из BroadcastReceiver.onReceive
     * безопасен — тяжёлая работа уходит в scope.launch.
     */
    fun cancel(bookUrl: String) {
        scope.launch {
            restoredSignal.await()
            val task: DownloadTaskState?
            tasksMutex.withLock {
                activeJobs.remove(bookUrl)?.cancel()
                domainQueues[extractDomain(bookUrl)]?.queue?.remove(bookUrl)
                task = _tasks.value[bookUrl]
                if (task != null) {
                    _tasks.value = _tasks.value - bookUrl
                }
            }
            task ?: return@launch

            // showCancelled и auto-close через 2s
            notifications.remove(bookUrl)?.let { notif ->
                notif.showCancelled()
                delay(2_000)
                notif.close()
            }
            withContext(Dispatchers.IO) { downloadTaskDao.delete(bookUrl) }
        }
    }

    /**
     * Убирает завершённую/паузнутую задачу из UI и закрывает уведомление.
     *
     * Fire-and-forget: метод асинхронный.
     */
    fun dismiss(bookUrl: String) {
        scope.launch {
            tasksMutex.withLock {
                _tasks.value = _tasks.value - bookUrl
            }
            notifications.remove(bookUrl)?.close()
            withContext(Dispatchers.IO) { downloadTaskDao.delete(bookUrl) }
        }
    }

    // ── Скачивание книги ─────────────────────────────────────────────────────

    private suspend fun downloadBook(initialTask: DownloadTaskState) {
        val bookUrl = initialTask.bookUrl

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // Переключаем уведомление с Queued на Downloading в момент старта
                val startTask = tasksMutex.withLock {
                    _tasks.value[bookUrl]
                }
                if (startTask != null) notifications[bookUrl]?.showDownloading(startTask)

                val delayMs = appPreferences.DOWNLOAD_DELAY_MS.value
                var i = tasksMutex.withLock {
                    _tasks.value[bookUrl]?.currentIndex ?: 0
                }

                while (true) {
                    val current = tasksMutex.withLock {
                        _tasks.value[bookUrl]
                    } ?: return@launch

                    if (current.isCancelled) return@launch
                    if (current.isPaused) return@launch
                    if (i >= current.chapterUrls.size) break

                    val chapterUrl = current.chapterUrls[i]

                    // Обновляем индекс, сохраняем в БД и обновляем уведомление
                    val withIndex = updateTask(bookUrl) { it.copy(currentIndex = i + 1) }
                    if (withIndex != null) {
                        scheduleSave(withIndex)
                        notifications[bookUrl]?.updateProgress(withIndex)
                    }

                    // Пропускаем уже скачанные — без delay, нет сетевого запроса
                    if (chapterBodyRepository.getCachedBody(chapterUrl) != null) {
                        android.util.Log.d(TAG, "skip cached: $chapterUrl")
                        updateTask(bookUrl) { it.copy(skippedCount = it.skippedCount + 1, consecutiveErrors = 0) }
                        i++
                        continue
                    }

                    // Загрузка с retry + exponential backoff и ожиданием сети
                    when (val fetchResult = fetchWithRetry(bookUrl, chapterUrl)) {
                        is FetchResult.Interrupted -> {
                            android.util.Log.d(TAG, "fetch interrupted by user: $chapterUrl")
                            return@launch
                        }
                        is FetchResult.WaitingForNetwork -> {
                            android.util.Log.w(TAG, "network unavailable, waiting for connection: $chapterUrl")
                            continue
                        }
                        is FetchResult.Failed -> {
                            android.util.Log.w(TAG, "all retries failed, pausing: $bookUrl")
                            val paused = updateTask(bookUrl) {
                                it.copy(
                                    isPaused = true,
                                    isWaitingForNetwork = false,
                                    errorCount = it.errorCount + 1,
                                    consecutiveErrors = it.consecutiveErrors + 1,
                                )
                            }
                            if (paused != null) {
                                scheduleSave(paused)
                                notifications[bookUrl]?.showPaused(paused)
                            }
                            return@launch
                        }
                        is FetchResult.Success -> {
                            // Проверяем паузу перед переводом — он может быть долгим
                            val taskBeforeTranslate = tasksMutex.withLock {
                                _tasks.value[bookUrl]
                            }
                            if (taskBeforeTranslate == null || taskBeforeTranslate.isCancelled || taskBeforeTranslate.isPaused) {
                                android.util.Log.d(TAG, "interrupted before translate: $chapterUrl")
                                return@launch
                            }

                            val translationSuccess = translateAndSave(chapterUrl, fetchResult.body)
                            updateTask(bookUrl) {
                                it.copy(
                                    successCount = it.successCount + 1,
                                    consecutiveErrors = 0,
                                    translationErrorCount = if (translationSuccess) it.translationErrorCount
                                    else it.translationErrorCount + 1,
                                )
                            }
                            i++

                            // Читаем актуальный размер списка глав — он мог вырасти
                            // пока мы качали (пользователь добавил новые главы через enqueue)
                            val actualSize = tasksMutex.withLock {
                                _tasks.value[bookUrl]?.chapterUrls?.size ?: i
                            }
                            if (i < actualSize) delay(delayMs)
                        }
                    }
                }

                // Все главы обработаны
                val completed = updateTask(bookUrl) { it.copy(isCompleted = true, isWaitingForNetwork = false) }
                if (completed != null) notifications[bookUrl]?.showCompleted(completed)
                withContext(Dispatchers.IO) { downloadTaskDao.delete(bookUrl) }

            } finally {
                tasksMutex.withLock { activeJobs.remove(bookUrl) }
            }
        }

        tasksMutex.withLock { activeJobs[bookUrl] = job }
        job.start()
        job.join()
    }

    /**
     * Результат попытки загрузки главы.
     */
    private sealed class FetchResult {
        data class Success(val body: String) : FetchResult()
        object Interrupted : FetchResult()
        object WaitingForNetwork : FetchResult()
        object Failed : FetchResult()
    }

    /**
     * Определяет, является ли ошибка сетевой (DNS/соединение/таймаут/TLS).
     * M4: добавлены SocketTimeoutException, SSLException как основные критерии,
     * строковые проверки оставлены как fallback.
     */
    private fun isNetworkError(error: my.noveldokusha.core.Response.Error): Boolean {
        val exception = error.exception
        return when (exception) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is SSLException -> true
            else -> {
                val msg = error.message
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                        msg.contains("Failed to connect", ignoreCase = true) ||
                        msg.contains("Network is unreachable", ignoreCase = true) ||
                        msg.contains("hostname", ignoreCase = true)
            }
        }
    }

    /**
     * Загружает главу с [MAX_FETCH_RETRIES] попытками и exponential backoff.
     *
     * Backoff: 0s → 60s → 300s → 600s между попытками.
     *
     * Если на любой попытке обнаружена сетевая ошибка (DNS/соединение),
     * то сразу переходит в бесконечный цикл с retry каждые [NETWORK_RETRY_INTERVAL_MS] без паузы задачи.
     */
    private suspend fun fetchWithRetry(
        bookUrl: String,
        chapterUrl: String,
    ): FetchResult {
        for (attempt in 0 until MAX_FETCH_RETRIES) {
            if (attempt > 0) {
                val backoffMs = when (attempt) {
                    1 -> 60_000L
                    2 -> 300_000L
                    else -> 600_000L
                }
                android.util.Log.d(TAG, "retry $attempt for $chapterUrl, wait ${backoffMs}ms")
                delay(backoffMs)

                // Проверяем статус после ожидания backoff.
                val taskAfterWait = tasksMutex.withLock {
                    _tasks.value[bookUrl]
                }
                if (taskAfterWait == null || taskAfterWait.isCancelled || taskAfterWait.isPaused) {
                    android.util.Log.d(TAG, "interrupted during backoff: $chapterUrl")
                    return FetchResult.Interrupted
                }
            }

            val result = chapterBodyRepository.fetchBody(chapterUrl)
            when (result) {
                is my.noveldokusha.core.Response.Success -> {
                    val body = result.data
                    if (body.isNullOrBlank()) {
                        android.util.Log.w(TAG, "empty body attempt=$attempt: $chapterUrl")
                        continue
                    }
                    return FetchResult.Success(body)
                }
                is my.noveldokusha.core.Response.Error -> {
                    android.util.Log.w(TAG, "fetch error attempt=$attempt: $chapterUrl — ${result.message}")
                    if (isNetworkError(result)) {
                        android.util.Log.w(TAG, "network error, entering network wait: $chapterUrl")
                        return waitForNetworkThenRetry(bookUrl, chapterUrl)
                    }
                }
            }
        }

        android.util.Log.w(TAG, "all retries exhausted (non-network): $chapterUrl")
        return FetchResult.Failed
    }

    /**
     * Проверяет, есть ли активное сетевое соединение через ConnectivityManager.
     * Используется в [waitForNetworkThenRetry] для выбора интервала ретрая.
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true // если не можем проверить — считаем что сеть есть
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true // fallback — пробуем сделать запрос
        }
    }

    /**
     * Бесконечный цикл ожидания сети.
     *
     * Если ConnectivityManager показывает, что сеть есть — использует
     * [NETWORK_RETRY_FAST_INTERVAL_MS] (5s), иначе [NETWORK_RETRY_INTERVAL_MS] (15s).
     * Это позволяет быстрее восстанавливать загрузку после выхода из Doze mode
     * при разблокировке экрана.
     */
    private suspend fun waitForNetworkThenRetry(
        bookUrl: String,
        chapterUrl: String,
    ): FetchResult {
        updateTask(bookUrl) { it.copy(isWaitingForNetwork = true) }
        notifications[bookUrl]?.showWaitingForNetwork(
            tasksMutex.withLock { _tasks.value[bookUrl] }
                ?: return FetchResult.Interrupted
        )

        while (true) {
            val hasNetwork = isNetworkAvailable()
            val delayMs = if (hasNetwork) NETWORK_RETRY_FAST_INTERVAL_MS else NETWORK_RETRY_INTERVAL_MS
            delay(delayMs)

            // Проверяем статус — могла прийти пауза/отмена
            val task = tasksMutex.withLock { _tasks.value[bookUrl] }
            if (task == null || task.isCancelled) {
                android.util.Log.d(TAG, "network wait cancelled: $chapterUrl")
                return FetchResult.Interrupted
            }
            if (task.isPaused) {
                android.util.Log.d(TAG, "network wait paused: $chapterUrl")
                return FetchResult.Interrupted
            }

            android.util.Log.d(TAG, "network retry for $chapterUrl (hasNetwork=$hasNetwork, delay=${delayMs}ms)")
            val result = chapterBodyRepository.fetchBody(chapterUrl)
            when (result) {
                is my.noveldokusha.core.Response.Success -> {
                    val body = result.data
                    if (body.isNullOrBlank()) {
                        android.util.Log.w(TAG, "network retry empty body: $chapterUrl")
                        continue
                    }
                    updateTask(bookUrl) { it.copy(isWaitingForNetwork = false) }
                    notifications[bookUrl]?.showDownloading(
                        tasksMutex.withLock { _tasks.value[bookUrl] }
                            ?: return FetchResult.Interrupted
                    )
                    return FetchResult.Success(body)
                }
                is my.noveldokusha.core.Response.Error -> {
                    if (isNetworkError(result)) {
                        android.util.Log.d(TAG, "network still down, retrying in ${delayMs}ms: $chapterUrl")
                        continue
                    }
                    android.util.Log.w(TAG, "non-network error during network wait: $chapterUrl")
                    return FetchResult.Failed
                }
            }
        }
    }

    // ── Вспомогательные методы ───────────────────────────────────────────────

    /**
     * Атомарно обновляет задачу в [_tasks] и возвращает новое состояние.
     * Не сохраняет в БД — вызывающий сохраняет сам через scheduleSave.
     * O(1) — прямое обращение по ключу в Map.
     */
    private suspend fun updateTask(
        bookUrl: String,
        transform: (DownloadTaskState) -> DownloadTaskState,
    ): DownloadTaskState? {
        var result: DownloadTaskState? = null
        tasksMutex.withLock {
            val existing = _tasks.value[bookUrl] ?: return@withLock
            val updated = transform(existing)
            _tasks.value = _tasks.value + (bookUrl to updated)
            result = updated
        }
        return result
    }

    /**
     * Создаёт [BookDownloadNotification] для книги и регистрирует в [notifications].
     * Если для этой книги уже есть уведомление — закрывает старое и создаёт новое.
     */
    private fun createNotification(task: DownloadTaskState): BookDownloadNotification {
        notifications.remove(task.bookUrl)?.close()
        val notif = BookDownloadNotification(
            bookUrl = task.bookUrl,
            bookTitle = task.bookTitle,
            context = context,
            notificationsCenter = notificationsCenter,
        )
        notifications[task.bookUrl] = notif
        return notif
    }

    // ── Перевод ──────────────────────────────────────────────────────────────

    /**
     * Переводит и сохраняет главу.
     * H5: Добавлен retry-цикл для translateBatch (2 попытки с backoff).
     * При полном провале возвращает false — глава засчитывается как успешно скачанная,
     * но translationErrorCount инкрементируется.
     */
    private suspend fun translateAndSave(chapterUrl: String, body: String): Boolean {
        val sourceLang = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
        val targetLang = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
        val isEnabled = appPreferences.GLOBAL_TRANSLATION_ENABLED.value
        if (!isEnabled || sourceLang.isBlank() || targetLang.isBlank()) {
            android.util.Log.d(TAG, "translation skipped (enabled=$isEnabled)")
            return true
        }

        return try {
            val paragraphs = body
                .replace(Regex("<(?!(imgEntry|/imgEntry))[^>]*>"), "")
                .replace("\r\n", "\n")
                .replace("\u00A0", " ")
                .replace(Regex("[ ]+"), " ")
                .let { clean ->
                    var parts = clean.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
                    if (parts.size <= 1 && clean.contains("\n"))
                        parts = clean.split("\n").filter { it.isNotBlank() }
                    parts.map { it.trim() }.filter { it.isNotBlank() }
                }

            if (paragraphs.isEmpty()) return true

            // H5: Retry-цикл для translateBatch
            val translations = translateBatchWithRetry(paragraphs, sourceLang, targetLang)
                ?: return false // Все попытки исчерпаны

            val translatedParagraphs = org.json.JSONArray(
                paragraphs.map { translations[it] ?: it }
            ).toString()

            var titleTranslation = ""
            try {
                val chapter = appRepository.bookChapters.get(chapterUrl)
                if (chapter != null && chapter.title.isNotBlank()) {
                    val translated = translationManager.translateTitle(
                        chapter.title, sourceLang, targetLang
                    )
                    if (translated != null) {
                        titleTranslation = translated
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "title translation failed", e)
            }

            chapterTranslationDao.insertReplace(
                my.noveldokusha.feature.local_database.tables.ChapterTranslation(
                    chapterUrl = chapterUrl,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    translatedParagraphs = translatedParagraphs,
                    titleTranslation = titleTranslation,
                )
            )
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "translateAndSave failed", e)
            false
        }
    }

    /**
     * Переводит batch параграфов с retry-циклом.
     * H5: [TRANSLATION_MAX_RETRIES] попыток с exponential backoff.
     * Возвращает null если все попытки исчерпаны.
     */
    private suspend fun translateBatchWithRetry(
        paragraphs: List<String>,
        sourceLang: String,
        targetLang: String,
    ): Map<String, String>? {
        var lastError: Exception? = null
        for (attempt in 0 until TRANSLATION_MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    val backoffMs = 1_000L * (attempt + 1) // 1s, 2s
                    delay(backoffMs)
                    // Проверяем не отменена ли задача
                    // (пауза/отмена проверяется выше в downloadBook перед вызовом)
                }
                return translationManager.translateBatch(paragraphs, sourceLang, targetLang)
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w(TAG, "translateBatch attempt=$attempt failed: ${e.message}")
            }
        }
        android.util.Log.e(TAG, "translateBatch exhausted after $TRANSLATION_MAX_RETRIES attempts", lastError)
        return null
    }
}