package my.noveldokusha.tooling.application_workers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.strings.R as StringsR
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Уведомление прогресса экспорта книги в EPUB.
 *
 * Жизненный цикл:
 *   showProgress (раз в 5 глав) → showComplete → close
 *
 * Каждый экземпляр изолирован: собственный [notificationId] из общего
 * AtomicInteger-счётчика. requestCode PendingIntent = notificationId —
 * коллизий между экспортами нет.
 */
class BookExportNotification(
    private val bookTitle: String,
    private val context: Context,
    private val notificationsCenter: NotificationsCenter,
) {
    val notificationId: Int = idCounter.getAndIncrement()

    // Переиспользуется для обновления прогресса, чтобы не пересоздавать PendingIntent
    private var builder: NotificationCompat.Builder? = null

    /** Имя канала уведомлений — из ресурсов (локализуется). */
    private val channelName = context.getString(StringsR.string.book_export_channel_name)

    fun showProgress(current: Int, total: Int) {
        if (!hasNotificationPermission()) return
        val currentBuilder = builder
        if (currentBuilder == null) {
            builder = notificationsCenter.showNotification(
                channelId = CHANNEL_ID,
                channelName = channelName,
                notificationId = notificationId,
                importance = NotificationManager.IMPORTANCE_LOW,
            ) {
                setContentTitle(bookTitle)
                setContentText(context.getString(StringsR.string.book_export_progress, current, total))
                setProgress(total, current, false)
                setOngoing(true)
                addCancelAction()
            }
            return
        }
        notificationsCenter.modifyNotification(currentBuilder, notificationId) {
            setContentText(context.getString(StringsR.string.book_export_progress, current, total))
            setProgress(total, current, false)
        }
    }

    fun showComplete(displayName: String, uri: Uri?) {
        if (!hasNotificationPermission()) return
        // Отдельный id от FGS-уведомления: WorkManager снимает своё уведомление
        // при завершении работы (Removing Notification id=N в логах) — иначе
        // «Экспорт завершён» исчезало бы сразу после показа.
        val completeId = completeIdCounter.getAndIncrement()
        builder = notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = channelName,
            notificationId = completeId,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(bookTitle)
            setContentText(context.getString(StringsR.string.book_export_complete, displayName))
            setOngoing(false)
            setAutoCancel(true)
            if (uri != null) {
                // Тап по уведомлению открывает файл; если для URI нет Activity —
                // уведомление строится без контент-интента (runCatching), чтобы не ронять экспорт.
                buildOpenContentIntent(uri)?.let { setContentIntent(it) }
                addOpenAction(uri, completeId)
                addDeleteAction(uri, completeId)
            }
        }
    }

    /**
     * Уведомление для foreground-сервиса экспорта: тот же ID и канал, что и у
     * прогресса, поэтому по ходу экспорта обновляется без смены уведомления.
     */
    fun foregroundNotification(total: Int): Notification =
        notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = channelName,
            notificationId = notificationId,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(bookTitle)
            setContentText(context.getString(StringsR.string.book_export_progress, 0, total))
            setOngoing(true)
        }.build()

    /** Показ ошибки экспорта (например, нет глав для экспорта): auto-cancel. */
    fun showError(message: String) {
        if (!hasNotificationPermission()) return
        builder = notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = channelName,
            notificationId = notificationId,
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            setContentTitle(bookTitle)
            setContentText(message)
            setOngoing(false)
            setAutoCancel(true)
        }
    }

    fun close() {
        notificationsCenter.close(notificationId)
        builder = null
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val result = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (result != PackageManager.PERMISSION_GRANTED) {
            Timber.w("POST_NOTIFICATIONS denied, skipping notification for book export")
            return false
        }
        return true
    }

    private fun NotificationCompat.Builder.addCancelAction() {
        addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(StringsR.string.book_export_cancel),
            PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, BookExportNotificationReceiver::class.java).apply {
                    action = BookExportNotificationReceiver.ACTION_CANCEL_EXPORT
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    /** PendingIntent открытия файла по тапу на уведомлении. */
    private fun buildOpenContentIntent(uri: Uri): PendingIntent? = runCatching {
        PendingIntent.getActivity(
            context.applicationContext,
            notificationId,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }.getOrNull()

    private fun NotificationCompat.Builder.addOpenAction(uri: Uri, actionId: Int) {
        addAction(
            android.R.drawable.ic_menu_view,
            // ponytail: хардкод до локализации — строки в этой задаче трогать нельзя
            "Open",
            PendingIntent.getBroadcast(
                context,
                actionId,
                Intent(context, BookExportNotificationReceiver::class.java).apply {
                    action = BookExportNotificationReceiver.ACTION_OPEN
                    putExtra(BookExportNotificationReceiver.EXTRA_URI, uri.toString())
                    putExtra(BookExportNotificationReceiver.EXTRA_NOTIFICATION_ID, actionId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun NotificationCompat.Builder.addDeleteAction(uri: Uri, actionId: Int) {
        addAction(
            android.R.drawable.ic_menu_delete,
            // ponytail: хардкод до локализации — строки в этой задаче трогать нельзя
            "Delete",
            PendingIntent.getBroadcast(
                context,
                actionId,
                Intent(context, BookExportNotificationReceiver::class.java).apply {
                    action = BookExportNotificationReceiver.ACTION_DELETE
                    putExtra(BookExportNotificationReceiver.EXTRA_URI, uri.toString())
                    putExtra(BookExportNotificationReceiver.EXTRA_NOTIFICATION_ID, actionId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        const val CHANNEL_ID = "book_export"

        /** MIME-тип экспортируемого EPUB — совпадает с createDocument в BookExportWorker. */
        const val MIME_TYPE = "application/epub+zip"

        // Счётчик уникальных ID — общий для всех экземпляров
        private val idCounter = AtomicInteger(2000)

        // ID финального уведомления — отдельный диапазон, чтобы WorkManager,
        // снимающий FGS-уведомление, не удалил «Экспорт завершён».
        private val completeIdCounter = AtomicInteger(3000)
    }
}