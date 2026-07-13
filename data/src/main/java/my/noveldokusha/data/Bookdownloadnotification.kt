package my.noveldokusha.data

import timber.log.Timber
import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import my.noveldokusha.coreui.states.NotificationsCenter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Управляет уведомлением одной книги в очереди загрузки.
 *
 * Каждый экземпляр изолирован: имеет собственный [notificationId] и [builder].
 * Разные книги не могут повлиять на уведомление друг друга.
 *
 * Жизненный цикл:
 *   enqueue → showQueued / showDownloading
 *   pause   → showPaused
 *   resume  → showDownloading
 *   cancel  → showCancelled → (auto-close after 2s)
 *   complete → showCompleted  ← висит пока пользователь не нажмёт Dismiss
 *   dismiss → close
 *
 * QA-контракты:
 * - [notificationId] уникален глобально — выдаётся из AtomicInteger companion'а.
 * - requestCode PendingIntent = notificationId * ACTION_COUNT + actionIndex.
 *   ACTION_COUNT = 3 (PAUSE=0, RESUME=1, CANCEL=2). Dismiss — свайп, не кнопка.
 *   Коллизии между книгами невозможны: notificationId уникален.
 * - [builder] пересоздаётся только при смене состояния (pause/resume/complete/cancel).
 *   При обновлении прогресса используется [updateProgress] — кнопки не пересоздаются.
 * - После [close] объект больше не используется — [DownloadManager] удаляет его из map.
 */
