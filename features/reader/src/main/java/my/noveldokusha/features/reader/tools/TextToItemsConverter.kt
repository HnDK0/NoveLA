package my.noveldokusha.features.reader.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import my.noveldokusha.core.BookTextMapper
import my.noveldokusha.core.models.RegexRule
import my.noveldokusha.features.reader.domain.ImgEntry
import my.noveldokusha.features.reader.domain.ReaderItem

internal suspend fun textToItemsConverter(
    chapterUrl: String,
    chapterIndex: Int,
    chapterItemPositionDisplacement: Int,
    text: String,
    userRegexRules: List<RegexRule> = emptyList()
): List<ReaderItem> = withContext(Dispatchers.Default) {

    val cleanText = text
        .replace(Regex("<(?!(imgEntry|/imgEntry))[^>]*>"), "")
        .replace("\r\n", "\n")
        .replace("\u00A0", " ")
        .replace(Regex("[ ]+"), " ")

    // Применение пользовательских regex-правил
    val processedText = applyUserRegexRules(cleanText, userRegexRules)

    val paragraphs = processTextIntoLogicalBlocks(processedText)

    paragraphs.mapIndexed { position, paragraph ->
        async {
            generateITEM(
                chapterUrl = chapterUrl,
                chapterIndex = chapterIndex,
                chapterItemPosition = position + chapterItemPositionDisplacement,
                text = paragraph,
                location = when (position) {
                    0 -> ReaderItem.Location.FIRST
                    paragraphs.lastIndex -> ReaderItem.Location.LAST
                    else -> ReaderItem.Location.MIDDLE
                }
            )
        }
    }.awaitAll()
}

private fun processTextIntoLogicalBlocks(text: String): List<String> {
    val result = mutableListOf<String>()

    var splitResult = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }

    // Если двойных переносов нет (ошибка парсинга), используем одиночные
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
    if (paragraph.length <= 800 || paragraph.contains("imgEntry")) {
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

        // Если превысили 800 с точкой или жесткий предел 2000
        if ((currentChunk.length >= 800 && safeSplitIndexInChunk != -1) || currentChunk.length >= 2000) {

            // Если безопасного индекса нет, ищем ближайший пробел с конца, чтобы не рвать слово
            val splitAt = if (safeSplitIndexInChunk != -1) {
                safeSplitIndexInChunk.coerceAtMost(currentChunk.length)
            } else {
                val lastSpace = currentChunk.lastIndexOf(' ')
                if (lastSpace != -1) (lastSpace + 1).coerceAtMost(currentChunk.length) else currentChunk.length
            }

            // Проверяем, что индекс в допустимом диапазоне перед вызовом substring
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

private fun generateITEM(
    chapterUrl: String,
    chapterIndex: Int,
    chapterItemPosition: Int,
    text: String,
    location: ReaderItem.Location
): ReaderItem = try {
    when (val imgEntry = BookTextMapper.ImgEntry.fromXMLString(text)) {
        null -> ReaderItem.Body(
            chapterUrl = chapterUrl,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition,
            text = text,
            location = location
        )
        else -> ReaderItem.Image(
            chapterUrl = chapterUrl,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition,
            text = text,
            location = location,
            image = ImgEntry(path = imgEntry.path, yrel = imgEntry.yrel)
        )
    }
} catch (e: Exception) {
    ReaderItem.Body(
        chapterUrl = chapterUrl,
        chapterIndex = chapterIndex,
        chapterItemPosition = chapterItemPosition,
        text = text,
        location = location
    )
}
