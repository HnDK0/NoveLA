package my.noveldokusha.tooling.novel_migration.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.strings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationHistoryScreen(
    groups: List<SourcePairGroup>,
    onPressBack: () -> Unit,
    onDeleteGroup: (oldSourceId: String, newSourceId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.migration_history)) },
                navigationIcon = {
                    IconButton(onClick = onPressBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.migration_no_history))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { "${it.oldSourceId}_${it.newSourceId}" }) { group ->
                    SourcePairGroupItem(
                        group = group,
                        onDelete = { onDeleteGroup(group.oldSourceId, group.newSourceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePairGroupItem(
    group: SourcePairGroup,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${group.oldSourceName} → ${group.newSourceName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.migration_books_count, group.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (group.completedCount > 0 || group.partialCount > 0) {
                    Text(
                        buildString {
                            if (group.completedCount > 0) {
                                append(stringResource(R.string.migration_completed_count, group.completedCount))
                            }
                            if (group.partialCount > 0) {
                                if (isNotEmpty()) append(", ")
                                append(stringResource(R.string.migration_partial_count, group.partialCount))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
