package my.noveldokusha.features.reader.tools

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.reader.R
import java.io.File

internal class FontsLoader(
    private val context: Context
) {
    companion object {
        // applicationContext — процесс-синглтон, статическое хранение утечки не создаёт.
        // Нужен статическому resolveFile, т.к. кэши и резолвер общие для всех экземпляров.
        private lateinit var appContext: Context

        val systemFonts = listOf(
            "inter",
            "lora",
            "merriweather",
            "source-sans-pro",
            "casual",
            "cursive",
            "monospace",
            "sans-serif",
            "sans-serif-black",
            "sans-serif-condensed",
            "sans-serif-condensed-light",
            "sans-serif-light",
            "sans-serif-medium",
            "sans-serif-smallcaps",
            "sans-serif-thin",
            "serif",
            "serif-monospace"
        )

        /** Единый реактивный источник списка шрифтов для дропдауна и читалки. */
        val availableFonts: MutableState<List<String>> = mutableStateOf(systemFonts)

        // Кэши статические: FontsLoader создаётся в двух местах (ReaderActivity и
        // StyleSettingDialog), импорт/удаление шрифта происходит на одном экземпляре,
        // а рендер — на другом. Статика — корневой фикс рассинхронизации.
        private val typeFaceNORMALCache = mutableMapOf<String, Typeface>()
        private val typeFaceBOLDCache = mutableMapOf<String, Typeface>()
        private val fontFamilyCache = mutableMapOf<String, FontFamily>()

        /** Инвалидирует кэши для ВСЕХ экземпляров FontsLoader. */
        fun resetCaches() {
            typeFaceNORMALCache.clear()
            typeFaceBOLDCache.clear()
            fontFamilyCache.clear()
        }

        // --- Чистые хелперы (без Android-зависимостей, тестируются на JVM) ---

        /** true, если значение — импортированный файл шрифта. */
        internal fun isImported(value: String): Boolean = value.startsWith("font_file:")

        /** Имя файла на диске для импортированного значения, иначе null. */
        internal fun parsePrefValue(value: String): String? =
            if (isImported(value)) value.removePrefix("font_file:") else null

        /** Оставляет только [A-Za-z0-9._-], схлопывает ".."; пусто -> fallback-имя. */
        internal fun sanitizeFileName(raw: String): String {
            val safe = raw.filter {
                it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' ||
                    it == '.' || it == '_' || it == '-'
            }.replace(Regex("\\.{2,}"), ".")
            return safe.ifEmpty { "font_${System.currentTimeMillis()}" }
        }

        /** Отображаемое имя: без префикса и расширения, с суффиксом при коллизии. */
        internal fun displayName(value: String, systemFonts: Set<String>): String {
            val fileName = parsePrefValue(value) ?: return value
            val base = fileName.substringBeforeLast('.')
            return if (base in systemFonts) "$base (imported)" else base
        }

        /** true, если имя файла оканчивается на распознаваемое расширение шрифта (.ttf/.otf). */
        internal fun isValidFontExtension(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".ttf") || lower.endsWith(".otf")
        }

        /** Единый резолвер файла импортированного шрифта (используется NORMAL и BOLD). */
        private fun resolveFile(name: String): File? {
            val fileName = parsePrefValue(name) ?: return null
            // Резолвим в ту же директорию, куда importFont пишет файлы (fontsDir) —
            // иначе импортированные шрифты никогда не находятся и молча падают в serif.
            return File(fontsDir(), fileName).takeIf { it.exists() }
        }

        /** Директория импортированных шрифтов (зеркалит паттерн AppFileResolver `File(filesDir,"books")`). */
        private fun fontsDir(): File = File(appContext.filesDir, "fonts")

        /** Список импортированных шрифтов в виде `font_file:`-значений, отсортированный по имени. */
        fun scanImported(): List<String> =
            fontsDir().listFiles()?.toList().orEmpty()
                .filter { it.isFile }
                .map { "font_file:" + it.name }
                .sorted()

        /** Обновляет реактивный список после импорта/удаления/старта (вызывается на Main). */
        private fun refreshAvailableFonts() {
            resetCaches()
            availableFonts.value = systemFonts + scanImported()
        }

        /** Инициализирует статический appContext и сканирует fontsDir при первой конструкции. */
        private fun initAppContext(context: Context) {
            if (!::appContext.isInitialized) {
                appContext = context.applicationContext
                // Первая конструкция (ReaderActivity) сканирует fontsDir: после рестарта
                // процесса импортированные шрифты снова видны в дропдауне.
                refreshAvailableFonts()
            }
        }

        // Мягкий лимит ~100MB на импортируемый файл шрифта.
        private const val MAX_IMPORT_BYTES = 100 * 1024 * 1024
    }

    init {
        initAppContext(context)
    }

    private fun customTypeface(name: String): Typeface? = when (name) {
        "inter" -> ResourcesCompat.getFont(context, R.font.inter_regular)
        "lora" -> ResourcesCompat.getFont(context, R.font.lora_regular)
        "merriweather" -> ResourcesCompat.getFont(context, R.font.merriweather_regular)
        "source-sans-pro" -> ResourcesCompat.getFont(context, R.font.source_sans_pro_regular)
        else -> null
    }

    fun getTypeFaceNORMAL(name: String) = typeFaceNORMALCache.getOrPut(name) {
        if (isImported(name)) {
            resolveFile(name)?.let { file ->
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } ?: Typeface.create("serif", Typeface.NORMAL)
        } else {
            customTypeface(name) ?: Typeface.create(name, Typeface.NORMAL)
        }
    }

    fun getTypeFaceBOLD(name: String) = typeFaceBOLDCache.getOrPut(name) {
        if (isImported(name)) {
            resolveFile(name)?.let { file ->
                runCatching { Typeface.createFromFile(file) }.getOrNull()
                    ?.let { Typeface.create(it, Typeface.BOLD) }
            } ?: Typeface.create("serif", Typeface.BOLD)
        } else {
            customTypeface(name)?.let { Typeface.create(it, Typeface.BOLD) }
                ?: Typeface.create(name, Typeface.BOLD)
        }
    }

    fun getFontFamily(name: String) = fontFamilyCache.getOrPut(name) {
        FontFamily(getTypeFaceNORMAL(name))
    }

    /** Импортирует шрифт из content-URI: копирует файл, валидирует, обновляет список. */
    suspend fun importFont(uri: Uri): Result<Unit> {
        // Копирование и валидация — на IO; обновление кэшей/списка — на Main после завершения.
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val rawName = context.contentResolver.query(uri, null, null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) cursor.getString(idx) else null
                        } else null
                    }
                    ?: uri.lastPathSegment
                    ?: "font"

                var fileName = sanitizeFileName(rawName)
                // Расширение — рекомендательное: реальный гейт — createFromFile ниже.
                if (!isValidFontExtension(fileName)) fileName += ".ttf"

                val dir = fontsDir().apply { mkdirs() }
                val file = File(dir, fileName)

                val input = context.contentResolver.openInputStream(uri)
                    ?: return@runCatching error("Не удалось открыть поток для $uri")
                input.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.size > MAX_IMPORT_BYTES) {
                        file.delete()
                        return@runCatching error("Файл шрифта превышает лимит ${MAX_IMPORT_BYTES} байт")
                    }
                    file.outputStream().use { it.write(bytes) }
                }

                // Валидация: повреждённый/не-шрифт файл удаляем и сообщаем об ошибке.
                runCatching { Typeface.createFromFile(file) }.getOrElse {
                    file.delete()
                    return@runCatching error("Файл не является валидным шрифтом: $fileName")
                }

                Unit
            }
        }
        if (result.isSuccess) refreshAvailableFonts()
        return result
    }

    /** Удаляет импортированный шрифт; системные/встроенные значения не трогает. */
    fun deleteFont(value: String): Boolean {
        if (!isImported(value)) return false
        val fileName = parsePrefValue(value) ?: return false
        val file = File(fontsDir(), fileName)
        val deleted = file.delete() || !file.exists()
        if (deleted) refreshAvailableFonts()
        return deleted
    }
}