package my.noveldokusha.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.coreui.states.NotificationsCenter
import javax.inject.Inject

/**
 * Foreground service для загрузок.
 *
 * Android 8+ требует foreground service для гарантии сетевого доступа
 * в Doze mode. Простой WakeLock (PARTIAL_WAKE_LOCK) не гарантирует
 * работу сети когда устройство в глубоком сне — foreground service даёт
 * минимальные гарантии от системы.
 *
 * Сервис:
 * - Стартует когда появляются активные загрузки
 * - Показывает постоянное уведомление "Загрузка..."
 * - Останавливается когда все загрузки завершены
 * - Удерживает WakeLock пока активен (дополнительная страховка)
 */
@AndroidEntryPoint
class DownloadForegroundService : android.app.Service() {

    @Inject lateinit var notificationsCenter: NotificationsCenter
    @Inject lateinit var downloadManager: DownloadManager

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    /**
     * Создаёт notification channel для foreground service notification.
     * Без этого Android 14+ кидает CannotPostForegroundServiceNotificationException
     * при вызове startForeground().
     *
     * Channel ID должен совпадать с BookDownloadNotification.CHANNEL_ID,
     * чтобы foreground notification был совместим с уведомлениями загрузок.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            BookDownloadNotification.CHANNEL_ID,
            BookDownloadNotification.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() ДОЛЖЕН быть первой операцией в onStartCommand
        // Android даёт ~5 секунд на вызов, иначе — ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, BookDownloadNotification.CHANNEL_ID)
            .setContentTitle("Novela")
            .setContentText("Downloading...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Novela:download_foreground"
            )?.apply {
                acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) { }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) { }
        wakeLock = null
    }

    companion object {
        private const val TAG = "DownloadFgService"
        const val NOTIFICATION_ID = 999

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
    }
}