package my.noveldokusha.globalsourcesearch

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.AnimatedTransition
import my.noveldokusha.coreui.components.LibraryBadgeMaps
import my.noveldokusha.coreui.components.LibraryBadgeState
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.components.TopAppBarSearch
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.strings.R as StringsR


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun GlobalSourceSearchScreen(
    searchInput: String,
    listSources: List<SourceResults>,
    onSearchInputChange: (String) -> Unit,
    onSearchInputSubmit: (String) -> Unit,
    onBookClick: (book: BookMetadata) -> Unit,
    onPressBack: () -> Unit,
    getLibraryBadge: (String, String) -> LibraryBadgeState? = { _, _ -> null },
    libraryBadgeData: State<LibraryBadgeMaps> = androidx.compose.runtime.mutableStateOf(LibraryBadgeMaps()),
    selectedScope: String = "",
    onScopeChange: (String) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        snapAnimationSpec = null,
        flingAnimationSpec = null
    )

    var toolbarMode by remember {
        mutableStateOf(if (searchInput.isNotEmpty()) ToolbarMode.SEARCH else ToolbarMode.MAIN)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                AnimatedTransition(targetState = toolbarMode) { target ->
                    when (target) {
                        ToolbarMode.MAIN -> {
                            MediumTopAppBar(
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                                title = {
                                    Text(
                                        text = stringResource(R.string.global_search),
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = onPressBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { toolbarMode = ToolbarMode.SEARCH }) {
                                        Icon(Icons.Default.Search, stringResource(R.string.global_search))
                                    }
                                }
                            )
                        }

                        ToolbarMode.SEARCH -> {
                            TopAppBarSearch(
                                focusRequester = focusRequester,
                                searchTextInput = searchInput,
                                onSearchTextChange = onSearchInputChange,
                                onTextDone = onSearchInputSubmit,
                                onClose = {
                                    if (searchInput.isEmpty()) {
                                        toolbarMode = ToolbarMode.MAIN
                                    } else {
                                        onPressBack()
                                    }
                                },
                                placeholderText = stringResource(R.string.global_search),
                                scrollBehavior = scrollBehavior,
                            )
                        }
                    }
                }

                // Селектор типа контента: показывается всегда (и при входе
                // из глобального поиска, и со страницы книги).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    val scopeOptions = listOf(
                        "" to stringResource(StringsR.string.all_categories),
                        "novel" to stringResource(StringsR.string.content_type_novel),
                        "manga" to stringResource(StringsR.string.content_type_manga),
                        "video" to stringResource(StringsR.string.content_type_video),
                    )
                    scopeOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedScope == value,
                            onClick = { onScopeChange(value) },
                            label = { Text(label) },
                        )
                    }
                }

                val progress by remember {
                    derivedStateOf {
                        val totalCount = listSources.size.coerceAtLeast(1)
                        val finishedCount = listSources.count { it.fetchIterator.hasFinished }
                        finishedCount.toFloat() / totalCount.toFloat()
                    }
                }
                // При пустой выборке (scope без источников) не показываем —
                // иначе индикатор висит вечно над "нет результатов".
                if (progress < 1f && listSources.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        content = { innerPadding ->
            GlobalSourceSearchScreenBody(
                listSources = listSources,
                contentPadding = innerPadding,
                onBookClick = onBookClick,
                getLibraryBadge = getLibraryBadge,
                libraryBadgeData = libraryBadgeData,
            )
        }
    )
}

@Preview
@Composable
private fun PreviewView() {
    InternalTheme {
        GlobalSourceSearchScreen(
            searchInput = "Some text here",
            listSources = listOf(),
            onSearchInputChange = { },
            onSearchInputSubmit = { },
            onBookClick = { },
            onPressBack = { },
        )
    }
}
