package my.noveldokusha.coreui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.strings.R
import java.util.Locale

/** Тип рейтинга книги: оценка по шкале или позиция в рейтинге (ранг). */
enum class BookRatingType { Score, Rank }

/** Разобранный рейтинг: тип, отображаемое значение и нормализованный балл (для Score). */
data class ParsedBookRating(
    val type: BookRatingType,
    val value: String,
    val score: Float? = null,
)

// Regex ловит только цифры: отрицательные значения ("-3") намеренно коэрсятся
// в модуль ("3") — сознательная всеядность под грязные данные источников, соответствует примерам спека.
private val NUMBER_REGEX = Regex("""\d+(?:[.,]\d+)?""")
private val SCALE_REGEX = Regex("""/(\d+(?:[.,]\d+)?)""")
private val RANK_REGEX = Regex("""\brank\b|#\d+""", RegexOption.IGNORE_CASE)

/**
 * Нормализует «сырую» строку рейтинга из источника в [ParsedBookRating].
 * Источники отдают гетерогенные форматы: "4.5", "4,8/5", "Rank: 3", "#12" и т.п.
 */
fun parseBookRating(raw: String?): ParsedBookRating? {
    val trimmed = raw?.trim() ?: return null
    if (trimmed.isEmpty()) return null

    val numberMatch = NUMBER_REGEX.find(trimmed) ?: return null
    val numberStr = numberMatch.value.replace(',', '.')

    // Ранг: "Rank 3", "Rank #12" → целое значение, балл не считается.
    if (RANK_REGEX.containsMatchIn(trimmed)) {
        val intValue = numberStr.toIntOrNull() ?: return null
        return ParsedBookRating(BookRatingType.Rank, intValue.toString())
    }

    // Иначе — оценка по шкале ("4.6/5", "8.7/10") или голое число ("4.3").
    val value = numberStr.toFloat()
    val scaleMatch = SCALE_REGEX.find(trimmed)
    val normalized = if (scaleMatch != null) {
        val scale = scaleMatch.groupValues[1].replace(',', '.').toFloat()
        // Защита от деления на ноль: "0/0" и прочие патологические "/0" → null (иначе NaN).
        if (scale <= 0f) return null
        if (value > scale) return null
        value / scale * 5f
    } else {
        if (value > 5f) return null
        value
    }

    if (normalized > 5f) return null

    val display = String.format(Locale.US, "%.1f", normalized).trimEnd('0').trimEnd('.')
    return ParsedBookRating(BookRatingType.Score, display, normalized)
}

/** Компактный бейдж рейтинга на обложке книги (каталог/библиотека/поиск). */
@Composable
fun BookRatingBadge(rating: String?, modifier: Modifier = Modifier) {
    val parsed = remember(rating) { parseBookRating(rating) } ?: return
    Surface(
        // Стиль в тон плашке источника (6.dp), topEnd = 0 — прижат к верхнему правому углу обложки
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 0.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // vertical = 3.dp: высота 12.dp (иконка) + 6.dp = 18.dp — в тон плашке источника
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = if (parsed.type == BookRatingType.Rank) Icons.Filled.EmojiEvents else Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = parsed.value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                ),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** Чип рейтинга со словами на странице книги. */
@Composable
fun BookRatingChip(rating: String?, modifier: Modifier = Modifier) {
    val parsed = remember(rating) { parseBookRating(rating) } ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (parsed.type == BookRatingType.Rank) Icons.Filled.EmojiEvents else Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (parsed.type == BookRatingType.Rank) {
                stringResource(R.string.book_rank_format, parsed.value)
            } else {
                stringResource(R.string.book_rating_format, parsed.value)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
