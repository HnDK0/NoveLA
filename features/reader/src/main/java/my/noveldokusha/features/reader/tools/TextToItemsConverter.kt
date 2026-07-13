package my.noveldokusha.features.reader.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.models.RegexRule
import my.noveldokusha.features.reader.domain.ImgEntry
import my.noveldokusha.features.reader.domain.ReaderItem
import org.jsoup.Jsoup
import timber.log.Timber

private val IMG_TAG_REGEX = Regex("<img\\b", RegexOption.IGNORE_CASE)

internal suspend fun textToItemsConverter(
    chapterUrl: String,
    chapterIndex: Int,
    chapterItemPositionDisplacement: Int,
    text: String,
    userRegexRules: List<RegexRule> = emptyList()
): List<ReaderItem> = withContext(Dispatchers.Default) {

    val items = mutableListOf<ReaderItem>()
    var itemPosition = chapterItemPositionDisplacement
    var remaining = text

    while (true) {
        val match = IMG_TAG_REGEX.find(remaining) ?: break

        val before = remaining.substring(0, match.range.first)
        if (before.isNotBlank()) {
            val bodyItems = buildBodyItems(
                text = before,
                chapterUrl = chapterUrl,
                chapterIndex = chapterIndex,
                startPosition = itemPosition,
                userRegexRules = userRegexRules
            )
            items.addAll(bodyItems)
            itemPosition += bodyItems.size
        }

        val afterMatch = remaining.substring(match.range.first)
        val imgTag = extractImgTag(afterMatch)
        if (imgTag != null) {
            val (src, yrel) = imgTag
            items.add(ReaderItem.Image(
                chapterUrl = chapterUrl,
                chapterIndex = chapterIndex,
                chapterItemPosition = itemPosition++,
                location = ReaderItem.Location.MIDDLE,
                text = "<img src=\"$src\" yrel=\"${"%.2f".format(yrel)}\">",
                image = ImgEntry(path = src, yrel = yrel)
            ))
        }

        val endIdx = afterMatch.indexOf('>')
        remaining = if (endIdx >= 0) afterMatch.substring(endIdx + 1) else ""
    }

    if (remaining.isNotBlank()) {
        val bodyItems = buildBodyItems(
            text = remaining,
            chapterUrl = chapterUrl,
            chapterIndex = chapterIndex,
            startPosition = itemPosition,
            userRegexRules = userRegexRules
        )
        items.addAll(bodyItems)
    }

    // Assign locations
    if (items.isNotEmpty()) {
        items[0] = items[0].let {
            when (it) {
                is ReaderItem.Body -> it.copy(location = ReaderItem.Location.FIRST)
                is ReaderItem.Image -> it.copy(location = ReaderItem.Location.FIRST)
                else -> it
            }
        }
        items[items.lastIndex] = items[items.lastIndex].let {
            when (it) {
                is ReaderItem.Body -> it.copy(location = ReaderItem.Location.LAST)
                is ReaderItem.Image -> it.copy(location = ReaderItem.Location.LAST)
                else -> it
            }
        }
    }

    items
}

private fun extractImgTag(text: String): Pair<String, Float>? {
    val endIdx = text.indexOf('>')
    if (endIdx < 0) return null
    val tag = text.substring(0, endIdx + 1)
    return try {
        val img = Jsoup.parse(tag).selectFirst("img") ?: return null
        val src = (img.attr("src").ifBlank { img.attr("data-src") }).takeIf { it.isNotBlank() } ?: return null
        val yrel = img.attr("yrel").toFloatOrNull()?.takeIf { it >= 0.01f } ?: 1.45f
        Pair(src, yrel)
    } catch (_: Exception) {
        null
    }
}

private fun buildBodyItems(
    text: String,
    chapterUrl: String,
    chapterIndex: Int,
    startPosition: Int,
    userRegexRules: List<RegexRule>
): List<ReaderItem.Body> {
    val cleanText = text
        .replace(Regex("<[^>]*>"), "")
        .replace("\r\n", "\n")
        .replace("\u00A0", " ")
        .replace(Regex("[ ]+"), " ")

    val processedText = applyUserRegexRules(cleanText, userRegexRules)
    val paragraphs = processTextIntoLogicalBlocks(processedText)

    return paragraphs.mapIndexedNotNull { i, para ->
        val trimmed = para.trim()
        if (trimmed.isBlank()) return@mapIndexedNotNull null
        ReaderItem.Body(
            chapterUrl = chapterUrl,
            chapterIndex = chapterIndex,
            chapterItemPosition = startPosition + i,
            text = trimmed,
            location = ReaderItem.Location.MIDDLE
        )
    }
}

