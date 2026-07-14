package my.noveldokusha.coreui.components.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

private val LUA_KEYWORDS: Set<String> = setOf(
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
    "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true",
    "until", "while"
)

private val LUA_BUILTIN_FUNCTIONS: Set<String> = setOf(
    // Standard Lua built-in functions & globals
    "assert", "collectgarbage", "dofile", "error", "getmetatable",
    "ipairs", "load", "loadfile", "next", "pairs", "pcall", "print",
    "rawequal", "rawget", "rawlen", "rawset", "select", "setmetatable",
    "tonumber", "tostring", "type", "xpcall", "require", "_G", "_VERSION",

    // NoveLA Custom Lua Sandbox APIs
    "http_get", "http_post", "http_get_batch",
    "get_cookies", "set_cookies", "get_preference", "set_preference",
    "aes_decrypt", "base64_decode", "base64_encode",
    "html_parse", "html_select", "html_select_first", "html_attr", "html_text", "html_remove",
    "url_encode", "url_encode_charset", "url_resolve",
    "regex_match", "regex_replace",
    "string_normalize", "string_split", "string_trim", "string_starts_with", "string_ends_with", "string_clean", "unescape_unicode",
    "json_parse", "json_stringify",
    "detect_pagination", "sleep", "log_info", "log_error", "os_time"
)

private val NOVELA_LUA_HOOKS: Set<String> = setOf(
    // Entry points/lifecycle hooks that extension scrapers define
    "getCatalogList", "getCatalogSearch", "getBookTitle", "getBookCoverImageUrl",
    "getBookDescription", "getBookGenres", "getChapterList", "getChapterText",
    "getChapterListHash", "getFilterList", "getSettingsSchema", "parsePage",
    "baseUrl", "cf_options"
)

private val LUA_STANDARD_MODULES: Set<String> = setOf(
    "coroutine", "debug", "io", "math", "os", "package", "string", "table", "utf8", "luajava"
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
private val LUA_OPERATOR_REGEX = Regex("[+\\-*/%^#=~<>&|.:]+")

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

    // 1. Standard Lua Keywords
    val keywordPattern = Regex("\\b(${LUA_KEYWORDS.joinToString("|") { Regex.escape(it) }})\\b")
    addTokens(keywordPattern, SpanStyle(color = colors.keyword, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))

    // 2. NoveLA Scraper Lifecycle Hooks
    val hooksPattern = Regex("\\b(${NOVELA_LUA_HOOKS.joinToString("|") { Regex.escape(it) }})\\b")
    addTokens(hooksPattern, SpanStyle(color = colors.type, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))

    // 3. Lua Built-in / NoveLA Sandbox Functions
    val builtinPattern = Regex("\\b(${LUA_BUILTIN_FUNCTIONS.joinToString("|") { Regex.escape(it) }})\\b")
    addTokens(builtinPattern, SpanStyle(color = colors.function, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))

    // 4. Lua Standard Library Modules
    val modulesPattern = Regex("\\b(${LUA_STANDARD_MODULES.joinToString("|") { Regex.escape(it) }})\\b")
    addTokens(modulesPattern, SpanStyle(color = colors.type, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))

    // 5. Function Calls (including user-defined)
    addTokens(FUNCTION_CALL_REGEX, SpanStyle(color = colors.function), groupIndex = 1)

    // 6. Generic Types / Capitalized identifiers
    addTokens(TYPE_REGEX, SpanStyle(color = colors.type), groupIndex = 1)

    // 7. Operators
    addTokens(LUA_OPERATOR_REGEX, SpanStyle(color = colors.operator))

    return AnnotatedString.Builder(text).apply {
        for (token in tokens.sortedBy { it.range.first }) {
            addStyle(token.style, token.range.first, token.range.last + 1)
        }
    }.toAnnotatedString()
}

