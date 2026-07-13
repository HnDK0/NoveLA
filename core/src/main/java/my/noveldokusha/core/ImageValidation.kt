package my.noveldokusha.core

import java.io.File

fun isImage(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    return IMAGE_MAGIC_BYTES.any { it(bytes) }
}

fun isCoverValid(file: File): Boolean {
    if (!file.exists() || !file.isFile) return false
    return try {
        val head = ByteArray(16)
        val read = file.inputStream().use { it.read(head) }
        read > 0 && isImage(head.copyOf(read))
    } catch (_: Exception) { false }
}

private val IMAGE_MAGIC_BYTES: List<(ByteArray) -> Boolean> = listOf(
    { it.size >= 3 && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() && it[2] == 0xFF.toByte() },
    { it.size >= 4 && it[0] == 0x89.toByte() && it[1] == 0x50.toByte() && it[2] == 0x4E.toByte() && it[3] == 0x47.toByte() },
    { it.size >= 3 && it[0] == 0x47.toByte() && it[1] == 0x49.toByte() && it[2] == 0x46.toByte() },
    { it.size >= 12 && it[0] == 0x52.toByte() && it[1] == 0x49.toByte() && it[2] == 0x46.toByte() && it[3] == 0x46.toByte()
            && it[8] == 0x57.toByte() && it[9] == 0x45.toByte() && it[10] == 0x42.toByte() && it[11] == 0x50.toByte() },
)
