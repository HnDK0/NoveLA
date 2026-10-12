package my.noveldokusha.features.chapterslist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.noveldokusha.coreui.components.ExpandableText
import my.noveldokusha.coreui.components.ImageView
import my.noveldokusha.coreui.theme.ImageBorderShape
import my.noveldokusha.coreui.theme.clickableNoIndicator
import my.noveldokusha.chapterslist.R
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.strings.R as StringsR

/**
 * Prototype detail page header (pages/detail):
 * fixed-size cover + editorial title block, 3-column stats grid,
 * primary action row, source card, book tools and chapter heading.
 * All information comes from the real book state — no decorative fakes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChaptersScreenHeader(
    bookState: ChaptersScreenState.BookState,
    genres: List<String>,
    rating: String,
    status: String,
    lastUpdateDate: String,
    sourceCatalogName: String,
    numberOfChapters: Int,
    readChapters: Int,
    paddingValues: PaddingValues,
    translatedTitle: String?,
    translatedDescription: String?,
    isTranslating: Boolean,
    showTranslateButton: Boolean,
    onTranslateClick: () -> Unit,
    onClearTranslationClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCoverLongClick: () -> Unit,
    onGlobalSearchClick: (input: String, contentType: String) -> Unit,
    onResumeReading: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onDownloadAllChapters: () -> Unit,
    onDownloadNext100Chapters: () -> Unit,
    onScrollToLastRead: (() -> Unit)?,
    onScrollToChapter: () -> Unit,
    bookCategory: String,
    categories: () -> List<String>,
    onCategoryClick: () -> Unit,
    coverReferer: String? = null,
) {
    val coverImageModel = bookState.coverImageUrl?.let {
        rememberResolvedBookImagePath(
            bookUrl = bookState.url,
            imagePath = it
        )
    } ?: R.drawable.ic_baseline_empty_24

    Column(
        modifier = modifier
            .padding(top = paddingValues.calculateTopPadding())
            .padding(horizontal = 16.dp),
    ) {
        // — Hero (prototype .detail-hero): fixed cover + title block —
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            var showImageFullScreen by rememberSaveable { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .aspectRatio(118f / 170f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickableNoIndicator { showImageFullScreen = true },
            ) {
                ImageView(
                    imageModel = coverImageModel,
                    referer = coverReferer,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showImageFullScreen) Dialog(
                onDismissRequest = { showImageFullScreen = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickableNoIndicator { showImageFullScreen = false },
                    contentAlignment = Alignment.Center
                ) {
                    ImageView(
                        imageModel = coverImageModel,
                        referer = coverReferer,
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(ImageBorderShape)
                            .fillMaxWidth()
                            .aspectRatio(1f / 1.45f)
                            .clickableNoIndicator { showImageFullScreen = false },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .weight(1f),
            ) {
                Text(
                    text = stringResource(StringsR.string.source_dot_novel_details, sourceCatalogName).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = translatedTitle ?: bookState.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickableNoIndicator {
                            onGlobalSearchClick(bookState.title, bookState.contentType)
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Prototype .detail-tags: genre pills + accent status pill
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    genres.take(3).forEach { genre ->
                        DetailPill(text = genre)
                    }
                    if (status.isNotBlank()) {
                        DetailPill(
                            text = status,
                            accent = true,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // — Stats grid (prototype .detail-stats): rating | read | status+updated —
        val statusLabel = if (bookState.completed) stringResource(StringsR.string.completed)
        else stringResource(StringsR.string.reading)
        val updatedLabel = if (lastUpdateDate.isNotBlank())
            stringResource(StringsR.string.last_update_short, lastUpdateDate)
        else stringResource(StringsR.string.original_catalogue)
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                DetailStatCell(
                    value = rating.ifBlank { "—" },
                    label = stringResource(StringsR.string.rating_label),
                    modifier = Modifier.weight(1f),
                )
                VerticalHairline()
                DetailStatCell(
                    value = "$readChapters / $numberOfChapters",
                    label = stringResource(StringsR.string.reading_progress_label),
                    modifier = Modifier.weight(1f),
                )
                VerticalHairline()
                DetailStatCell(
                    value = statusLabel,
                    label = updatedLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // — Actions row (prototype .detail-actions): primary continue + icon tools —
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                onClick = onResumeReading,
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (readChapters > 0) stringResource(StringsR.string.resume_reading)
                        else stringResource(StringsR.string.start_reading),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }

            DetailIconButton(
                content = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(id = R.string.go_to_chapter),
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = onScrollToChapter,
            )

            if (showTranslateButton) {
                DetailIconButton(
                    content = {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Icon(
                                imageVector = if (translatedTitle != null || translatedDescription != null)
                                    Icons.Outlined.Close else Icons.Outlined.GTranslate,
                                contentDescription = if (translatedTitle != null || translatedDescription != null)
                                    stringResource(R.string.clear_translation) else stringResource(R.string.translate),
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    onClick = {
                        if (translatedTitle != null || translatedDescription != null) {
                            onClearTranslationClick()
                        } else {
                            onTranslateClick()
                        }
                    },
                )
            }
        }

        // — Source card (prototype .detail-source-card) —
        if (!bookState.url.isLocalUri) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(27.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "↗",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(StringsR.string.open_source_page),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(StringsR.string.original_catalogue),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                    Text(
                        text = stringResource(StringsR.string.open_in_browser),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = { onOpenInBrowser(bookState.url) }),
                    )
                }
            }
        }

        // — About this story (prototype .synopsis) —
        val displayDesc = (translatedDescription ?: bookState.description).trim()
        if (displayDesc.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(StringsR.string.about_this_story).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            SelectionContainer {
                ExpandableText(
                    text = displayDesc,
                    linesForExpand = 4,
                )
            }
        }

        // — Category + genres —
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val categoryLabel = when (bookCategory) {
                "" -> stringResource(R.string.reading)
                "Completed" -> stringResource(R.string.completed)
                else -> bookCategory
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onCategoryClick,
                ),
            ) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (genres.size > 3) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "+${genres.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        // — Chapter heading (prototype .chapter-heading) —
        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(StringsR.string.reading_order_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(StringsR.string.chapters),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = numberOfChapters.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(StringsR.string.download_all_chapters),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onDownloadAllChapters)
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            )
        }

        // — Chapter tools (prototype .chapter-tools) —
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                onClick = onDownloadNext100Chapters,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(StringsR.string.download_next_100_chapters),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
                )
            }
            if (onScrollToLastRead != null) {
                Surface(
                    onClick = onScrollToLastRead,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.scroll_to_last_read_chapter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))
    }
}

/** Prototype `.pill`: hairline rounded-99 tag; accent variant uses soft primary fill. */
@Composable
private fun DetailPill(text: String, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = if (accent) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .then(
                if (accent) Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(99.dp),
                    )
                else Modifier
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(99.dp),
                    )
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** One stats-grid cell: bold value over a muted caption. */
@Composable
private fun RowScope.DetailStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 9.dp, horizontal = 3.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

/** Prototype `.action-button.icon-only`: 39dp bordered square tool. */
@Composable
private fun DetailIconButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(width = 39.dp, height = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
