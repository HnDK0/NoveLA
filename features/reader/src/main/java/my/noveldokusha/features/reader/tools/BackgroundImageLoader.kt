package my.noveldokusha.features.reader.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.core.isImage
import java.io.File

internal class BackgroundImageLoader(
    private val context: Context
) {
    companion object {
        // applicationContext — процесс-синглтон, статическое хранение утечки не создаёт.
        private lateinit var appContext: Context

        /** Единый реактивный список импортированных фоновых картинок для UI и рендера. */
        val availableBackgrounds: MutableState<List<File>> = mutableStateOf(emptyList())

        // --- Чистые хелперы (без Android-зависимостей, тестируются на JVM) ---

        /** true, если значение — импортированная фоновая картинка. */
        internal fun isImported(value: String): Boolean = value.startsWith("background_file:")

        /** Имя файла на диске для импортированного значения, иначе null. */
        internal fun parsePrefValue(value: String): String? =
            if (isImported(value)) value.removePrefix("background_file:") else null

        /** Оставляет только [A-Za-z0-9._-], схлопывает ".."; пусто -> fallback-имя. */
        internal fun sanitizeFileName(raw: String): String {
            val safe = raw.filter {
                it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' ||
                    it == '.' || it == '_' || it == '-'
            }.replace(Regex("\\.{2,}"), ".")
            return safe.ifEmpty { "background_${System.currentTimeMillis()}" }
        }

        /** true, если имя файла оканчивается на распознаваемое расширение картинки. */
        internal fun isValidImageExtension(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp")
        }

        /** Единый резолвер файла импортированной фоновой картинки. */
        internal fun resolveFile(name: String): File? {
            val fileName = parsePrefValue(name) ?: return null
            return File(backgroundsDir(), fileName).takeIf { it.exists() }
        }

        /** Директория импортированных фоновых картинок. */
        private fun backgroundsDir(): File = File(appContext.filesDir, "backgrounds")

        /** Список импортированных фоновых картинок, отсортированный по имени. */
        fun scanImported(): List<File> =
            backgroundsDir().listFiles()?.toList().orEmpty()
                .filter { it.isFile }
                .sortedBy { it.name }

        /** Обновляет реактивный список после импорта/удаления/старта. */
        private fun refreshAvailableBackgrounds() {
            availableBackgrounds.value = scanImported()
        }

        /** Инициализирует статический appContext и сканирует backgroundsDir при первой конструкции. */
        private fun initAppContext(context: Context) {
            if (!::appContext.isInitialized) {
                appContext = context.applicationContext
                // Первая конструкция (ReaderActivity) сканирует backgroundsDir: после рестарта
                // процесса импортированные фоновые картинки снова видны в UI.
                refreshAvailableBackgrounds()
            }
        }

        // Мягкий лимит ~20MB на импортируемую фоновую картинку (фону не нужен 100MB шрифтового лимита).
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

        // Сериализует запись+валидацию импорта: два быстрых импорта с коллизией
        // санитизированных имён (например, оба "photo.png") не должны писать в один
        // файл одновременно (перемежение/порча) или молча перезаписывать друг друга.
        private val importMutex = Mutex()
    }

    init {
        initAppContext(context)
    }

    /** Импортирует фоновую картинку из content-URI: копирует файл, валидирует, обновляет список. */
    suspend fun importBackgroundImage(uri: Uri): Result<Unit> {
        // Копирование и валидация — на IO; обновление списка — на Main после завершения.
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
                    ?: "image"

                var fileName = sanitizeFileName(rawName)
                // Расширение — рекомендательное: реальный гейт — isImage ниже.
                if (!isValidImageExtension(fileName)) fileName += ".png"

                // Запись+валидация под общим мьютексом: коллизия санитизированных имён
                // не должна приводить к перемежению/порче файла или молчаливой перезаписи.
                importMutex.withLock {
                    val dir = backgroundsDir().apply { mkdirs() }
                    val file = File(dir, fileName)

                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching error("Не удалось открыть поток для $uri")
                    input.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_IMPORT_BYTES) {
                            file.delete()
                            return@runCatching error("Файл превышает лимит ${MAX_IMPORT_BYTES} байт")
                        }
                        file.outputStream().use { it.write(bytes) }
                    }

                    // Валидация магическими байтами: повреждённый/не-картинка файл удаляем и сообщаем об ошибке.
                    val imageBytes = file.readBytes()
                    if (!isImage(imageBytes)) {
                        file.delete()
                        return@runCatching error("Файл не является картинкой: $fileName")
                    }

                    Unit
                }
            }
        }
        if (result.isSuccess) refreshAvailableBackgrounds()
        return result
    }

    /** Удаляет импортированную фоновую картинку; префы-заглушки не трогает. */
    fun deleteBackground(value: String): Boolean {
        if (!isImported(value)) return false
        val fileName = parsePrefValue(value) ?: return false
        val file = File(backgroundsDir(), fileName)
        val deleted = file.delete() || !file.exists()
        if (deleted) refreshAvailableBackgrounds()
        return deleted
    }
}
