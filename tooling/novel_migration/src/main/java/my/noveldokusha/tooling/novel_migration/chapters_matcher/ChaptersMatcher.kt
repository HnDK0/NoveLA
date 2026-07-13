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
        // ponytail: was `mutableListOf` + `.remove(element)` inside a loop — O(n) per remove,
        // O(n*m) over a 3000-chapter novel. LinkedHashSet gives O(1) remove + preserves
        // insertion order so the returned unmatched lists stay deterministic.
        val oldRemaining = LinkedHashSet(old)
        val newRemaining = LinkedHashSet(new)
        val matched = mutableListOf<Pair<ChapterResult, ChapterResult>>()

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

        for ((id, oldCandidates) in oldById) {
            val newCandidates = newById[id] ?: continue
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
            unmatchedOld = oldRemaining.toList(),
            unmatchedNew = newRemaining.toList(),
            score = score,
        )
    }

    private val nonMainKeywords = listOf(
        "side story", "side chapter", "extra", "special", "omake",
        "bonus", "prologue", "epilogue", "afterword",
    )

    private fun isMainChapter(title: String): Boolean {
        val t = title.lowercase().trim()
        val clean = BRACKET_REGEX.replace(t, "").trim()
        for (kw in nonMainKeywords) {
            if (clean.startsWith(kw)) return false
        }
        if (SS_KEYWORD_REGEX.containsMatchIn(clean)) return false
        if (AUTHOR_NOTE_REGEX.containsMatchIn(clean)) return false
        return true
    }

    private fun extractChapterNumber(title: String): Float? {
        if (!isMainChapter(title)) return null

        val t = title.trim()
        val clean = BRACKET_REGEX.replace(t, "").trim()

        // Chapter/Episode keyword + number
        CHAPTER_NUM_REGEX.find(clean)?.let {
            return it.groupValues[1].toFloatOrNull()
        }

        // East Asian: 第15話, 15話, 第15章, 15화
        ASIAN_CHAPTER_REGEX.find(clean)?.let {
            return it.groupValues[1].toFloatOrNull()
        }

        // Starts with number
        LEADING_NUM_REGEX.find(clean)?.let {
            return it.groupValues[1].toFloatOrNull()
        }

        return null
    }

    private fun extractVolume(title: String): Int? {
        val t = title.trim()
        val clean = BRACKET_REGEX.replace(t, "").trim()
        VOLUME_REGEX.find(clean)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        return null
    }

    private companion object {
        // ponytail: was Regex(...) constructed inside isMainChapter / extractChapterNumber /
        // extractVolume on every call. For a 3000-chapter migration that's ~12000+ Pattern
        // compilations on the hot path. Hoisted to top-level companion val's, compiled once.
        val BRACKET_REGEX = Regex("""\[.*?]""")
        val SS_KEYWORD_REGEX = Regex("""(?i)\bss\b""")
        val AUTHOR_NOTE_REGEX = Regex("""(?i)\bauthor'?s?\s+note\b""")
        val CHAPTER_NUM_REGEX = Regex(
            """(?i)(?:chapter|ch\.?\s*|episode|ep\.?\s*|chapitre|capítulo|kapitel|bab)\s*""" +
            """(\d+(?:\.\d+)?)"""
        )
        val ASIAN_CHAPTER_REGEX = Regex("""[第제]?\s*(\d+(?:\.\d+)?)\s*[話화章节]""")
        val LEADING_NUM_REGEX = Regex("""^(\d+(?:\.\d+)?)""")
        val VOLUME_REGEX = Regex(
            """(?i)(?:volume|vol\.?\s*|book|v)\s*(\d+)\s*""" +
            """(?:chapter|ch\.?\s*|episode|ep\.?\s*|話)"""
        )
    }
}
