package my.noveldokusha.features.reader.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import my.noveldokusha.core.AppFileResolver
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.R as CoreUiR
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.coreui.states.text
import my.noveldokusha.coreui.states.title
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.features.reader.ReaderActivity
import my.noveldokusha.features.reader.domain.chapterReadPercentage
import my.noveldokusha.features.reader.manager.ReaderManager
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.reader.R
import javax.inject.Inject

internal class NarratorMediaControlsNotification @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationsCenter: NotificationsCenter,
    private val readerManager: ReaderManager,
    private val navigationRoutes: NavigationRoutes,
    private val appFileResolver: AppFileResolver,
) {
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("NarratorNotificationService")
    )

    private val channelName = context.getString(R.string.notification_channel_name_reader_narrator)
    private val channelId = "Reader narrator v2"
    private val mediaTagDebug = "NoveLA_narratorMediaControls"
    val notificationId: Int = channelId.hashCode()

    private var mediaSession: MediaSessionCompat? = null
    private var currentChapterTitle: String? = null
    private var currentBookTitle: String? = null
    private var currentCoverBitmap: Bitmap? = null

    private fun refreshMediaSessionMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
        currentChapterTitle?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it) }
        currentBookTitle?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it) }
        currentCoverBitmap?.let {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
        }
        mediaSession?.setMetadata(builder.build())
    }

    private suspend fun loadCoverBitmap(coverUrl: String?, bookUrl: String): Bitmap? {
        if (coverUrl.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = Coil.imageLoader(context)
                val localCover = appFileResolver.resolvedBookImagePath(bookUrl, coverUrl, isCover = true)
                val request = ImageRequest.Builder(context)
                    .data(localCover)
                    .size(Size(512, 512))
                    .allowHardware(false)
                    .build()
                val bitmap = when (val result = imageLoader.execute(request)) {
                    is SuccessResult -> result.drawable.toBitmap()
                    else -> null
                } ?: return@withContext null
                if (bitmap.byteCount > 900_000) {
                    val scale = kotlin.math.sqrt(900_000.0 / bitmap.byteCount)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun handleCommand(intent: Intent?) {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
    }

    fun createNotificationMediaControls(context: Context): Notification? {

        val readerSession = readerManager.session ?: return null

        val mbrIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_PLAY_PAUSE
        )
        val session = MediaSessionCompat(
            context,
            mediaTagDebug,
            ComponentName(context, MediaButtonReceiver::class.java),
            mbrIntent
        ).apply {
            currentBookTitle = readerSession.bookTitle
            val initialChapterTitle = readerSession.readerTextToSpeech.currentTextPlaying.value
                .let { readerSession.readerChaptersLoader.chaptersStats[it.itemPos.chapterUrl] }
                ?.chapter?.title
            currentChapterTitle = initialChapterTitle
            // https://stackoverflow.com/questions/59443133/disable-or-hide-seekbar-in-mediastyle-notifications
            refreshMediaSessionMetadata()

            setCallback(NarratorMediaControlsCallback(readerSession.readerTextToSpeech))
            isActive = true
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)

            val initialIsPlaying = readerSession.readerTextToSpeech.state.isPlaying.value
            val playbackState = if (initialIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_FAST_FORWARD
                )
                .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (initialIsPlaying) 1.0f else 0.0f)
            setPlaybackState(stateBuilder.build())
        }
        this.mediaSession = session

        val mbrPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(context, MediaButtonReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session.setMediaButtonReceiver(mbrPendingIntent)

        val cancelButton = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_STOP
        )

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowCancelButton(true)
            .setShowActionsInCompactView(0, 2, 4)
            .setCancelButtonIntent(cancelButton)
            .setMediaSession(session.sessionToken)

        val readerIntent = ReaderActivity.IntentData(
            ctx = context,
            bookUrl = readerSession.bookUrl,
            chapterUrl = readerSession.currentChapter.chapterUrl,
            scrollToSpeakingItem = true
        )
        readerIntent.setFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        val chain = listOf(
            navigationRoutes.main(context)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            navigationRoutes.chapters(
                context,
                BookMetadata(
                    url = readerSession.bookUrl,
                    title = readerSession.bookTitle ?: ""
                )
            ),
            readerIntent
        )

        fun generateIntentStack() = PendingIntent.getActivities(
            context,
            readerSession.bookUrl.hashCode(),
            chain.toTypedArray(),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionIntentPrevious = NotificationCompat.Action(
            R.drawable.ic_media_control_previous,
            context.getString(R.string.media_control_previous),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
        )
        val actionIntentRewind = NotificationCompat.Action(
            R.drawable.ic_media_control_rewind,
            context.getString(R.string.media_control_rewind),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_REWIND
            )
        )
        val actionIntentPause = NotificationCompat.Action(
            R.drawable.ic_media_control_pause,
            context.getString(R.string.media_control_play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_PAUSE
            )
        )
        val actionIntentPlay = NotificationCompat.Action(
            R.drawable.ic_media_control_play,
            context.getString(R.string.media_control_pause),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_PLAY
            )
        )
        val actionIntentFastForward = NotificationCompat.Action(
            R.drawable.ic_media_control_fast_forward,
            context.getString(R.string.media_control_fast_forward),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_FAST_FORWARD
            )
        )
        val actionIntentNext = NotificationCompat.Action(
            R.drawable.ic_media_control_next,
            context.getString(R.string.media_control_next),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
        )

        fun NotificationCompat.Builder.defineActions(
            isPlaying: Boolean
        ) {
            clearActions()
            addAction(actionIntentPrevious)
            addAction(actionIntentRewind)
            if (isPlaying) {
                addAction(actionIntentPause)
            } else {
                addAction(actionIntentPlay)
            }
            addAction(actionIntentFastForward)
            addAction(actionIntentNext)
        }

        val notificationBuilder = notificationsCenter.showNotification(
            channelId = channelId,
            channelName = channelName,
            notificationId = notificationId,
            importance = NotificationManager.IMPORTANCE_LOW,
            channelConfig = { setLockscreenVisibility(Notification.VISIBILITY_PUBLIC) }
        ) {
            title = ""
            text = ""
            defineActions(isPlaying = readerSession.readerTextToSpeech.state.isPlaying.value)
            setOngoing(true)
            setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            priority = NotificationCompat.PRIORITY_HIGH
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setColorized(false)
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_logo))
            setStyle(mediaStyle)
            setDeleteIntent(cancelButton)
            color = ContextCompat.getColor(context, CoreUiR.color.colorAccent)
            setContentIntent(generateIntentStack())
        }

        // Update reader speaking state
        scope.launch {
            snapshotFlow { readerSession.readerTextToSpeech.state.isPlaying.value }
                .collectLatest { isPlaying ->
                    notificationsCenter.modifyNotification(
                        builder = notificationBuilder,
                        notificationId = notificationId,
                    ) {
                        defineActions(isPlaying = isPlaying)
                    }
                    val playbackState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                    val stateBuilder = PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_REWIND or
                            PlaybackStateCompat.ACTION_FAST_FORWARD
                        )
                        .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (isPlaying) 1.0f else 0.0f)
                    this@NarratorMediaControlsNotification.mediaSession?.setPlaybackState(stateBuilder.build())
                }
        }

        // Update chapter notification title + media session title
        scope.launch {
            snapshotFlow { readerSession.readerTextToSpeech.currentTextPlaying.value }
                .mapNotNull { readerSession.readerChaptersLoader.chaptersStats[it.itemPos.chapterUrl] }
                .map { it.chapter.title }
                .distinctUntilChanged()
                .collectLatest { chapterTitle ->
                    currentChapterTitle = chapterTitle
                    refreshMediaSessionMetadata()
                    notificationsCenter.modifyNotification(
                        builder = notificationBuilder,
                        notificationId = notificationId,
                    ) {
                        title = chapterTitle
                    }
                }
        }

        // Update chapter notification intent
        scope.launch {
            snapshotFlow { readerSession.readerTextToSpeech.currentTextPlaying.value }
                .mapNotNull { readerSession.readerChaptersLoader.chaptersStats[it.itemPos.chapterUrl] }
                .map { it.chapter.url }
                .distinctUntilChanged()
                .collectLatest { chapterUrl ->
                    readerIntent.chapterUrl = chapterUrl
                    notificationsCenter.modifyNotification(
                        builder = notificationBuilder,
                        notificationId = notificationId,
                    ) {
                        setContentIntent(generateIntentStack())
                    }
                }
        }


        // Update chapter progress and remaining time
        scope.launch {
            combine(
                snapshotFlow { readerSession.speakerStats.value },
                snapshotFlow { readerSession.readerTextToSpeech.state.estimatedRemainingSeconds.value },
            ) { stats, remainingSecs -> stats to remainingSecs }
                .collectLatest { pair ->
                    val stats = pair.first ?: return@collectLatest
                    val remainingSecs = pair.second
                    val chapterPos = context.getString(
                        R.string.chapter_x_over_n,
                        stats.chapterIndex + 1,
                        stats.chapterCount
                    )
                    val progress = context.getString(
                        R.string.progress_x_percentage,
                        stats.chapterReadPercentage()
                    )
                    val remainingStr = if (remainingSecs > 0) {
                        "  •  -${formatDuration(remainingSecs)}"
                    } else ""
                    notificationsCenter.modifyNotification(
                        builder = notificationBuilder,
                        notificationId = notificationId,
                    ) {
                        text = "$chapterPos  $progress$remainingStr"
                    }
                }
        }

        // Load cover image and update session metadata + notification
        scope.launch {
            val coverBitmap = loadCoverBitmap(readerSession.bookCoverUrl, readerSession.bookUrl)
            if (coverBitmap != null) {
                currentCoverBitmap = coverBitmap
                refreshMediaSessionMetadata()
                notificationsCenter.modifyNotification(
                    builder = notificationBuilder,
                    notificationId = notificationId,
                ) {
                    setLargeIcon(coverBitmap)
                }
            }
        }

        return notificationBuilder.build()
    }

    fun close() {
        mediaSession?.release()
        mediaSession = null
        scope.cancel()
    }

    fun createDefaultNotification(context: Context): Notification {
        return notificationsCenter.showNotification(
            channelId = channelId,
            channelName = channelName,
            notificationId = notificationId,
            importance = NotificationManager.IMPORTANCE_LOW
        ) {
            title = context.getString(R.string.app_name)
            text = context.getString(R.string.notification_channel_name_reader_narrator)
            setOngoing(true)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.ic_media_control_play)
        }.build()
    }
}

private fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60

    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}