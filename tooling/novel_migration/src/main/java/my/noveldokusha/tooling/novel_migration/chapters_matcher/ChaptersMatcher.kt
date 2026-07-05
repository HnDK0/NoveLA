package my.noveldokusha.tooling.novel_migration.chapters_matcher

import my.noveldokusha.scraper.domain.ChapterResult

data class MatchResult(
    val matched: List<Pair<ChapterResult, ChapterResult>>,
    val unmatchedOld: List<ChapterResult>,
    val unmatchedNew: List<ChapterResult>,
    val score: Float,
)

private data class ChapterId(val number: Float, val volume: Int?)

class ChaptersMatcher {

    fun match(old: List<ChapterResult>, new: List<ChapterResult>): MatchResult {
        val matched = mutableListOf<Pair<ChapterResult, ChapterResult>>()
        val oldRemaining = old.toMutableList()
        val newRemaining = new.toMutableList()

        val newById = mutableMapOf<ChapterId, MutableList<ChapterResult>>()
        for (ch in new) {
            val num = extractChapterNumber(ch.title) ?: continue
            val vol = extractVolume(ch.title)
            newById.getOrPut(ChapterId(num, vol)) { mutableListOf() }.add(ch)
        }

        val oldById = mutableMapOf<ChapterId, MutableList<ChapterResult>>()
        for (ch in old) {
            val num = extractChapterNumber(ch.title) ?: continue
            val vol = extractVolume(ch.title)
            oldById.getOrPut(ChapterId(num, vol)) { mutableListOf() }.add(ch)
        }

        val newLookup = newById.mapValues { (_, v) -> v.toMutableList() }
        val oldLookup = oldById.mapValues { (_, v) -> v.toMutableList() }

        for ((id, oldCandidates) in oldLookup) {
            val newCandidates = newLookup[id] ?: continue
            val minSize = minOf(oldCandidates.size, newCandidates.size)
            for (i in 0 until minSize) {
                val oldCh = oldCandidates[i]
                val newCh = newCandidates[i]
                matched.add(oldCh to newCh)
                oldRemaining.remove(oldCh)
                newRemaining.remove(newCh)
            }
        }

        val score = if (old.isEmpty()) 0f else matched.size.toFloat() / old.size
        return MatchResult(
            matched = matched,
            unmatchedOld = oldRemaining,
            unmatchedNew = newRemaining,
            score = score,
        )
    }

    private val nonMainKeywords = listOf(
        "side story", "side chapter", "extra", "special", "omake",
        "bonus", "prologue", "epilogue", "afterword",
    )

    private fun isMainChapter(title: String): Boolean {
        val t = title.lowercase().trim()
        val clean = t.replace(Regex("""\[.*?]"""), "").trim()
        for (kw in nonMainKeywords) {
            if (clean.startsWith(kw)) return false
        }
        if (Regex("""(?i)\bss\b""").containsMatchIn(clean)) return false
        if (Regex("""(?i)\bauthor'?s?\s+note\b""").containsMatchIn(clean)) return false
        return true
    }

    private fun extractChapterNumber(title: String): Float? {
        if (!isMainChapter(title)) return null

        val t = title.trim()
        val clean = t.replace(Regex("""\[.*?]"""), "").trim()

        // Chapter/Episode keyword + number
        val ch = Regex(
            """(?i)(?:chapter|ch\.?\s*|episode|ep\.?\s*|chapitre|capítulo|kapitel|bab)\s*""" +
            """(\d+(?:\.\d+)?)"""
        ).find(clean)
        if (ch != null) return ch.groupValues[1].toFloatOrNull()

        // East Asian: 第15話, 15話, 第15章, 15화
        val asian = Regex("""[第제]?\s*(\d+(?:\.\d+)?)\s*[話화章节]""").find(clean)
        if (asian != null) return asian.groupValues[1].toFloatOrNull()

        // Starts with number
        val plain = Regex("""^(\d+(?:\.\d+)?)""").find(clean)
        if (plain != null) return plain.groupValues[1].toFloatOrNull()

        return null
    }

    private fun extractVolume(title: String): Int? {
        val t = title.trim()
        val clean = t.replace(Regex("""\[.*?]"""), "").trim()

        // Volume/Book/Vol./V X [Chapter/Episode/Ch.] Y
        val vol = Regex(
            """(?i)(?:volume|vol\.?\s*|book|v)\s*(\d+)\s*""" +
            """(?:chapter|ch\.?\s*|episode|ep\.?\s*|話)"""
        ).find(clean)
        if (vol != null) return vol.groupValues[1].toIntOrNull()

        return null
    }
}
