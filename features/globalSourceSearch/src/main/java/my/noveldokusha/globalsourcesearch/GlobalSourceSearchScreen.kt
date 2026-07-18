package my.noveldokusha.globalsourcesearch

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import my.noveldokusha.coreui.components.AnimatedTransition
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.components.TopAppBarSearch
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.feature.local_database.BookMetadata


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun GlobalSourceSearchScreen(
    searchInput: String,
    listSources: List<SourceResults>,
    onSearchInputChange: (String) -> Unit,
    onSearchInputSubmit: (String) -> Unit,
    onBookClick: (book: BookMetadata) -> Unit,
    onPressBack: () -> Unit
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
                                colors = TopAppBarDefaults.mediumTopAppBarColors(
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
                val progress by remember {
                    derivedStateOf {
                        val totalCount = listSources.size.coerceAtLeast(1)
                        val finishedCount = listSources.count { it.fetchIterator.hasFinished }
                        finishedCount.toFloat() / totalCount.toFloat()
                    }
                }
                if (progress < 1f) {
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
                onBookClick = onBookClick
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
