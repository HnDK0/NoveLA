package my.noveldokusha.features.chapterslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.noveldokusha.chapterslist.R
import my.noveldokusha.feature.local_database.ChapterWithContext

@Composable
internal fun GoToChapterDialog(
    chapters: List<ChapterWithContext>,
    // index — позиция в LazyColumn (+1 за header), url — для подсветки
    onChapterSelected: (index: Int, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = if (query.isBlank()) chapters
    else chapters.filter { it.chapter.title.contains(query.trim(), ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column {
                Text(
                    text = stringResource(id = R.string.go_to_chapter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(id = R.string.search_chapter_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                HorizontalDivider(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .alpha(0.4f)
                )

                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.no_chapters_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            items = filtered,
                            key = { it.chapter.url }
                        ) { chapterWithContext ->
                            // Реальный индекс в оригинальном списке (+1 за header)
                            val realIndex = chapters.indexOf(chapterWithContext) + 1
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChapterSelected(realIndex, chapterWithContext.chapter.url)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = chapterWithContext.chapter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (chapterWithContext.chapter.read)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            HorizontalDivider(modifier = Modifier.alpha(0.15f))
                        }
                    }
                }
            }
        }
    }
}