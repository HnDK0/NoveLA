@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
)

package my.noveldokusha.catalogexplorer

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.components.AnimatedTransition
import my.noveldokusha.coreui.components.ImageView
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes
import my.noveldokusha.data.CatalogItem
import my.noveldokusha.scraper.DatabaseInterface
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.displayName
import my.noveldokusha.scraper.fixtures.fixturesCatalogList
import my.noveldokusha.scraper.fixtures.fixturesDatabaseList
import my.noveldokusha.core.getLanguageDisplayName
import my.noveldokusha.strings.R as StringsR

/**
 * Prototype finder page (pages/finder): editorial intro, integrated search box,
 * shortcut cards, then hairline source rows + bordered database card.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CatalogList(
    innerPadding: PaddingValues,
    databasesList: List<DatabaseInterface>,
    sourcesList: List<CatalogItem>,
    onDatabaseClick: (DatabaseInterface) -> Unit,
    onSourceClick: (SourceInterface.Catalog) -> Unit,
    onSourceSetPinned: (id: String, pinned: Boolean) -> Unit,
    translationSettingsExtensionIds: Map<String, String> = emptyMap(), // catalog.id → extensionId
    onTranslationSettingsClick: (String) -> Unit = {}, // extensionId
    pluginEnabledMap: Map<String, Boolean> = emptyMap(),
    onGlobalSearchClick: () -> Unit = {},
    onAddByUrlClick: () -> Unit = {},
    onImportFileClick: () -> Unit = {},
    onManageExtensionsClick: () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 300.dp),
        modifier = Modifier.padding(paddingValues = innerPadding)
    ) {
        // — Editorial intro (prototype .page-heading) —
        item(key = "intro") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = stringResource(StringsR.string.title_finder).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(StringsR.string.find_a_story),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 26.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(StringsR.string.finder_page_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // — Search box entry (opens global search; prototype .global-search) —
        item(key = "search") {
            FinderSearchBox(
                onClick = onGlobalSearchClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // — Shortcut cards (prototype .finder-shortcuts) —
        item(key = "shortcuts") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FinderShortcutCard(
                    glyph = "＋",
                    title = stringResource(StringsR.string.add_by_url),
                    subtitle = stringResource(StringsR.string.add_by_url_sub),
                    onClick = onAddByUrlClick,
                    modifier = Modifier.weight(1f),
                )
                FinderShortcutCard(
                    glyph = "⇧",
                    title = stringResource(StringsR.string.import_local_file),
                    subtitle = stringResource(StringsR.string.import_local_file_sub),
                    onClick = onImportFileClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // — Section head: BROWSE / Sources & catalogues + Manage extensions —
        item(key = "browse-head") {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(StringsR.string.browse_eyebrow).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(StringsR.string.browse_catalogues),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(StringsR.string.manage_extensions),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onManageExtensionsClick),
                )
            }
        }

        // — Databases: bordered card rows (prototype .database-row) —
        items(
            items = databasesList,
            key = { "db_${it.baseUrl}" }
        ) { database ->
            DatabaseRow(
                database = database,
                onClick = { onDatabaseClick(database) },
            )
        }

        // — Sources: hairline rows (prototype .source-row) —
        item(key = "sources-head") {
            Text(
                text = stringResource(StringsR.string.sources).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
            )
        }

        items(
            items = sourcesList,
            key = { it.catalog.id }
        ) { item ->
            SourceRow(
                item = item,
                onClick = { onSourceClick(item.catalog) },
                onPinToggle = { onSourceSetPinned(item.catalog.id, !item.pinned) },
                translationSettingsExtensionIds = translationSettingsExtensionIds,
                onTranslationSettingsClick = onTranslationSettingsClick,
                pluginEnabledMap = pluginEnabledMap,
            )
        }
    }
}

/** Source letter/icon square (prototype .source-icon: 30dp bordered rounded-8). */
@Composable
private fun SourceIconBadge(catalog: SourceInterface.Catalog) {
    val iconResId = catalog.iconResId
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (iconResId != null || !catalog.iconUrl.isNullOrBlank()) {
            ImageView(
                imageModel = iconResId ?: catalog.iconUrl,
                modifier = Modifier.size(22.dp),
                error = my.noveldokusha.coreui.R.drawable.default_icon,
            )
        } else {
            Text(
                text = catalog.displayName().take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Hairline source row (prototype .source-row). */
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.SourceRow(
    item: CatalogItem,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    translationSettingsExtensionIds: Map<String, String>,
    onTranslationSettingsClick: (String) -> Unit,
    pluginEnabledMap: Map<String, Boolean>,
) {
    var openConfig by rememberSaveable(item.catalog.id) { mutableStateOf(false) }
    val catalog = item.catalog

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateItem()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        SourceIconBadge(catalog)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = catalog.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val typeLabel = when (catalog.contentType) {
                "manga" -> stringResource(StringsR.string.content_type_manga)
                "video" -> stringResource(StringsR.string.content_type_video)
                else -> stringResource(StringsR.string.content_type_novel)
            }
            val lang = catalog.languageTag?.let { getLanguageDisplayName(it) } ?: ""
            Text(
                text = listOf(typeLabel, lang).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (catalog is SourceInterface.Configurable) {
            IconButton(onClick = { openConfig = true }, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(StringsR.string.configuration),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val extensionId = translationSettingsExtensionIds[catalog.id]
        if (extensionId != null && catalog.contentType !in setOf("manga", "video")) {
            IconButton(
                onClick = { onTranslationSettingsClick(catalog.id) },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = stringResource(StringsR.string.extension_translation_settings),
                    modifier = Modifier.size(16.dp),
                    tint = if (pluginEnabledMap[extensionId] == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.pinned) {
            IconButton(onClick = onPinToggle, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = stringResource(StringsR.string.pin_or_unpin_source),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            // Prototype arrow affordance for unpinned rows
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .clickable(onClick = onPinToggle)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )

    if (openConfig && catalog is SourceInterface.Configurable) {
        AlertDialog(
            onDismissRequest = { openConfig = false },
            confirmButton = {
                FilledTonalButton(
                    onClick = { openConfig = false },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(StringsR.string.close),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = { catalog.ScreenConfig() },
            icon = {
                Icon(
                    Icons.Filled.Settings,
                    stringResource(id = StringsR.string.configuration),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

/** Bordered database card (prototype .database-row). */
@Composable
private fun DatabaseRow(
    database: DatabaseInterface,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp),
        ) {
            ImageView(
                imageModel = database.iconUrl,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp)),
                error = my.noveldokusha.coreui.R.drawable.default_icon,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = database.nameStrId),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(StringsR.string.database),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

/** Prototype `.search-box` as a tappable entry into global search. */
@Composable
private fun FinderSearchBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                painter = painterResource(my.noveldokusha.coreui.R.drawable.ic_baseline_search_24),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(StringsR.string.search_all_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(25.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↵",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Prototype `.finder-shortcuts` card: glyph square + title + subtitle. */
@Composable
private fun FinderShortcutCard(
    glyph: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(70.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@PreviewThemes
@Composable
private fun PreviewView() {
    val catalogItemsList = fixturesCatalogList().mapIndexed { index, it ->
        CatalogItem(catalog = it, pinned = index % 2 == 0)
    }
    InternalTheme {
        CatalogList(
            innerPadding = PaddingValues(),
            databasesList = fixturesDatabaseList(),
            sourcesList = catalogItemsList,
            onDatabaseClick = {},
            onSourceClick = {},
            onSourceSetPinned = { _, _ -> },
        )
    }
}
