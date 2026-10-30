package my.noveldokusha.features.reader.video

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import my.noveldokusha.core.OfflineVideoCleaner
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.network.MediaHttpClient
import my.noveldokusha.reader.R
import my.noveldokusha.scraper.domain.VideoSource
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * media3 не даёт именованной константы паузы: любой ненулевой stopReason
 * останавливает загрузку, [Download.STOP_REASON_NONE] возобновляет.
 */
internal const val STOP_REASON_PAUSED = 1

/**
 * Обёртка media3 [DownloadManager] для офлайн-видео (Task 14).
 *
 * Схема по официальной доке media3 (verify-point брифа): заявка
 * [DownloadRequest] уходит в [VideoDownloadService] через
 * [DownloadService.sendAddDownload] — сервис поднимает FGS и докачивает
 * в фоне; singleton здесь владеет кэшем и индексом.
 *
 * Кэш — в filesDir (D6): «Очистить кэш» не трогает скачанные эпизоды.
 */
@UnstableApi
@Singleton
class VideoDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @MediaHttpClient private val client: OkHttpClient,
    private val notificationsCenter: NotificationsCenter,
) : OfflineVideoCleaner {
    private val databaseProvider = StandaloneDatabaseProvider(context)

    private val downloadCache = SimpleCache(
        File(context.filesDir, "video_downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    /**
     * DownloadManager с явной фабрикой даунлоадеров: заголовки (Referer и пр.)
     * едут в DownloadRequest.data и применяются per-download — глобальных
     * default-заголовков у OkHttpDataSource.Factory для параллельных
     * загрузок из разных источников быть не может.
     */
    val downloadManager: DownloadManager = DownloadManager(
        context,
        DefaultDownloadIndex(databaseProvider),
        HeaderAwareDownloaderFactory(downloadCache, client),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Бейджи всех отслеживаемых эпизодов: chapterUrl → состояние.
     *
     * Один общий опрос на процесс вместо отдельного flow на каждый url —
     * иначе список из 100 глав превращается в 100 параллельных опросов.
     */
    private val _uiStates = MutableStateFlow<Map<String, ChapterDownloadUi>>(emptyMap())
    val uiStates: StateFlow<Map<String, ChapterDownloadUi>> = _uiStates.asStateFlow()

    /**
     * Терминалы (COMPLETED/FAILED), ушедшие из currentDownloads.
     *
     * Живут бессрочно: chapter.downloaded для видео всегда false
     * (ChaptersRepository собирает его только из ChapterBody и страниц манги),
     * поэтому бейдж целиком держится на этой карте. Снимают её только
     * remove/removeAll и media3-callback onDownloadRemoved.
     */
    private val recentTerminal = ConcurrentHashMap<String, ChapterDownloadUi>()

    /**
     * Заявки, по которым remove() уже вызван: бейдж снят, media3 доделывает
     * удаление асинхронно. Опрос не возвращает их в карту, пока заявка ещё
     * видна в currentDownloads; метка снимается, когда оттуда она ушла.
     */
    private val pendingRemoval = ConcurrentHashMap.newKeySet<String>()

    /**
     * Оптимистичные метки «в очереди»: url, поставленные в очередь UI до
     * того, как media3 создал заявку (резолв потока — сетевой запрос).
     *
     * Живёт только в [uiStates]: в currentDownloads и в проверках
     * needsPausedNotification/needsStartedService не попадает.
     */
    private val optimisticQueued = ConcurrentHashMap.newKeySet<String>()

    /**
     * Названия серий для уведомлений (url → title), порядок вставки —
     * порядок глав. synchronizedMap: пишет главный поток (enqueue),
     * читает опрос на Dispatchers.IO и сервис.
     */
    private val chapterTitles: MutableMap<String, String> =
        Collections.synchronizedMap(LinkedHashMap())

    // Будильник для опроса в простое: listener сообщает о новой загрузке.
    private val wakeSignal = MutableStateFlow(0L)

    // Уведомление «на паузе»: показано/скрыто, чтобы не дёргать notify на каждом тике.
    // Вolatile: пишет и опрос (IO), и кнопки из шторки (главный поток).
    @Volatile
    private var pausedNotificationShown = false

    // Показанный в 1402 набор id — в паре с pausedNotificationShown (их пишут
    // только show/close). Поллинг сравнивает с актуальным и перепоказывает,
    // когда resolve-цикл дозаписал главы в паузу уже после оптимистичного
    // показа: счётчик в уведомлении не замерзает на «1 серия».
    @Volatile
    private var pausedNotificationUrls: List<String> = emptyList()

    /**
     * Поколение отмены: +1 на каждый [cancelAll].
     *
     * Гонка со «Стопом» из шторки: enqueueVideoDownloads резолвит главы
     * по одной (сетевой запрос, сотни мс), и cancelAll() не видит ещё не
     * добавленные заявки — цикл добавлял бы новые уже после отмены, и
     * пользователь видел «отменяет по 1». Цикл фиксирует поколение до
     * старта и сверяет перед каждой главой.
     */
    private val cancelEpoch = AtomicInteger(0)

    /** Текущее поколение отмены — см. [cancelEpoch]. */
    val cancelGeneration: Int get() = cancelEpoch.get()

    /**
     * Включена ли глобальная пауза ([pauseAll] без [resumeAll]).
     *
     * Заявки, добавленные циклом enqueueVideoDownloads уже после паузы,
     * стартовали бы сразу — флаг говорит поставить их на паузу.
     */
    @Volatile
    private var pausedAll = false

    /** Текущее состояние глобальной паузы — см. [pausedAll]. */
    val isPausedAll: Boolean get() = pausedAll

    // Снято ли stale-уведомление 1402 при первом инициализированном тике
    // (один раз на процесс, см. uiStatesPollLoop).
    private var staleCleared = false

    init {
        // Единый DownloadManager на процесс — конфигурируем здесь, в месте
        // создания, а не в сервисе (getDownloadManager() менеджер не создаёт).
        downloadManager.setMaxParallelDownloads(MAX_PARALLEL_DOWNLOADS)
        downloadManager.setMinRetryCount(MIN_RETRY_COUNT)
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(manager: DownloadManager) = wakeUp()

            override fun onDownloadChanged(
                manager: DownloadManager,
                download: Download,
                exception: Exception?,
            ) = wakeUp()

            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                recentTerminal.remove(download.request.id)
                wakeUp()
            }
        })
        scope.launch { uiStatesPollLoop() }
    }

    private fun wakeUp() {
        wakeSignal.update { it + 1 }
    }

    /**
     * Опрос нетерминальных загрузок каждые [POLL_INTERVAL_MS].
     *
     * Listener media3 не вызывается на каждый тик процентов — только при
     * смене состояния, поэтому проценты читаем опросом. Как только активных
     * не осталось — ровно один read индекса на каждый «ушедший» id (там
     * лежит COMPLETED/FAILED) и замираем до следующего события.
     */
    private suspend fun uiStatesPollLoop() {
        var previousActive = emptySet<String>()
        var seeded = false
        while (true) {
            // Считываем сигнал ДО чтения currentDownloads: пробуждение,
            // пришедшее во время тика, не должно потеряться. isInitialized ставится
            // в true ДО присваивания списка (DownloadManager.onInitialized), так что
            // true актуальность списка ниже не гарантирует — её даёт чтение seen
            // первым: пробуждение от onInitialized не потеряется и догонит лишним тиком.
            val seen = wakeSignal.value
            val initialized = downloadManager.isInitialized
            // Один сид на процесс: после рестарта previousActive пуст и
            // leftActive не сработает — без чтения индекса бейджей COMPLETED
            // на докачанных эпизодах не было бы вовсе.
            if (initialized && !seeded) {
                seeded = true
                seedTerminalStates()
            }
            val active = downloadManager.currentDownloads.associateBy { it.request.id }
            val leftActive = previousActive - active.keys
            previousActive = active.keys

            val states = HashMap<String, ChapterDownloadUi>(active.size + recentTerminal.size)
            for ((id, download) in active) {
                // Заявка в remove(): media3 ещё не удалил её — не воскрешаем бейдж.
                if (id in pendingRemoval) continue
                states[id] = chapterDownloadUiOf(download)
            }

            for (id in leftActive) {
                if (id in pendingRemoval) continue
                val ui = readTerminalUi(id) ?: continue
                states[id] = ui
                if (ui.state == ChapterDownloadUiState.COMPLETED ||
                    ui.state == ChapterDownloadUiState.FAILED
                ) {
                    recentTerminal[id] = ui
                }
            }
            // putIfAbsent: живая загрузка важнее закэшированного терминала.
            for ((id, terminal) in recentTerminal) states.putIfAbsent(id, terminal)

            // media3 подтвердил удаление (заявка вышла из активных) — метка снимается.
            pendingRemoval.retainAll(active.keys)

            // Оптимистичная метка: заявка ещё резолвится, бейдж ставим уже
            // сейчас. Гасим метки, у которых уже есть активная заявка (бейдж
            // продолжает реальное состояние) или терминал в states — иначе
            // ghost-QUEUED жил бы вечно. Набор resolved считаем ДО
            // подстановки: иначе свежевставленный id попал бы в states и сам
            // себя погасил на этом же тике.
            val resolved = optimisticQueued.filter { it in active || it in states }
            for (id in resolved) optimisticQueued.remove(id)
            for (id in optimisticQueued) {
                if (id !in states) states[id] = ChapterDownloadUi(state = ChapterDownloadUiState.QUEUED)
            }

            _uiStates.value = states
            // Холодный старт по кнопке Resume: resumeAll уже снял паузу, пока
            // поднималась эта корутина, поэтому первый тик может увидеть «всё
            // активно» при shown=false — снимаем возможное stale-1402 (cancel
            // несуществующего id — no-op), иначе «на паузе» висит вечно рядом
            // с работающим FGS. Если загрузки всё ещё на паузе — перепоказ ниже.
            if (initialized && !staleCleared) {
                staleCleared = true
                closePausedNotification()
            }
            // Страховка микро-гонки: пауза могла пройти между проверкой
            // isPausedAll в enqueue и доставкой add-интента — глава ушла бы
            // в качку. Условие по stopReason: null-id путь в media3 дополнительно
            // пишет индекс, звать его каждый тик на уже паузнутых нельзя;
            // для уже паузнутых заявок setStopReason не шлёт listener-событий,
            // так что цикла пробуждений нет. Тик тут идёт всегда: во время
            // паузы currentDownloads непуст (STOPPED-заявки активны),
            // а пустое active означает, что чинить нечего.
            if (initialized && pausedAll &&
                active.values.any { it.stopReason != STOP_REASON_PAUSED }
            ) {
                downloadManager.setStopReason(null, STOP_REASON_PAUSED)
            }
            // FGS-уведомление гаснет вместе с сервисом после паузы всех заявок
            // (media3 onIdle → stopSelf) — показываем своё «на паузе» вместо него.
            if (initialized) updatePausedNotification(active.values.toList())

            if (initialized && active.isEmpty()) {
                // Активных нет: терминалы неподвижны — опрос не нужен, спим
                // до события. Пробуждение прерывает сон (устойчиво к тику).
                wakeSignal.first { it > seen }
            } else {
                // Пробуждение прерывает сон: снятие заявки видно сразу, а не через тик.
                withTimeoutOrNull(POLL_INTERVAL_MS) { wakeSignal.first { it > seen } }
            }
        }
    }

    /**
     * Терминалы COMPLETED/FAILED из индекса при первом тике процесса.
     * currentDownloads терминальные заявки не содержит (дока media3), а
     * chapter.downloaded для видео не заполняется — без сидинга докачанный
     * эпизод после рестарта выглядел бы нескачанным.
     */
    private fun seedTerminalStates() {
        runCatching {
            downloadManager.downloadIndex
                .getDownloads(Download.STATE_COMPLETED, Download.STATE_FAILED)
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        if (download.request.id in pendingRemoval) continue
                        recentTerminal[download.request.id] = chapterDownloadUiOf(download)
                    }
                }
        }
    }

    /** Терминальное состояние из индекса; null — загрузки уже нет (удалена). */
    private fun readTerminalUi(chapterUrl: String): ChapterDownloadUi? =
        runCatching { downloadManager.downloadIndex.getDownload(chapterUrl) }
            .getOrNull()
            ?.let { chapterDownloadUiOf(it) }

    /** Пауза/возобновление конкретной главы через stopReason (не через remove). */
    fun pause(chapterUrl: String) = setStopReason(chapterUrl, STOP_REASON_PAUSED)

    fun resume(chapterUrl: String) = setStopReason(chapterUrl, Download.STOP_REASON_NONE)

    private fun setStopReason(chapterUrl: String, stopReason: Int) =
        DownloadService.sendSetStopReason(
            context, VideoDownloadService::class.java, chapterUrl, stopReason, false
        )

    /**
     * Пауза всех нетерминальных заявок: null-id применяет stopReason ко всем
     * загрузкам в памяти менеджера (media3 не хранит там COMPLETED/FAILED —
     * они в индексе и в бейджах не затрагиваются). Вызов идёт напрямую
     * в [downloadManager], минуя сервис: DownloadService.onDestroy не вызывает
     * release() — singleton живёт и обрабатывает сообщения, когда сервис
     * остановлен.
     */
    fun pauseAll() {
        // Флаг до setStopReason: цикл enqueueVideoDownloads читает его сразу
        // после резолва и ставит новые заявки на паузу.
        pausedAll = true
        downloadManager.setStopReason(null, STOP_REASON_PAUSED)
        // Уведомление «на паузе» сразу, не через тик опроса (500 мс+): иначе
        // FGS-гаснет вместе с сервисом, а Resume появляется лишь на следующем
        // опросе — шторка успевает закрыться, и возобновить нечего.
        // setStopReason асинхронен (handler DownloadService), state ещё не
        // STOPPED — показываем оптимистично по currentDownloads: все они сейчас
        // и уйдут в паузу. Поллинг (updatePausedNotification) — страховка.
        val ids = downloadManager.currentDownloads.map { it.request.id }
        if (ids.isNotEmpty()) showPausedNotificationIfChanged(ids)
    }

    /** Возобновление всех + поднятие FGS заново. */
    fun resumeAll() {
        pausedAll = false
        // Немедленно: stale-1402 не должен висеть рядом с работающим FGS,
        // ждать тик опроса нельзя.
        closePausedNotification()
        downloadManager.setStopReason(null, Download.STOP_REASON_NONE)
        // Пауза гасила FGS (media3 onIdle → stopSelf): без него докачка пойдёт
        // без уведомления. У остановленного сервиса scheduler == null, так что
        // scheduler-рестарта нет — поднимаем явно. Собственная попытка media3
        // (restartService из DownloadManagerHelper) сработает только когда
        // заявка реально перейдёт в DOWNLOADING и может быть отклонена системой,
        // поэтому отказ здесь — не безобидный, его логируем.
        runCatching { DownloadService.startForeground(context, VideoDownloadService::class.java) }
            .onFailure { Timber.e(it, "Failed to start VideoDownloadService foreground after resumeAll") }
    }

    /**
     * Отмена всех активных заявок. НЕ использовать removeAllDownloads() —
     * он стирает и уже скачанное.
     */
    fun cancelAll() {
        // Поколение в первую очередь: цикл enqueueVideoDownloads, резолвящий
        // главы, увидит смену и выйдет, не добавляя новые заявки поверх отмены.
        cancelEpoch.incrementAndGet()
        // Немедленно: уведомление «на паузе» после Стопа устарело.
        closePausedNotification()
        // Очередь вся удалена — флаг глобальной паузы не должен переживать её:
        // иначе цепочка «пауза → стоп → новый запуск» рождает новые заявки
        // сразу на паузе, и юзер вынужден жать Resume в шторке.
        pausedAll = false
        // id читаем с диска, а не из памяти менеджера: на холодном старте
        // DownloadManager только что создан — currentDownloads пуст, пока
        // MSG_INITIALIZE стоит в очереди HandlerThread, а onReceive уже идёт.
        // removeDownload уходит в ту же очередь ПОСЛЕ инициализации, так что
        // сообщения обработаются в правильном порядке. Терминалы
        // (COMPLETED/FAILED) в набор не входят — скачанное не трогаем.
        val ids = runCatching {
            downloadManager.downloadIndex
                .getDownloads(
                    Download.STATE_QUEUED,
                    Download.STATE_STOPPED,
                    Download.STATE_DOWNLOADING,
                    Download.STATE_REMOVING,
                    Download.STATE_RESTARTING,
                )
                .use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.download.request.id)
                    }
                }
        }.getOrElse { emptyList() }
        // Активных не остаётся — и оптимистичных меток, и заголовков тоже.
        optimisticQueued.clear()
        chapterTitles.clear()
        for (id in ids) remove(id)
    }

    /**
     * Оптимистичная метка «в очереди» до создания заявки media3:
     * резолв потока — сетевой запрос, бейдж иначе появляется лишь после
     * resolve + старта сервиса + тика опроса.
     */
    fun markQueued(urls: Set<String>) {
        if (urls.isEmpty()) return
        optimisticQueued.addAll(urls)
        // Прерываем сон опроса — метка должна появиться сразу, а не через 500 мс.
        wakeUp()
    }

    /** Резолв не удался — снимаем оптимистичную метку (и будим опрос). */
    fun unmarkQueued(url: String) {
        if (optimisticQueued.remove(url)) wakeUp()
    }

    /** Заголовки серий для уведомлений: url → title, порядок вставки сохраняется. */
    fun registerChapterTitles(titles: Map<String, String>) {
        if (titles.isEmpty()) return
        chapterTitles.putAll(titles)
    }

    /**
     * Текст «что именно качается»: первые две названия через запятую,
     * остальные — суффикс «· ещё N». null — заголовков нет, вызывающий
     * contentText не трогает. Порядок — порядок [registerChapterTitles]
     * (вставки), не сортировка.
     */
    fun activeChaptersText(urls: List<String>): String? {
        if (urls.isEmpty()) return null
        val wanted = urls.toHashSet()
        val titles = synchronized(chapterTitles) {
            chapterTitles.entries.filter { it.key in wanted }.map { it.value }
        }
        return chaptersListText(titles) { rest ->
            context.getString(R.string.download_active_chapters_more, rest)
        }
    }

    /** Ставит эпизод в media3-очередь; id заявки = chapterUrl. */
    suspend fun enqueue(chapterUrl: String, video: VideoSource) = withContext(Dispatchers.IO) {
        // Новая заявка отменяет метку снятия: remove() ещё может не пройти
        // у media3, а бейдж уже должен вернуться.
        pendingRemoval.remove(chapterUrl)
        val existing = runCatching { downloadManager.downloadIndex.getDownload(chapterUrl) }.getOrNull()
        if (existing != null && existing.state == Download.STATE_COMPLETED) return@withContext
        val builder = DownloadRequest.Builder(chapterUrl, Uri.parse(video.url))
        // Тип: mime от плагина, иначе — как раньше, по расширению. HLS качает
        // HlsDownloader (плейлист+сегменты), иначе — один файл Progressive-ом;
        // заявка несёт mime дальше — он возвращается в completedVideo().
        val mime = video.mime ?: MimeTypes.APPLICATION_M3U8.takeIf { video.url.contains(".m3u8") }
        if (mime != null) builder.setMimeType(mime)
        if (video.headers.isNotEmpty()) builder.setData(encodeHeaders(video.headers))
        val request = builder.build()
        if (pausedAll) {
            // Глобальная пауза: НЕ через сервис. sendAddDownload(foreground=true)
            // поднял бы FGS на каждую главу (мигание шторки), и глава успела бы
            // качнуться до пер-главной паузы — юзер видел «паузу по 1».
            // Прямой addDownload кладёт заявку в память и индекс сразу
            // STATE_STOPPED/STOP_REASON_PAUSED: сервис не поднимается
            // (needsStartedService(STOPPED) == false), байты не идут, add идёт
            // в тот же handler FIFO — без гонок. resumeAll подхватит:
            // setStopReason(null, NONE) + startForeground.
            downloadManager.addDownload(request, STOP_REASON_PAUSED)
        } else {
            DownloadService.sendAddDownload(
                context, VideoDownloadService::class.java, request, true
            )
            // Пауза могла пройти между проверкой выше и доставкой add-интента:
            // обе команды уходят в один работающий сервис FIFO (сначала add,
            // потом stop) — глава останется на паузе. Здесь, а не у вызывающего,
            // чтобы тот не дёргал service-старт сам.
            if (pausedAll) pause(chapterUrl)
        }
    }

    /**
     * Удаление: index + кэш-файлы снимает сам DownloadManager.
     *
     * Бейдж убирается оптимистично — иначе он висит ещё до 500 мс, пока
     * опрос не заметит, что заявка ушла. media3 доделывает удаление
     * асинхронно: до подтверждения заявка скрыта через [pendingRemoval],
     * чтобы тик опроса не вернул её из currentDownloads.
     */
    fun remove(chapterUrl: String) {
        pendingRemoval.add(chapterUrl)
        recentTerminal.remove(chapterUrl)
        // Метка снимается тоже: если заявка ещё не создана (оптимистичная),
        // тик опроса иначе вернул бы ghost-QUEUED.
        optimisticQueued.remove(chapterUrl)
        chapterTitles.remove(chapterUrl)
        _uiStates.update { it - chapterUrl }
        wakeUp()
        downloadManager.removeDownload(chapterUrl)
    }

    /**
     * Полная очистка всех офлайн-видео. Тот же экземпляр менеджера
     * отдаёт [VideoDownloadService.getDownloadManager], поэтому снятие
     * идёт через общий DownloadManager (index + кэш-файлы), без отдельного
     * вызова сервиса. Удаление асинхронно — файлы подчищает DownloadManager.
     */
    override fun removeAll() {
        pendingRemoval.addAll(downloadManager.currentDownloads.map { it.request.id })
        recentTerminal.clear()
        optimisticQueued.clear()
        chapterTitles.clear()
        _uiStates.update { emptyMap() }
        wakeUp()
        downloadManager.removeAllDownloads()
    }

    /**
     * Уведомление «на паузе»: заменяет пропавшее FGS-уведомление, когда
     * media3 гасит сервис после паузы всех заявок (onIdle → stopSelf).
     */
    private fun updatePausedNotification(downloads: List<Download>) {
        if (needsPausedNotification(downloads.map { it.state })) {
            showPausedNotificationIfChanged(
                downloads.filter { it.state == Download.STATE_STOPPED }.map { it.request.id }
            )
        } else if (pausedNotificationShown) {
            closePausedNotification()
        }
    }

    /**
     * Показ/перепоказ 1402, если набор паузнутых id изменился.
     *
     * Без сравнения счётчик замерзал бы на значении оптимистичного показа
     * (пауза застала цикл в начале — «1 серия» при 40 фактически паузнутых).
     */
    private fun showPausedNotificationIfChanged(pausedUrls: List<String>) {
        if (pausedNotificationShown && pausedUrls == pausedNotificationUrls) return
        showPausedNotification(pausedUrls)
    }

    /** Немедленное закрытие 1402: после Стоп/возобновления оно stale. */
    private fun closePausedNotification() {
        // Без проверки shown: 1402 переживает рестарт процесса (stale из
        // прошлой сессии), cancel несуществующего id — no-op.
        notificationsCenter.close(PAUSED_NOTIFICATION_ID)
        pausedNotificationShown = false
        pausedNotificationUrls = emptyList()
    }

    /**
     * Показ уведомления «на паузе» по уже отобранному списку id (порядок —
     * порядок заявок, как в [registerChapterTitles]).
     *
     * Отбор делает вызывающий: поллинг фильтрует по состоянию, а pauseAll()
     * показывает оптимистично — setStopReason ещё не дошёл до handler'а и
     * state на момент вызова не STOPPED.
     */
    private fun showPausedNotification(pausedUrls: List<String>) {
        val pausedCount = pausedUrls.size
        val pausedText = context.getString(R.string.download_paused_count, pausedCount)
        // Названия серий — какие именно на паузе. Нет заголовков — прежний
        // вид: заголовок «Загрузки» + счётчик в тексте; есть — счётчик
        // переезжает в заголовок, текст отдаётся под названия.
        val chaptersText = activeChaptersText(pausedUrls)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        notificationsCenter.showNotification(
            channelId = PAUSED_CHANNEL_ID,
            // Имя канала видно в системных настройках — строковый ресурс,
            // как у остальных каналов (notification_channel_name_*).
            channelName = context.getString(R.string.notification_channel_name_video_downloads_paused),
            notificationId = PAUSED_NOTIFICATION_ID,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(
                if (chaptersText != null) pausedText
                else context.getString(R.string.download_progress_title)
            )
            setContentText(chaptersText ?: pausedText)
            contentIntent?.let { setContentIntent(it) }
            // Можно смахнуть: фоновых работ всё равно нет.
            setOngoing(false)
            setShowWhen(false)
            addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.download_resume),
                broadcastPendingIntent(
                    VideoDownloadNotificationReceiver.ACTION_RESUME_ALL,
                    REQUEST_CODE_RESUME_ALL,
                ),
            )
            addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.download_cancel),
                broadcastPendingIntent(
                    VideoDownloadNotificationReceiver.ACTION_CANCEL_ALL,
                    REQUEST_CODE_CANCEL_ALL,
                ),
            )
        }
        pausedNotificationShown = true
        pausedNotificationUrls = pausedUrls
    }

    /** Явный компонент receiver'а: intent-filter для кнопок уведомления не нужен. */
    private fun broadcastPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, VideoDownloadNotificationReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Шаг 5: источник для полностью скачанного эпизода — без сети и без
     * заголовков. Плеер играет uri из заявки через общий [downloadCache]
     * (см. [playerDataSourceFactory]); file:// тут невозможен — HLS
     * скачивается набором файлов, единого локального URL у media3 нет.
     */
    suspend fun completedVideo(chapterUrl: String): VideoSource? = withContext(Dispatchers.IO) {
        val download = runCatching { downloadManager.downloadIndex.getDownload(chapterUrl) }
            .getOrNull() ?: return@withContext null
        if (download.state != Download.STATE_COMPLETED) return@withContext null
        // mime заявки несём обратно: без него плеер по расширению uri (у HLS
        // его часто нет) снова выберет прогрессивный источник.
        VideoSource(url = download.request.uri.toString(), mime = download.request.mimeType)
    }

    /**
     * Тот же кэш, что у загрузчика: скачанное читается, остальное — из сети.
     * Запись из плеера выключена (дока media3 downloading-media): онлайн-
     * воспроизведение не должно заливать байты в filesDir под NoOpCacheEvictor
     * (докачивает только DownloadService); промах читается через upstream.
     */
    fun playerDataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
}

