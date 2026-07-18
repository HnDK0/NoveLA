package my.noveldokusha.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DownloadNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onReceive(context: Context, intent: Intent) {
        val bookUrl = intent.getStringExtra(EXTRA_BOOK_URL) ?: return
        when (intent.action) {
            ACTION_PAUSE -> downloadManager.pause(bookUrl)
            ACTION_RESUME -> downloadManager.resume(bookUrl)
            ACTION_CANCEL -> downloadManager.cancel(bookUrl)
        }
    }

    companion object {
        const val ACTION_PAUSE = "my.noveldokusha.action.DOWNLOAD_PAUSE"
        const val ACTION_RESUME = "my.noveldokusha.action.DOWNLOAD_RESUME"
        const val ACTION_CANCEL = "my.noveldokusha.action.DOWNLOAD_CANCEL"
        const val EXTRA_BOOK_URL = "book_url"
    }
}