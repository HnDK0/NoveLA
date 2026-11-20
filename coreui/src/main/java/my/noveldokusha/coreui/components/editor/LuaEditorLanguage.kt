package my.noveldokusha.coreui.components.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.util.BaseAnalyzeManager
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class LuaEditorLanguage : Language {

    private val manager = LuaAnalyzeManager()
    private val formatter = EmptyFormatter

    override fun getAnalyzeManager(): AnalyzeManager = manager
    override fun getInterruptionLevel() = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        content: ContentReference, position: CharPosition,
        publisher: CompletionPublisher, extraArguments: Bundle
    ) = Unit

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int) = 0
    override fun useTab() = false
    override fun getFormatter(): Formatter = formatter
    override fun getSymbolPairs() = SymbolPairMatch.DefaultSymbolPairs()
    override fun getNewlineHandlers() = emptyArray<NewlineHandler>()
    override fun destroy() = manager.destroy()

    private object EmptyFormatter : Formatter {
        override fun format(text: io.github.rosemoe.sora.text.Content, cursorRange: io.github.rosemoe.sora.text.TextRange) = Unit
        override fun formatRegion(text: io.github.rosemoe.sora.text.Content, rangeToFormat: io.github.rosemoe.sora.text.TextRange, cursorRange: io.github.rosemoe.sora.text.TextRange) = Unit
        override fun setReceiver(receiver: Formatter.FormatResultReceiver?) = Unit
        override fun isRunning() = false
        override fun destroy() = Unit
    }
}

private class LuaAnalyzeManager : BaseAnalyzeManager() {

    override fun insert(start: CharPosition, end: CharPosition, insertedContent: CharSequence) = Unit
    override fun delete(start: CharPosition, end: CharPosition, deletedContent: CharSequence) = Unit

    override fun rerun() {
        val receiver = getReceiver() ?: return
        val content = getContentRef() ?: return
        val lineCount = content.lineCount
        if (lineCount == 0) {
            receiver.setStyles(this, null)
            return
        }

        val sb = StringBuilder()
        for (i in 0 until lineCount) {
            if (i > 0) sb.append('\n')
            sb.append(content.getLine(i))
        }
        val text = sb.toString()
        if (text.isEmpty()) {
            receiver.setStyles(this, null)
            return
        }

        val lineOfOffset = IntArray(text.length)
        var currentLine = 0
        for (i in text.indices) {
            lineOfOffset[i] = currentLine
            if (text[i] == '\n') currentLine++
        }

        val taken = BooleanArray(text.length)
        val tokens = mutableListOf<TokenSpan>()

        fun addTokens(regex: Regex, colorId: Int, groupIndex: Int = 0) {
            for (match in regex.findAll(text)) {
                val group = match.groups[groupIndex] ?: continue
                val range = group.range
                if (range.first > range.last) continue
                if ((range.first..range.last).any { taken[it] }) continue
                tokens.add(TokenSpan(range, colorId))
                for (i in range.first..range.last) taken[i] = true
            }
        }

        addTokens(LUA_BLOCK_COMMENT_REGEX, EditorColorScheme.COMMENT)
        addTokens(LINE_COMMENT_REGEX, EditorColorScheme.COMMENT)
        addTokens(LUA_STRING_REGEX, EditorColorScheme.IDENTIFIER_VAR)
        addTokens(STRING_REGEX, EditorColorScheme.IDENTIFIER_VAR)
        addTokens(NUMBER_REGEX, EditorColorScheme.LITERAL)

        val keywordPattern = Regex("""\b(${LUA_KEYWORDS.joinToString("|") { Regex.escape(it) }})\b""")
        addTokens(keywordPattern, EditorColorScheme.KEYWORD)

        val hooksPattern = Regex("""\b(${NOVELA_LUA_HOOKS.joinToString("|") { Regex.escape(it) }})\b""")
        addTokens(hooksPattern, EditorColorScheme.ANNOTATION)

        val builtinPattern = Regex("""\b(${LUA_BUILTIN_FUNCTIONS.joinToString("|") { Regex.escape(it) }})\b""")
        addTokens(builtinPattern, EditorColorScheme.FUNCTION_NAME)

        val modulesPattern = Regex("""\b(${LUA_STANDARD_MODULES.joinToString("|") { Regex.escape(it) }})\b""")
        addTokens(modulesPattern, EditorColorScheme.ANNOTATION)

        addTokens(FUNCTION_CALL_REGEX, EditorColorScheme.FUNCTION_NAME, groupIndex = 1)
        addTokens(TYPE_REGEX, EditorColorScheme.ANNOTATION, groupIndex = 1)
        addTokens(LUA_OPERATOR_REGEX, EditorColorScheme.OPERATOR)

        tokens.sortBy { it.range.first }

        val tokensByLine = Array<MutableList<TokenSpan>>(lineCount) { mutableListOf() }
        for (token in tokens) {
            val start = token.range.first
            if (start in lineOfOffset.indices) {
                tokensByLine[lineOfOffset[start]].add(token)
            }
        }

        val lines = text.split('\n')
        val lineStartOff = IntArray(lineCount)
        var pos = 0
        for (i in 0 until lineCount) {
            lineStartOff[i] = pos
            pos += lines[i].length + 1
        }

        val lineSpanList = arrayOfNulls<List<Span>>(lineCount)

        for (lineIdx in 0 until lineCount) {
            val lineTokens = tokensByLine[lineIdx]
            if (lineTokens.isEmpty()) {
                lineSpanList[lineIdx] = listOf(
                    Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                )
                continue
            }

            val lineLen = lines[lineIdx].length
            val boundaries = mutableSetOf(0)

            for (token in lineTokens) {
                val sc = token.range.first - lineStartOff[lineIdx]
                val ec = token.range.last + 1 - lineStartOff[lineIdx]
                if (sc < lineLen) boundaries.add(sc)
                boundaries.add(minOf(ec, lineLen))
            }

            val sorted = boundaries.sorted()
            val spans = mutableListOf<Span>()

            for (col in sorted) {
                if (col >= lineLen) break
                val coveringToken = lineTokens.firstOrNull {
                    val sc = it.range.first - lineStartOff[lineIdx]
                    val ec = it.range.last + 1 - lineStartOff[lineIdx]
                    sc <= col && col < ec
                }
                val colorId = coveringToken?.colorId ?: EditorColorScheme.TEXT_NORMAL
                val style = TextStyle.makeStyle(colorId)
                if (spans.isEmpty() || spans.last().getStyle() != style) {
                    spans.add(Span.obtain(col, style))
                }
            }

            if (spans.isEmpty()) {
                spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
            }
            lineSpanList[lineIdx] = spans
        }

        val lineSpans = LineSpans(lineSpanList)
        val styles = Styles(lineSpans)
        receiver.setStyles(this, styles)
    }

