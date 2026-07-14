package my.noveldokusha.coreui.components.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

private val LUA_KEYWORDS: Set<String> = setOf(
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
    "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true",
    "until", "while"
)

private val NUMBER_REGEX = Regex("\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(\\.\\d+)?[fFLl]?\\b")

// Matches strings (single/double quoted) and long bracket strings [[...]]
private val STRING_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
private val LUA_STRING_REGEX = Regex("\\[\\[[\\s\\S]*?\\]\\]")

// Lua comments: line comments start with --, block comments `--[[ ... ]]`
private val LINE_COMMENT_REGEX = Regex("--[^\n]*")
private val LUA_BLOCK_COMMENT_REGEX = Regex("--\\[\\[[\\s\\S]*?\\]\\]")

private val FUNCTION_CALL_REGEX = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()")
private val TYPE_REGEX = Regex("\\b([A-Z][A-Za-z0-9_]*)\\b")

private data class Token(val range: IntRange, val style: SpanStyle)

/**
 * Lightweight regex-based highlighter specifically tailored for Lua scripts.
 */
fun highlight(text: String, language: String = "lua", colors: SyntaxColors): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    val tokens = mutableListOf<Token>()
    val taken = BooleanArray(text.length)

    fun addTokens(regex: Regex, style: SpanStyle, groupIndex: Int = 0) {
        for (match in regex.findAll(text)) {
            val group = match.groups[groupIndex] ?: continue
            val range = group.range
            if (range.first > range.last) continue
            if ((range.first until range.last + 1).any { taken[it] }) continue
            tokens += Token(range, style)
            for (i in range) taken[i] = true
        }
    }

    // Order matters: comments and strings first so keyword/number matching inside them is suppressed.
    addTokens(LUA_BLOCK_COMMENT_REGEX, SpanStyle(color = colors.comment))
    addTokens(LINE_COMMENT_REGEX, SpanStyle(color = colors.comment))
    
    addTokens(LUA_STRING_REGEX, SpanStyle(color = colors.string))
    addTokens(STRING_REGEX, SpanStyle(color = colors.string))
    addTokens(NUMBER_REGEX, SpanStyle(color = colors.number))

    val pattern = Regex("\\b(${LUA_KEYWORDS.joinToString("|") { Regex.escape(it) }})\\b")
    addTokens(pattern, SpanStyle(color = colors.keyword, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))

    addTokens(FUNCTION_CALL_REGEX, SpanStyle(color = colors.function), groupIndex = 1)
    addTokens(TYPE_REGEX, SpanStyle(color = colors.type), groupIndex = 1)

    return AnnotatedString.Builder(text).apply {
        for (token in tokens.sortedBy { it.range.first }) {
            addStyle(token.style, token.range.first, token.range.last + 1)
        }
    }.toAnnotatedString()
}