/** Фабрика с заголовками конкретной заявки (см. [encodeHeaders]). */
@UnstableApi
private class HeaderAwareDownloaderFactory(
    private val cache: SimpleCache,
    private val client: OkHttpClient,
) : DownloaderFactory {
    override fun createDownloader(request: DownloadRequest): Downloader {
        val upstream = OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(decodeHeaders(request.data))
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        val mediaItem = request.toMediaItem()
        return if (request.mimeType == MimeTypes.APPLICATION_M3U8) {
            HlsDownloader.Factory(dataSourceFactory).create(mediaItem)
        } else {
            ProgressiveDownloader(mediaItem, dataSourceFactory)
        }
    }
}

// ponytail: line-based кодировка заголовков вместо JSON — значения HTTP-заголовков
// не содержат переводов строк; если понадобится больше структуры — kotlinx.serialization.
internal fun encodeHeaders(headers: Map<String, String>): ByteArray =
    headers.entries.joinToString("\n") { "${it.key}:${it.value}" }.encodeToByteArray()

internal fun decodeHeaders(data: ByteArray?): Map<String, String> {
    if (data == null || data.isEmpty()) return emptyMap()
    return data.decodeToString().lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }
        .toMap()
}

/**
 * Текст «что именно качается» для уведомления: первые два названия через
 * запятую, остальные — [moreSuffix]. Вызывающий подставляет локализованный
 * суффикс («· ещё N»), поэтому функция чистая и тестируется без Context.
 * null — названий нет, вызывающий contentText не трогает. Порядок [titles]
 * (порядок вставки, не сортировка) сохраняется.
 */