    private data class TokenSpan(val range: IntRange, val colorId: Int)
}

private class LineSpans(private val spans: Array<out List<Span>?>) : io.github.rosemoe.sora.lang.styling.Spans {

    override fun getLineCount() = spans.size

    override fun read() = object : io.github.rosemoe.sora.lang.styling.Spans.Reader {
        private var currentLine = -1
        override fun moveToLine(line: Int) { currentLine = line }
        override fun getSpanCount() = spans.getOrNull(currentLine)?.size ?: 1
        override fun getSpanAt(index: Int) = spans.getOrNull(currentLine)?.getOrNull(index)
            ?: Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
        override fun getSpansOnLine(line: Int): List<Span> {
            val s = spans.getOrNull(line)
            if (!s.isNullOrEmpty()) return s
            return listOf(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
        }
    }

    override fun adjustOnInsert(start: CharPosition, end: CharPosition) = Unit
    override fun adjustOnDelete(start: CharPosition, end: CharPosition) = Unit
    override fun supportsModify() = false
    override fun modify() = throw UnsupportedOperationException()
}

private val LUA_KEYWORDS = setOf(
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
    "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true",
    "until", "while"
)

internal val LUA_BUILTIN_FUNCTIONS = setOf(
    "assert", "collectgarbage", "dofile", "error", "getmetatable",
    "ipairs", "load", "loadfile", "next", "pairs", "pcall", "print",
    "rawequal", "rawget", "rawlen", "rawset", "select", "setmetatable",
    "tonumber", "tostring", "type", "xpcall", "require", "_G", "_VERSION",
    "http_get", "http_post", "http_get_batch",
    "get_cookies", "set_cookies", "get_preference", "set_preference",
    "aes_decrypt", "base64_decode", "base64_encode",
    "html_parse", "html_select", "html_select_first", "html_attr", "html_text", "html_remove",
    "url_encode", "url_encode_charset", "url_resolve",
    "regex_match", "regex_replace",
    "string_normalize", "string_split", "string_trim", "string_starts_with", "string_ends_with", "string_clean", "unescape_unicode",
    "json_parse", "json_stringify",
    "detect_pagination", "sleep", "log_info", "log_error", "os_time",
    // Metode OOP Jsoup ala Shosetsu untuk elemen dan koleksi DOM
    "toHtml", "to_html", "selectFirst", "select_first", "ownText", "own_text",
    "hasClass", "has_class", "hasAttr", "has_attr", "outerHtml", "outer_html",
    "tagName", "tag_name", "nextSibling", "nextElementSibling", "previousSibling", "previousElementSibling"
)

internal val NOVELA_LUA_HOOKS = setOf(
    "getCatalogList", "getCatalogSearch", "getBookTitle", "getBookCoverImageUrl",
    "getBookDescription", "getBookGenres", "getChapterList", "getChapterText",
    "getChapterListHash", "getFilterList", "getSettingsSchema", "parsePage",
    "baseUrl", "cf_options"
)

internal val LUA_STANDARD_MODULES = setOf(
    "coroutine", "debug", "io", "math", "os", "package", "string", "table", "utf8", "luajava"
)

internal val NUMBER_REGEX = Regex("\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(\\.\\d+)?[fFLl]?\\b")
internal val STRING_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
internal val LUA_STRING_REGEX = Regex("\\[\\[[\\s\\S]*?\\]\\]")
internal val LINE_COMMENT_REGEX = Regex("--[^\n]*")
internal val LUA_BLOCK_COMMENT_REGEX = Regex("--\\[\\[[\\s\\S]*?\\]\\]")
internal val FUNCTION_CALL_REGEX = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()")
internal val TYPE_REGEX = Regex("\\b([A-Z][A-Za-z0-9_]*)\\b")
internal val LUA_OPERATOR_REGEX = Regex("[+\\-*/%^#=~<>&|.:]+")
