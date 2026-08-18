package my.noveldokusha.features.reader.manager

import kotlinx.coroutines.channels.Channel
import my.noveldokusha.features.reader.ui.ReaderViewHandlersActions
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ReaderManager @Inject constructor(
    private val readerSessionProvider: ReaderSessionProvider,
    private val readerViewHandlersActions: ReaderViewHandlersActions
) {
    var session: ReaderSession? = null
        private set

    val onCloseRequested = Channel<Unit>(Channel.CONFLATED)

    fun initiateOrGetSession(
        bookUrl: String,
        chapterUrl: String,
    ): ReaderSession {
        Timber.d("ReaderStart: initiateOrGetSession bookUrl=$bookUrl chapterUrl=$chapterUrl")
        val currentSession = session
        if (currentSession != null && bookUrl == currentSession.bookUrl && chapterUrl == currentSession.currentChapter.chapterUrl) {
            Timber.d("ReaderStart: reusing session")
            readerViewHandlersActions.introScrollToCurrentChapter = true
            return currentSession
        }

        currentSession?.close()
        readerViewHandlersActions.introScrollToCurrentChapter = false

        Timber.d("ReaderStart: creating new session initialChapterUrl=$chapterUrl")
        val newSession = readerSessionProvider.create(
            bookUrl = bookUrl,
            initialChapterUrl = chapterUrl,
        )
        session = newSession
        newSession.init()

        return newSession
    }

    fun close() {
        Timber.d("ReaderManager.close")
        session?.close()
        session = null
    }

    fun closeReader() {
        close()
        onCloseRequested.trySend(Unit)
    }

    fun detachSession() {
        Timber.d("ReaderManager.detachSession")
        readerViewHandlersActions.invalidate()
    }
}