internal fun chaptersListText(titles: List<String>, moreSuffix: (Int) -> String): String? {
    if (titles.isEmpty()) return null
    val head = titles.take(2).joinToString(", ")
    val rest = titles.size - 2
    return if (rest > 0) head + " " + moreSuffix(rest) else head
}

/**
 * Маппинг состояния media3 в бейдж главы.
 *
 * Проценты — только у активной загрузки: у QUEUED/PAUSED/терминалов прогресс
 * неизвестен (null = индетерминированный). media3 не имеет STATE_STOPPING —
 * останавливающаяся загрузка на момент опроса обычно уже в STOPPED.
 */
internal fun chapterDownloadUiOf(download: Download): ChapterDownloadUi =
    chapterDownloadUiOf(download.state, download.stopReason, download.percentDownloaded)

/** Тот же маппинг без объекта [Download] — чистая функция для тестов. */
internal fun chapterDownloadUiOf(
    state: Int,
    stopReason: Int,
    percentDownloaded: Float,
): ChapterDownloadUi {
    val uiState = when (state) {
        Download.STATE_QUEUED -> ChapterDownloadUiState.QUEUED
        Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> ChapterDownloadUiState.DOWNLOADING
        Download.STATE_STOPPED ->
            if (stopReason != Download.STOP_REASON_NONE) ChapterDownloadUiState.PAUSED
            else ChapterDownloadUiState.QUEUED

        Download.STATE_COMPLETED -> ChapterDownloadUiState.COMPLETED
        Download.STATE_FAILED -> ChapterDownloadUiState.FAILED
        else -> ChapterDownloadUiState.NONE
    }
    val progress = if (uiState == ChapterDownloadUiState.DOWNLOADING) {
        percentDownloaded.takeIf { it >= 0f }?.div(100f)?.coerceIn(0f, 1f)
    } else null
    return ChapterDownloadUi(state = uiState, progress = progress)
}