class BookDownloadNotification(
    private val bookUrl: String,
    private val bookTitle: String,
    private val context: Context,
    private val notificationsCenter: NotificationsCenter,
) {
    val notificationId: Int = idCounter.getAndIncrement()

    // Текущий builder — переиспользуется для updateProgress чтобы не пересоздавать PendingIntent
    private var builder: NotificationCompat.Builder? = null

    // ── Публичные методы смены состояния ─────────────────────────────────────

    /** Книга добавлена в очередь, ещё не качается. */
    fun showQueued(task: DownloadTaskState) {
        if (!hasNotificationPermission()) return
        builder = build {
            setContentText("Queued: ${task.progressText}${translationSuffix(task)}")
            setOngoing(true)
            // Pause не имеет смысла пока книга в очереди — только Cancel
            if (task.totalCount > 0) {
                setProgress(task.totalCount, task.currentIndex, false)
            } else {
                // Количество глав ещё неизвестно — indeterminate
                setProgress(0, 0, true)
            }
            addCancelAction()
        }
    }

    /** Книга активно качается. */
    fun showDownloading(task: DownloadTaskState) {
        if (!hasNotificationPermission()) return
        builder = build {
            setContentText("Downloading: ${task.progressText}${translationSuffix(task)}")
            setOngoing(true)
            if (task.totalCount > 0) {
                setProgress(task.totalCount, task.currentIndex, false)
            } else {
                setProgress(0, 0, true)
            }
            addPauseAction()
            addCancelAction()
        }
    }

    /**
     * Обновляет только прогресс-бар и текст.
     * Кнопки и PendingIntent НЕ пересоздаются — нет мигания уведомления.
     * Если builder ещё нет (первый вызов) — создаёт полное уведомление.
     */
    fun updateProgress(task: DownloadTaskState) {
        if (!hasNotificationPermission()) return
        val current = builder
        if (current == null) {
            showDownloading(task)
            return
        }
        notificationsCenter.modifyNotification(current, notificationId) {
            setContentText("Downloading: ${task.progressText}${translationSuffix(task)}")
            if (task.totalCount > 0) {
                setProgress(task.totalCount, task.currentIndex, false)
            } else {
                setProgress(0, 0, true)
            }
        }
    }

    /** Загрузка поставлена на паузу. */
    fun showPaused(task: DownloadTaskState) {
        if (!hasNotificationPermission()) return
        builder = build {
            setContentText("Paused: ${task.progressText}${translationSuffix(task)}")
            setOngoing(false)   // можно смахнуть
            setProgress(task.totalCount, task.currentIndex, false)
            addResumeAction()
            addCancelAction()
        }
    }

    /** Ожидание восстановления сети (DNS/соединение временно недоступно). */
    fun showWaitingForNetwork(task: DownloadTaskState) {
        if (!hasNotificationPermission()) return
        builder = build {
            setContentText("Waiting for network: ${task.progressText}${translationSuffix(task)}")
            setOngoing(true)
            setProgress(task.totalCount, task.currentIndex, false)
            // Только Cancel — не Pause, задача продолжает retry
            addCancelAction()
        }
    }

    /**
     * Загрузка завершена успешно.
     * Уведомление остаётся пока пользователь не смахнёт или не нажмёт Dismiss в UI.
     * Кнопок действий нет — задача завершена.
     */
    fun showCompleted(task: DownloadTaskState) {
        if (!hasNotificationPermission()) return
        builder = build {
            setContentText("Completed: ${task.successCount} chapters${translationSuffix(task)}")
            setOngoing(false)
            setProgress(0, 0, false)
            // Кнопок нет — смахнуть или dismiss из UI
        }
    }

    /**
     * Загрузка отменена.
     * Уведомление автоматически закрывается через 2 секунды.
     */
    fun showCancelled() {
        if (!hasNotificationPermission()) return
        // Не сохраняем в builder — уведомление временное
        notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            notificationId = notificationId,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(bookTitle)
            setContentText("Download cancelled")
            setOngoing(false)
            setProgress(0, 0, false)
        }
    }

    /** Закрывает уведомление. Вызывается из dismiss() или после auto-close cancel. */
    fun close() {
        notificationsCenter.close(notificationId)
        builder = null
    }

    // ── Внутренние хелперы ───────────────────────────────────────────────────

    /**
     * Проверяет наличие разрешения POST_NOTIFICATIONS на Android 13+.
     * H4: На Android 13+ (TIRAMISU) без разрешения уведомления не показываются.
     * Использует NotificationManagerCompat.notify() который тихо игнорирует вызов
     * при отсутствии разрешения, но для надёжности проверяем явно.
     */
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val result = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (result != PackageManager.PERMISSION_GRANTED) {
            Timber.w("POST_NOTIFICATIONS denied, skipping notification for $bookUrl")
            return false
        }
        return true
    }

    /**
     * Возвращает суффикс для отображения ошибок перевода.
     * H5: Если translationErrorCount > 0, добавляет предупреждение в текст уведомления.
     */
    private fun translationSuffix(task: DownloadTaskState): String {
        return if (task.translationErrorCount > 0) {
            " | Translation errors: ${task.translationErrorCount}"
        } else ""
    }

    /**
     * Создаёт новый builder и сразу показывает уведомление.
     * Вызывается при смене состояния — пересоздаёт кнопки с актуальными PendingIntent.
     */
    private fun build(block: NotificationCompat.Builder.() -> Unit): NotificationCompat.Builder {
        return notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            notificationId = notificationId,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(bookTitle)
            block()
        }
    }

    private fun NotificationCompat.Builder.addPauseAction() {
        addAction(
            android.R.drawable.ic_media_pause,
            "Pause",
            pendingIntent(DownloadNotificationReceiver.ACTION_PAUSE, ACTION_IDX_PAUSE)
        )
    }

    private fun NotificationCompat.Builder.addResumeAction() {
        addAction(
            android.R.drawable.ic_media_play,
            "Resume",
            pendingIntent(DownloadNotificationReceiver.ACTION_RESUME, ACTION_IDX_RESUME)
        )
    }

    private fun NotificationCompat.Builder.addCancelAction() {
        addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            pendingIntent(DownloadNotificationReceiver.ACTION_CANCEL, ACTION_IDX_CANCEL)
        )
    }

    /**
     * Стабильный PendingIntent.
     * requestCode = notificationId * ACTION_COUNT + actionIndex
     * Уникален: notificationId уникален per-book, actionIndex ∈ {0,1,2}.
     * Не меняется между вызовами — Android переиспользует существующий Intent.
     */
    private fun pendingIntent(action: String, actionIndex: Int): PendingIntent {
        val intent = Intent(context, DownloadNotificationReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadNotificationReceiver.EXTRA_BOOK_URL, bookUrl)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId * ACTION_COUNT + actionIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "chapter_downloads"
        const val CHANNEL_NAME = "Chapter Downloads"

        // Счётчик уникальных ID — общий для всех экземпляров
        private val idCounter = AtomicInteger(1000)

        // Индексы action — фиксированы, менять нельзя без пересчёта requestCode
        private const val ACTION_COUNT   = 3
        private const val ACTION_IDX_PAUSE  = 0
        private const val ACTION_IDX_RESUME = 1
        private const val ACTION_IDX_CANCEL = 2
    }
}