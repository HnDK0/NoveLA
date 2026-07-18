package my.noveldokusha.features.chapterslist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.AnimatedTransition
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes
import my.noveldokusha.chapterslist.R
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.feature.local_database.tables.Chapter

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
internal fun ChaptersScreenChapterItem(
    chapterWithContext: ChapterWithContext,
    translatedTitle: String? = null,
    selected: Boolean,
    isLocalSource: Boolean,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val chapter = chapterWithContext.chapter

    val targetContainerColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        highlighted -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 200),
        label = "chapterItemBackground"
    )

    val stableOnClick = remember(onClick) { onClick }
    val stableOnLongClick = remember(onLongClick) { onLongClick }
    val stableOnDownload = remember(onDownload) { onDownload }

    val badge: @Composable (() -> Unit)? = remember(chapterWithContext.lastReadChapter, chapter.read) {
        when {
            chapterWithContext.lastReadChapter -> {
                {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(id = R.string.last_read),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            chapter.read -> {
                {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = stringResource(id = R.string.read),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            else -> null
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .combinedClickable(
                    onClick = stableOnClick,
                    onLongClick = stableOnLongClick,
                )
        ) {
            SlimListItem(
                headlineContent = {
                    Text(
                        text = translatedTitle ?: chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = if (badge != null) {
                    {
                        badge()
                    }
                } else null,
                trailingContent = if (isLocalSource) null else {
                    {
                        AnimatedTransition(
                            targetState = chapterWithContext.downloaded,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { downloaded ->
                            IconButton(onClick = stableOnDownload) {
                                Icon(
                                    if (downloaded) Icons.Filled.CloudDownload
                                    else Icons.Outlined.CloudDownload,
                                    null
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}


@PreviewThemes
@Composable
private fun PreviewView(
    @PreviewParameter(PreviewProvider::class) previewProviderState: PreviewProviderState
) {
    InternalTheme {
        ChaptersScreenChapterItem(
            chapterWithContext = previewProviderState.chapterWithContext,
            selected = previewProviderState.selected,
            isLocalSource = false,
            onLongClick = {},
            onClick = {},
            onDownload = {}
        )
    }
}


private data class PreviewProviderState(
    val chapterWithContext: ChapterWithContext,
    val selected: Boolean
)

private class PreviewProvider : PreviewParameterProvider<PreviewProviderState> {
    override val values = sequenceOf(
        PreviewProviderState(
            chapterWithContext = ChapterWithContext(
                chapter = Chapter(
                    title = "Title of the chapter",
                    url = "url",
                    bookUrl = "bookUrl",
                    lastReadOffset = 0,
                    lastReadPosition = 0,
                    position = 0,
                    read = false
                ),
                downloaded = false,
                lastReadChapter = false
            ),
            selected = false
        ),
        PreviewProviderState(
            chapterWithContext = ChapterWithContext(
                chapter = Chapter(
                    title = "Title of the chapter, Title of the chapter, Title of the chapter, Title of the chapter, Title of the chapter,Title of the chapter ,Title of the chapter",
                    url = "url",
                    bookUrl = "bookUrl",
                    lastReadOffset = 0,
                    lastReadPosition = 0,
                    position = 0,
                    read = true
                ),
                downloaded = true,
                lastReadChapter = false
            ),
            selected = false
        ),
        PreviewProviderState(
            chapterWithContext = ChapterWithContext(
                chapter = Chapter(
                    title = "Title of the chapter",
                    url = "url",
                    bookUrl = "bookUrl",
                    lastReadOffset = 0,
                    lastReadPosition = 0,
                    position = 0,
                    read = false
                ),
                downloaded = true,
                lastReadChapter = true
            ),
            selected = true
        )
    )
}