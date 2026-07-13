package my.noveldokusha.features.reader.tools

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import my.noveldokusha.data.AppRepository
import my.noveldokusha.features.reader.domain.ChapterUrl
import java.util.concurrent.ConcurrentHashMap

internal class ChaptersIsReadRoutine(
    private val appRepository: AppRepository,
    private val scope: CoroutineScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("ChapterIsReadRoutine")
    )
) {
    fun setReadStart(chapterUrl: String) = checkLoadStatus(chapterUrl) { it.copy(startSeen = true) }
    fun setReadEnd(chapterUrl: String) = checkLoadStatus(chapterUrl) { it.copy(endSeen = true) }

    private data class ChapterReadStatus(val startSeen: Boolean, val endSeen: Boolean)

    private val chapterRead = ConcurrentHashMap<ChapterUrl, ChapterReadStatus>()

    private fun checkLoadStatus(chapterUrl: String, fn: (ChapterReadStatus) -> ChapterReadStatus) =
        scope.launch {

            val chapter = appRepository.bookChapters.get(chapterUrl) ?: return@launch
            // Атомарный RMW через compute: исключает TOCTOU между чтением
            // старого статуса и последующей записью newStatus из параллельных корутин.
            val newStatus = chapterRead.compute(chapterUrl) { _, current ->
                val base = current ?: when (chapter.read) {
                    true -> ChapterReadStatus(startSeen = true, endSeen = true)
                    false -> ChapterReadStatus(startSeen = false, endSeen = false)
                }
            fn(base)
        } ?: return@launch

        if (newStatus.startSeen && newStatus.endSeen) {
                appRepository.bookChapters.setAsRead(chapterUrl = chapterUrl, read = true)
            }
        }
}