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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import my.noveldokusha.coreui.theme.Grey0
import my.noveldokusha.coreui.theme.ImageBorderShape
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes
import my.noveldokusha.coreui.theme.isLightTheme

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
    sourceIcon: (@Composable () -> Unit)? = null,
    sourceText: String? = null,
    topLeftBadge: (@Composable () -> Unit)? = null,
    topRightBadge: (@Composable () -> Unit)? = null,
    bottomLeftBadge: (@Composable () -> Unit)? = null,
    forceCache: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = { },
) {
    val rememberedInteractionSource = remember { MutableInteractionSource() }
    val effectiveInteractionSource = interactionSource ?: rememberedInteractionSource
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

            // Source text in top-right corner (on top of image, not clipped)
            sourceText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(topEnd = 0.dp, bottomStart = 12.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Top-left badge (count, etc.) — not clipped
            topLeftBadge?.let {
                Box(modifier = Modifier.align(Alignment.TopStart)) { it() }
            }

            // Top-right badge (priority over sourceText) — not clipped
            if (sourceText == null) topRightBadge?.let {
                Box(modifier = Modifier.align(Alignment.TopEnd)) { it() }
            }

            // Source icon in top-right corner (only if no source text or badge)
            if (sourceText == null && topRightBadge == null) {
                sourceIcon?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        it()
                    }
                }
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
                        .padding(top = 30.dp, bottom = 8.dp)
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
                        .padding(top = 30.dp, bottom = 8.dp)
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                )
            }

            // Bottom-left badge (rating, etc.) — not clipped
            bottomLeftBadge?.let {
                Box(modifier = Modifier.align(Alignment.BottomStart)) { it() }
            }
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
                modifier = Modifier.weight(1f)
            )
            BookImageButtonView(
                title = "Hello there text very long for a title, but many cases just like this",
                coverImageModel = "",
                onClick = { },
                onLongClick = { },
                bookTitlePosition = BookTitlePosition.Outside,
                modifier = Modifier.weight(1f)
            )
        }
    }
}