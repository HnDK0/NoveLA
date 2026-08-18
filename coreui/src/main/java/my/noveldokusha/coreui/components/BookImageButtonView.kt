package my.noveldokusha.coreui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.AppTestTags
import my.noveldokusha.coreui.R
import my.noveldokusha.coreui.theme.ImageBorderShape
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes

enum class BookTitlePosition {
    Inside, Outside, Hidden
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookImageButtonView(
    title: String,
    coverImageModel: Any,
    modifier: Modifier = Modifier,
    bookTitlePosition: BookTitlePosition = BookTitlePosition.Inside,
    indication: Indication = LocalIndication.current,
    interactionSource: MutableInteractionSource? = null,
    topLeftBadge: (@Composable () -> Unit)? = null,
    topRightBadge: (@Composable () -> Unit)? = null,
    sourceStripUnreadCount: Int? = null,
    sourceStripSourceName: String? = null,
    sourceStripOnCover: Boolean = true,
    forceCache: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = { },
) {
    val rememberedInteractionSource = remember { MutableInteractionSource() }
    val effectiveInteractionSource = interactionSource ?: rememberedInteractionSource
    // Полоса источника рендерится когда задан хотя бы один из двух контентов — иначе вёрстка идентична прежней.
    val stripUnreadCount = sourceStripUnreadCount
    val stripSourceName = sourceStripSourceName
    val showStrip = stripUnreadCount != null || stripSourceName != null
    // При полосе на кромке (20.dp) поднимаем заголовок, чтобы он не перекрывался.
    val titleBottomPadding = if (showStrip && sourceStripOnCover) 32.dp else 8.dp
    Column(modifier = modifier.testTag(AppTestTags.BOOK_IMAGE_BUTTON_VIEW)) {
        Box(
            Modifier
                .padding(2.dp)
                .clip(ImageBorderShape)
                .fillMaxWidth()
                .aspectRatio(1 / 1.45f)
        ) {
            // Image with clipping — badges must be OUTSIDE this Box
            Box(
                Modifier
                    .matchParentSize()
                    .clip(ImageBorderShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .combinedClickable(
                        indication = indication,
                        interactionSource = effectiveInteractionSource,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
            ) {
                ImageView(
                    imageModel = coverImageModel,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    error = R.drawable.default_book_cover,
                    forceCache = forceCache,
                )
            }

            // Top-left badge (count, etc.) — not clipped
            topLeftBadge?.let {
                Box(modifier = Modifier.align(Alignment.TopStart)) { it() }
            }

            // Top-right badge (rating, etc.) — not clipped
            topRightBadge?.let {
                Box(modifier = Modifier.align(Alignment.TopEnd)) { it() }
            }
            if (bookTitlePosition == BookTitlePosition.Inside) {
                // Stroke outline for better readability
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.0f),
                                0.4f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                1f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            )
                        )
                        .padding(top = 30.dp, bottom = titleBottomPadding)
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        drawStyle = Stroke(
                            miter = 4f,
                            width = 4f,
                            join = StrokeJoin.Miter
                        )
                    )
                )
                // Fill text on top
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(top = 30.dp, bottom = titleBottomPadding)
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                )
            }

            // Полоса источника на кромке обложки — низ скругляется внешним clip(ImageBorderShape)
            if (sourceStripOnCover && (stripUnreadCount != null || stripSourceName != null)) {
                SourceStrip(
                    unreadCount = stripUnreadCount,
                    sourceName = stripSourceName,
                    onCover = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }
        // Плашка под обложкой: рендерится только при непустом контенте полосы
        if (!sourceStripOnCover && (stripUnreadCount != null || stripSourceName != null)) {
            SourceStrip(
                unreadCount = stripUnreadCount,
                sourceName = stripSourceName,
                onCover = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }
        if (bookTitlePosition == BookTitlePosition.Outside) {
            Text(
                text = title,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(4.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Полоса «непрочитанные | источник»: фикс. окно 32.dp слева, делитель 1.dp, имя источника справа (weight). */
@Composable
private fun SourceStrip(
    unreadCount: Int?,
    sourceName: String?,
    onCover: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // На обложке — градиентный скрим (полоса «вырастает» из обложки), под обложкой — диагональный
    // градиент 0.65→0.85: глубина сверху вниз + затухание к правому краю
    val stripBackground: Brush = if (onCover) {
        Brush.verticalGradient(
            0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            1f to MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(20.dp)
            .background(stripBackground)
            .padding(horizontal = 6.dp)
    ) {
        // Фиксированное окно счётчика: без внутреннего horizontal padding — делитель всегда на одном месте
        if (unreadCount != null) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (unreadCount == 0) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = unreadCount.toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
            )
        }
        if (sourceName != null) {
            Text(
                text = sourceName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            )
        }
    }
}

/** Иконка типа контента: "manga" → [R.drawable.ic_content_type_manga], любое другое (включая null и "") → [R.drawable.ic_content_type_novel]. */
fun String?.toContentTypeBadgeIcon(): Int =
    if (this == "manga") R.drawable.ic_content_type_manga else R.drawable.ic_content_type_novel

@PreviewThemes
@Composable
private fun PreviewView() {
    InternalTheme {
        Row {
            BookImageButtonView(
                title = "Hello there",
                coverImageModel = "",
                onClick = { },
                onLongClick = { },
                bookTitlePosition = BookTitlePosition.Inside,
                sourceStripUnreadCount = 0,
                sourceStripSourceName = "Local",
                modifier = Modifier.weight(1f)
            )
            BookImageButtonView(
                title = "Hello there text very long for a title, but many cases just like this",
                coverImageModel = "",
                onClick = { },
                onLongClick = { },
                bookTitlePosition = BookTitlePosition.Outside,
                sourceStripUnreadCount = 99999,
                sourceStripSourceName = "Very long source name that must be cut off with ellipsis",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Плашка под обложкой (sourceStripOnCover = false) — вариант для сравнения на F3
@PreviewThemes
@Composable
private fun PreviewViewStripPlaque() {
    InternalTheme {
        Row {
            BookImageButtonView(
                title = "Hello there",
                coverImageModel = "",
                onClick = { },
                onLongClick = { },
                bookTitlePosition = BookTitlePosition.Inside,
                sourceStripUnreadCount = 0,
                sourceStripSourceName = "Local",
                sourceStripOnCover = false,
                modifier = Modifier.weight(1f)
            )
            BookImageButtonView(
                title = "Hello there text very long for a title, but many cases just like this",
                coverImageModel = "",
                onClick = { },
                onLongClick = { },
                bookTitlePosition = BookTitlePosition.Inside,
                sourceStripUnreadCount = 99999,
                sourceStripSourceName = "Very long source name that must be cut off with ellipsis",
                sourceStripOnCover = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}