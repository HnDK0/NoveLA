package my.noveldokusha.tooling.epub_importer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.coreui.states.removeProgressBar
import my.noveldokusha.coreui.states.text
import my.noveldokusha.coreui.states.title
import my.noveldokusha.data.LocalBookImporterRepository
import my.noveldokusha.core.asSequence
import my.noveldokusha.core.isFb2File
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.core.utils.Extra_Uri
import my.noveldokusha.core.utils.isServiceRunning
import my.noveldokusha.epub_tooling.epubParser
import my.noveldokusha.epub_tooling.fb2Parser
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BookImportService : Service() {

    @Inject
    lateinit var notificationsCenter: NotificationsCenter

    @Inject
    lateinit var localBookImporterRepository: LocalBookImporterRepository

    private class IntentData : Intent {
        var uri by Extra_Uri()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, uri: Uri) : super(ctx, BookImportService::class.java) {
            this.uri = uri
        }
    }

    companion object {
        fun start(ctx: Context, uri: Uri) {
            if (!isRunning(ctx))
                ContextCompat.startForegroundService(ctx, IntentData(ctx, uri))
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(BookImportService::class.java)
    }

    private val channelName by lazy { getString(R.string.notification_channel_name_import_epub) }
    private val channelId = "book_import"
    private val notificationId get() = channelId.hashCode()
    private val failureNotificationId get() = (channelId + "_failure").hashCode()

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationBuilder = notificationsCenter.showNotification(
            notificationId = notificationId,
            channelId = channelId,
            channelName = channelName,
        )
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, notificationBuilder.build())
        if (intent == null) return START_NOT_STICKY
        val intentData = IntentData(intent)
        job = CoroutineScope(Dispatchers.IO).launch {
            tryAsResponse {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    title = getString(R.string.import_epub)
                    foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                    setProgress(100, 0, true)
                }
                val inputStream = contentResolver.openInputStream(intentData.uri)
                if (inputStream == null) {
                    notificationsCenter.showNotification(
                        channelName = channelName,
                        channelId = channelId,
                        notificationId = failureNotificationId
                    ) {
                        text = getString(R.string.failed_get_file)
                        removeProgressBar()
                    }
                    return@tryAsResponse
                }

                val fileName = contentResolver.query(
                    intentData.uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                    null
                ).asSequence().map { it.getString(0) }.last()

                val bookData = inputStream.use { stream ->
                    if (fileName.isFb2File()) fb2Parser(stream) else epubParser(stream)
                }

                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.importing_epub)
                }
                localBookImporterRepository.epubImporter(
                    storageFolderName = fileName,
                    epub = bookData,
                    addToLibrary = true
                )
            }.onError {
                Timber.e(it.exception)
                notificationsCenter.showNotification(
                    channelName = channelName,
                    channelId = channelId,
                    notificationId = failureNotificationId
                ) {
                    text = getString(R.string.failed_to_import_epub)
                    setSubText(it.message)
                    removeProgressBar()
                }
            }

            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}