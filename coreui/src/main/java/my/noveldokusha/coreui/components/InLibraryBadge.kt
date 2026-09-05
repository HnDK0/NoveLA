package my.noveldokusha.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import my.noveldokusha.core.utils.normalizeBookUrl

/**
 * Badge displayed on book covers in catalog/search when the book is already in the library.
 *
 * @param inSameSource true = filled bookmark (added from this source), false = outlined bookmark (added from different source)
 * @param sourceCount number of sources this book is in the library from (>1 shows number on the badge)
 */
@Composable
fun InLibraryBadge(
    inSameSource: Boolean,
    sourceCount: Int,
    modifier: Modifier = Modifier
) {
    val containerColor = if (inSameSource) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val contentColor = if (inSameSource) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val description = if (sourceCount > 1) {
        "In library from $sourceCount sources"
    } else {
        "In library"
    }

    Box(
        modifier = modifier
            .padding(start = 5.dp)
            .width(18.dp)
            .height(30.dp)
            .drawBehind {
                val notch = size.height * 0.22f
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, size.height - notch)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = containerColor)
            }
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier.padding(top = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            if (sourceCount > 1) {
                Text(
                    text = if (sourceCount > 99) "99+" else sourceCount.toString(),
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Badge data maps: normalized URL -> count, title -> count.
 * Used by ViewModels to build reactive badge lookups.
 */
data class LibraryBadgeMaps(
    val urls: Map<String, Int> = emptyMap(),
    val titles: Map<String, Int> = emptyMap()
)

/**
 * Determines the in-library badge state for a book.
 */
data class LibraryBadgeState(
    val inSameSource: Boolean,
    val sourceCount: Int
)

/**
 * Checks if a book is in the library and returns badge state.
 * Priority: URL match (same source) > title match (different source).
 */
fun getLibraryBadgeState(
    bookUrl: String,
    bookTitle: String,
    libraryUrls: Map<String, Int>,
    libraryTitles: Map<String, Int>
): LibraryBadgeState? {
    val urlMatch = libraryUrls[normalizeBookUrl(bookUrl)]
    val titleCount = libraryTitles[bookTitle] ?: 0

    // Exact URL match — this book is in the library from this source
    if (urlMatch != null) {
        return LibraryBadgeState(
            inSameSource = true,
            sourceCount = maxOf(1, titleCount)
        )
    }

    // Title match only — book exists but from a different source
    if (titleCount > 0) {
        return LibraryBadgeState(inSameSource = false, sourceCount = titleCount)
    }

    return null
}