private fun processTextIntoLogicalBlocks(text: String): List<String> {
    val result = mutableListOf<String>()

    var splitResult = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }

    if (splitResult.size <= 1 && text.contains("\n")) {
        splitResult = text.split("\n").filter { it.isNotBlank() }
    }

    for (paragraph in splitResult) {
        val trimmedParagraph = paragraph.trim()
        if (trimmedParagraph.isEmpty()) continue

        val firstNonSpace = paragraph.indexOfFirst { !it.isWhitespace() }
        val indentation = if (firstNonSpace > 0) paragraph.substring(0, firstNonSpace) else ""

        val subBlocks = splitParagraphRespectingLogicalBlocks(trimmedParagraph)

        if (subBlocks.isNotEmpty()) {
            result.add(indentation + subBlocks[0])
            if (subBlocks.size > 1) {
                result.addAll(subBlocks.subList(1, subBlocks.size))
            }
        }
    }
    return result
}

private fun splitParagraphRespectingLogicalBlocks(paragraph: String): List<String> {
    if (paragraph.length <= 800) {
        return listOf(paragraph)
    }

    val result = mutableListOf<String>()
    var currentChunk = StringBuilder()

    var bracketDepth = 0
    var quoteState = false
    var safeSplitIndexInChunk = -1

    val openingBrackets = setOf('[', '(', '{', '<')
    val closingBrackets = setOf(']', ')', '}', '>')
    val quotes = setOf('"', '«', '»', '“', '”', '„', '‘', '’')

    for (char in paragraph) {
        currentChunk.append(char)

        when (char) {
            in openingBrackets -> bracketDepth++
            in closingBrackets -> bracketDepth--
            in quotes -> quoteState = !quoteState
        }

        val isSafeZone = bracketDepth <= 0 && !quoteState

        if (isSafeZone) {
            if (char == '.' || char == '!' || char == '?' || char == ';' || char == ':') {
                safeSplitIndexInChunk = currentChunk.length
            }
            else if (char == ' ' && currentChunk.length >= 400) {
                safeSplitIndexInChunk = currentChunk.length
            }
        }

        if ((currentChunk.length >= 800 && safeSplitIndexInChunk != -1) || currentChunk.length >= 2000) {
            val splitAt = if (safeSplitIndexInChunk != -1) {
                safeSplitIndexInChunk.coerceAtMost(currentChunk.length)
            } else {
                val lastSpace = currentChunk.lastIndexOf(' ')
                if (lastSpace != -1) (lastSpace + 1).coerceAtMost(currentChunk.length) else currentChunk.length
            }

            val chunkToTake = if (splitAt > 0 && splitAt <= currentChunk.length) {
                currentChunk.substring(0, splitAt).trim()
            } else {
                currentChunk.toString().trim()
            }
            if (chunkToTake.isNotEmpty()) {
                result.add(chunkToTake)
            }

            val remaining = if (splitAt > 0 && splitAt < currentChunk.length) {
                currentChunk.substring(splitAt).trimStart()
            } else if (splitAt >= currentChunk.length) {
                ""
            } else {
                currentChunk.toString().trimStart()
            }

            currentChunk = StringBuilder(remaining)
            bracketDepth = countUnbalancedBrackets(remaining, openingBrackets, closingBrackets)
            quoteState = countQuotes(remaining, quotes) % 2 != 0
            safeSplitIndexInChunk = -1
        }
    }

    if (currentChunk.isNotBlank()) {
        result.add(currentChunk.toString().trim())
    }

    return if (result.isEmpty()) listOf(paragraph) else result
}

private fun countUnbalancedBrackets(str: String, open: Set<Char>, close: Set<Char>): Int {
    var depth = 0
    for (char in str) {
        if (char in open) depth++
        else if (char in close) depth--
    }
    return depth.coerceAtLeast(0)
}

private fun countQuotes(str: String, quotes: Set<Char>): Int = str.count { it in quotes }

private fun applyUserRegexRules(text: String, rules: List<RegexRule>): String {
    var result = text
    rules.forEach { rule ->
        try {
            val regex = Regex(rule.pattern)
            result = result.replace(regex, rule.replacement)
        } catch (e: Exception) {
            println("Failed to apply user regex rule: ${e.message}, pattern: ${rule.pattern}")
        }
    }
    return result
}