/**
 * Нужно ли наше уведомление «на паузе»: есть остановленные заявки и ни одной
 * активной. STATE_STOPPED в currentDownloads возможен только с ненулевым
 * stopReason (после resumeAll state становится QUEUED), а активные состояния —
 * те, при которых media3 ещё держит сервис и его FGS-уведомление живым.
 */
@UnstableApi
internal fun needsPausedNotification(states: List<Int>): Boolean =
    Download.STATE_STOPPED in states &&
        states.none { it in ACTIVE_DOWNLOAD_STATES }

// Состояния, при которых FGS-уведомление сервиса ещё присутствует.
// media3 не имеет STATE_ERROR — набор сверен с needsStartedService(1.11.1).
@UnstableApi
private val ACTIVE_DOWNLOAD_STATES = setOf(
    Download.STATE_QUEUED,
    Download.STATE_DOWNLOADING,
    Download.STATE_RESTARTING,
    Download.STATE_REMOVING,
)

// Пределы DownloadManager: два параллельных потока не забивают источник,
// 3 ретрая — баланс между честностью и бесконечным циклом на мёртвых ссылках.
private const val MAX_PARALLEL_DOWNLOADS = 2
private const val MIN_RETRY_COUNT = 3

// Опрос процентов: media3 не шлёт onDownloadChanged на каждый тик.
private const val POLL_INTERVAL_MS = 500L

// Уведомление «на паузе»: id рядом с FOREGROUND_NOTIFICATION_ID сервиса (1401),
// своя канал — чтобы не смешивать с FGS-каналом video_downloads.
// Имя канала — ресурс notification_channel_name_video_downloads_paused.
private const val PAUSED_NOTIFICATION_ID = 1402
private const val PAUSED_CHANNEL_ID = "video_downloads_paused"

// Request-коды PendingIntent кнопок: уникальны на действие, иначе Android
// считает их одним и тем же интентом и подменяет extras.
private const val REQUEST_CODE_RESUME_ALL = 1
private const val REQUEST_CODE_CANCEL_ALL = 2
