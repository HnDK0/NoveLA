package my.noveldokusha.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import my.noveldokusha.coreui.theme.colorAccent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.noveldokusha.coreui.components.ImageView
import my.noveldokusha.core.Extension
import my.noveldokusha.coreui.components.MyButton
import my.noveldokusha.coreui.components.SlimListItem
import java.util.Locale
import my.noveldokusha.core.getLanguageDisplayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.R
import my.noveldokusha.strings.R as StringsR


@Composable
fun ExtensionsScreen(
    innerPadding: PaddingValues,
    onBackPressed: (() -> Unit)? = null,
    showExtensionsLanguageFilter: Boolean = false,
    onExtensionsLanguageFilterDismiss: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onImportLua: () -> Unit = {}
) {
    val viewModel = hiltViewModel<ExtensionsManagerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    UnifiedExtensionsScreen(
        innerPadding = innerPadding,
        state = state,
        viewModel = viewModel,
        onBackPressed = onBackPressed,
        showExtensionsLanguageFilter = showExtensionsLanguageFilter,
        onExtensionsLanguageFilterDismiss = onExtensionsLanguageFilterDismiss,
        onRefresh = onRefresh,
        onImportLua = onImportLua
    )
}

@Composable
private fun UnifiedExtensionsScreen(
    innerPadding: PaddingValues,
    state: ExtensionsScreenState,
    viewModel: ExtensionsManagerViewModel,
    @Suppress("UNUSED_PARAMETER") onBackPressed: (() -> Unit)?,
    @Suppress("UNUSED_PARAMETER") showExtensionsLanguageFilter: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onExtensionsLanguageFilterDismiss: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onRefresh: (() -> Unit)? = null,
    onImportLua: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = innerPadding.calculateTopPadding())
    ) {
        // Content area
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            state.error != null -> {
                val errorText = state.error
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnRefresh) }) {
                            Text(stringResource(my.noveldokusha.strings.R.string.retry))
                        }
                    }
                }
            }
            else -> {
                val localExtensionDesc = stringResource(my.noveldokusha.strings.R.string.local_extension_description)
                val localExtensionAuthor = stringResource(my.noveldokusha.strings.R.string.extension_author_local)
                val filteredExtensions = state.availableExtensions
                    .filter { state.selectedLanguages.isEmpty() || it.language in state.selectedLanguages }
                    .filter { state.selectedContentType.isEmpty() || it.contentType == state.selectedContentType || (state.selectedContentType == "novel" && it.contentType.isEmpty()) }

                val localInstalledExtensions = remember(state.extensions, state.availableExtensions, state.selectedLanguages) {
                    val ids = filteredExtensions.map { it.id }.toSet()
                    state.extensions
                        .filter { it.id !in ids }
                        .filter { state.selectedLanguages.isEmpty() || it.language in state.selectedLanguages }
                        .map { installed ->
                            ExtensionInfo(
                                id = installed.id,
                                name = installed.name,
                                description = localExtensionDesc,
                                author = localExtensionAuthor,
                                version = installed.version,
                                remoteVersion = installed.version,
                                codeUrl = "",
                                iconUrl = installed.iconUrl.orEmpty(),
                                language = installed.language,
                                isInstalled = true,
                                isEnabled = installed.enabled,
                                isLocal = true
                            )
                        }
                }

                val installedExtensions = remember(filteredExtensions, localInstalledExtensions) {
                    (filteredExtensions.filter { it.isInstalled } + localInstalledExtensions)
                        .sortedBy { it.name.lowercase(Locale.ROOT) }
                }

                val availableExtensions = remember(filteredExtensions) {
                    filteredExtensions.filter { !it.isInstalled }.sortedBy { it.name.lowercase(Locale.ROOT) }
                }

                if (filteredExtensions.isEmpty() && localInstalledExtensions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        Text(
                            text = stringResource(my.noveldokusha.strings.R.string.no_extensions_available),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(
                            onClick = onImportLua,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(my.noveldokusha.strings.R.string.import_lua))
                        }
                        FilledTonalButton(
                            onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnRefresh) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(my.noveldokusha.strings.R.string.refresh))
                        }
                        }
                    }
                } else {
                    var searchQuery by rememberSaveable { mutableStateOf("") }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(StringsR.string.search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true
                    )

                    // Content type filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val contentTypeOptions = listOf(
                            "" to stringResource(StringsR.string.all_categories),
                            "manga" to stringResource(StringsR.string.content_type_manga),
                            "novel" to stringResource(StringsR.string.content_type_novel),
                        )
                        contentTypeOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = state.selectedContentType == value,
                                onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnContentTypeFilterToggle(value)) },
                                label = { Text(label) },
                            )
                        }
                    }

                    val searchFilteredExtensions = if (searchQuery.isBlank()) {
                        filteredExtensions
                    } else {
                        filteredExtensions.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    val searchLocalInstalled = if (searchQuery.isBlank()) {
                        localInstalledExtensions
                    } else {
                        localInstalledExtensions.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    val searchInstalledExtensions = remember(searchFilteredExtensions, searchLocalInstalled) {
                        (searchFilteredExtensions.filter { it.isInstalled } + searchLocalInstalled)
                            .sortedBy { it.name.lowercase(Locale.ROOT) }
                    }

                    val searchAvailableExtensions = remember(searchFilteredExtensions) {
                        searchFilteredExtensions.filter { !it.isInstalled }.sortedBy { it.name.lowercase(Locale.ROOT) }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 300.dp),
                    ) {
                        if (searchInstalledExtensions.isNotEmpty()) {
                            item(contentType = "header") {
                                val hasUpdates = searchInstalledExtensions.any { it.isUpdateAvailable }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(my.noveldokusha.strings.R.string.installed),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (hasUpdates) {
                                        TextButton(
                                            onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnUpdateAll) }
                                        ) {
                                            Text(stringResource(StringsR.string.update_all))
                                        }
                                    }
                                }
                            }

                            items(searchInstalledExtensions, key = { extension -> "installed_${extension.id}" }, contentType = { "extension" }) { extension ->
                                ExtensionListItem(
                                    extension = extension,
                                    viewModel = viewModel
                                )
                            }
                        }

                        if (searchAvailableExtensions.isNotEmpty()) {
                            item(contentType = "header") {
                                Text(
                                    text = stringResource(my.noveldokusha.strings.R.string.available),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 8.dp),
                                )
                            }

                            items(searchAvailableExtensions, key = { extension -> "available_${extension.id}" }, contentType = { "extension" }) { extension ->
                                ExtensionListItem(
                                    extension = extension,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Repository URL dialog
    if (state.showRepositoryDialog) {
        RepositoryUrlDialog(
            state = state,
            viewModel = viewModel
        )
    }

    if (state.showLuaEditor) {
        LuaEditorDialog(
            title = state.luaEditorTitle,
            code = state.luaEditorCode,
            error = state.luaEditorError,
            onCodeChange = { viewModel.onEvent(ExtensionsScreenEvent.OnLuaEditorChange(it)) },
            onDismiss = { viewModel.onEvent(ExtensionsScreenEvent.OnLuaEditorDismiss) },
            onSave = { viewModel.onEvent(ExtensionsScreenEvent.OnLuaEditorSave) }
        )
    }
}

@Composable
private fun RepositoryUrlDialog(
    state: ExtensionsScreenState,
    viewModel: ExtensionsManagerViewModel
) {
    var tempUrl by remember { mutableStateOf(state.repositoryUrl) }

    AlertDialog(
        onDismissRequest = {
            viewModel.onEvent(ExtensionsScreenEvent.OnHideRepositoryDialog)
        },
        title = {
            Text("Repository URL")
        },
        text = {
            OutlinedTextField(
                value = tempUrl,
                onValueChange = { tempUrl = it },
                label = { Text("Extensions Repository URL") },
                placeholder = { Text("https://raw.githubusercontent.com/.../index.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.onEvent(ExtensionsScreenEvent.OnHideRepositoryDialog)
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                FilledTonalButton(
                    onClick = {
                        viewModel.onEvent(ExtensionsScreenEvent.OnUpdateRepositoryUrl(tempUrl))
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Save",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}


@Composable
private fun ContentTypeBadge(contentType: String) {
    // ponytail: empty contentType = novel (same convention as library covers)
    val (textRes, color) = when (contentType) {
        "manga" -> StringsR.string.content_type_manga to MaterialTheme.colorScheme.primary
        else -> StringsR.string.content_type_novel to MaterialTheme.colorScheme.tertiary
    }
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun ExtensionListItem(
    extension: ExtensionInfo,
    viewModel: ExtensionsManagerViewModel
) {
    SlimListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ContentTypeBadge(contentType = extension.contentType)
                Row {
                    if (extension.isUpdateAvailable) {
                        Text(
                            text = " ",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    if (extension.isInstalled) {
                        Text(
                            text = " ",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (extension.isUpdateAvailable)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = "Language: ${getLanguageDisplayName(extension.language)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val versionText = if (extension.isUpdateAvailable) {
                    "v${extension.version} < v${extension.remoteVersion}"
                } else {
                    "v${extension.version}"
                }
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (extension.isUpdateAvailable)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                if (extension.iconUrl.isNotBlank()) {
                    ImageView(
                        imageModel = "${extension.iconUrl}",
                        modifier = Modifier.fillMaxSize(),
                        error = R.drawable.default_icon,
                        placeholder = R.drawable.default_icon
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            when {
                extension.isInstalling -> {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.height(40.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(my.noveldokusha.strings.R.string.installing))
                    }
                }
                extension.isUpdateAvailable -> {
                    FilledTonalButton(
                        onClick = {
                            viewModel.onEvent(ExtensionsScreenEvent.OnExtensionInstall(extension.id))
                        },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(my.noveldokusha.strings.R.string.update))
                    }
                }
                extension.isInstalled -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnEditLuaClick(extension.id)) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(my.noveldokusha.strings.R.string.edit_lua),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (!extension.isLocal) {
                            FilledTonalIconButton(
                                onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnResetLuaClick(extension.id)) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(my.noveldokusha.strings.R.string.reset),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { viewModel.onEvent(ExtensionsScreenEvent.OnExtensionUninstallById(extension.id)) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(my.noveldokusha.strings.R.string.uninstall),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                else -> {
                    FilledTonalButton(
                        onClick = {
                            viewModel.onEvent(ExtensionsScreenEvent.OnExtensionInstall(extension.id))
                        },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(my.noveldokusha.strings.R.string.install))
                    }
                }
            }
        }
    )
}