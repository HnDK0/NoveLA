package my.noveldokusha.features.reader.tools

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import my.noveldokusha.data.AppRepository
import my.noveldokusha.features.reader.domain.ChapterUrl
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

internal class ChaptersIsReadRoutine(
    private val appRepository: AppRepository,
    private val scope: CoroutineScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("ChapterIsReadRoutine")
    )
) {
    fun setReadStart(chapterUrl: String) {
        Timber.d("setReadStart: $chapterUrl")
        // Дедупликация: markChapterStartAsSeen прилетает на каждый PLAYING/LOADING
        // абзаца — если флаг уже стоит, не ходим в БД (bookChapters.get).
        if (chapterRead[chapterUrl]?.startSeen == true) return
        checkLoadStatus(chapterUrl) { it.copy(startSeen = true) }
    }
    fun setReadEnd(chapterUrl: String) {
        Timber.d("setReadEnd: $chapterUrl")
        if (chapterRead[chapterUrl]?.endSeen == true) return
        checkLoadStatus(chapterUrl) { it.copy(endSeen = true) }
    }

    private data class ChapterReadStatus(val startSeen: Boolean, val endSeen: Boolean)

    private val chapterRead = ConcurrentHashMap<ChapterUrl, ChapterReadStatus>()

    private fun checkLoadStatus(chapterUrl: String, fn: (ChapterReadStatus) -> ChapterReadStatus) =
        scope.launch {

            val chapter = appRepository.bookChapters.get(chapterUrl)
            if (chapter == null) {
                Timber.d("checkLoadStatus: $chapterUrl -> chapter not found in DB")
                return@launch
            }
            // Атомарный RMW через compute: исключает TOCTOU между чтением
            // старого статуса и последующей записью newStatus из параллельных корутин.
            val newStatus = chapterRead.compute(chapterUrl) { _, current ->
                val base = current ?: when (chapter.read) {
                    true -> ChapterReadStatus(startSeen = true, endSeen = true)
                    false -> ChapterReadStatus(startSeen = false, endSeen = false)
                }
            fn(base)
        } ?: return@launch

        Timber.d("checkLoadStatus: $chapterUrl startSeen=${newStatus.startSeen} endSeen=${newStatus.endSeen}")

        if (newStatus.startSeen && newStatus.endSeen) {
                Timber.d("checkLoadStatus: calling setAsRead($chapterUrl)")
                appRepository.bookChapters.setAsRead(chapterUrl = chapterUrl, read = true)
            }
        }

    /**
     * Синхронно сбрасывает накопленные статусы глав в БД.
     * Вызывать перед [close] чтобы pending корутины не потеряли запись.
     */
    fun sync() = runBlocking(Dispatchers.IO) {
        Timber.d("sync: flushing ${chapterRead.size} chapters")
        chapterRead.forEach { (chapterUrl, status) ->
            Timber.d("sync: $chapterUrl startSeen=${status.startSeen} endSeen=${status.endSeen}")
            if (status.startSeen && status.endSeen) {
                Timber.d("sync: setAsRead($chapterUrl)")
                appRepository.bookChapters.setAsRead(chapterUrl, true)
            }
        }
    }

    fun close() {
        scope.cancel()
        chapterRead.clear()
    }
}