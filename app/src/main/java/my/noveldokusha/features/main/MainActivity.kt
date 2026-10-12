package my.noveldokusha.features.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import my.noveldokusha.coreui.BaseActivity
import my.noveldokusha.tooling.application_workers.setup.PeriodicWorkersInitializer
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.R
import my.noveldokusha.catalogexplorer.CatalogExplorerScreen
import my.noveldokusha.libraryexplorer.LibraryScreen
import my.noveldokusha.settings.SettingsScreen
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.tooling.epub_importer.BookImportService
import my.noveldokusha.historyexplorer.HistoryScreen
import my.noveldokusha.features.discover.DiscoverScreen
import my.noveldokusha.features.discover.DiscoverFeedSheet
import my.noveldokusha.features.discover.DiscoverThemeSheet
import my.noveldokusha.features.discover.DiscoverViewModel
import my.noveldokusha.features.searchsettings.SearchSourcePrioritySheet
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

private data class Page(
    @DrawableRes val iconRes: Int,
    @StringRes val stringRes: Int,
)

// Redesign nav (prototype .bottom-nav): Discover (NEW home) / Library (old home) / Finder / Settings.
// History keeps its functionality as a full-screen page opened from the Library app bar.
private val pages = listOf(
    Page(iconRes = my.noveldokusha.coreui.R.drawable.ic_baseline_home_24, stringRes = R.string.title_discover),
    Page(iconRes = my.noveldokusha.coreui.R.drawable.ic_baseline_menu_book_24, stringRes = R.string.title_library),
    Page(iconRes = my.noveldokusha.coreui.R.drawable.ic_baseline_search_24, stringRes = R.string.title_finder),
    Page(iconRes = R.drawable.ic_twotone_settings_24, stringRes = R.string.title_settings),
)

private const val PAGE_DISCOVER = 0
private const val PAGE_LIBRARY = 1
private const val PAGE_FINDER = 2
private const val PAGE_SETTINGS = 3
private const val PAGE_HISTORY = 4 // overlay page, not in bottom nav


@AndroidEntryPoint
open class MainActivity : BaseActivity() {

    companion object {
        private var isColdStart = true
    }

    @Inject
    lateinit var periodicWorkersInitializer: PeriodicWorkersInitializer

    @Inject
    lateinit var navigationRoutes: NavigationRoutes

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (isColdStart) {
            installSplashScreen()
            isColdStart = false
        }
        super.onCreate(savedInstanceState)

        // Defer WorkManager init to first onResume (cold start optimization)
        var initCalled = false
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !initCalled) {
                initCalled = true
                periodicWorkersInitializer.init()
            }
        })

        requestPushNotificationPermission()

        setContent {
            var activePageIndex by rememberSaveable { mutableIntStateOf(0) }
            var showThemeSheet by rememberSaveable { mutableStateOf(false) }
            var showFeedSheet by rememberSaveable { mutableStateOf(false) }
            var showSearchPrioritySheet by rememberSaveable { mutableStateOf(false) }

            BackHandler(enabled = activePageIndex != 0) {
                activePageIndex = if (activePageIndex == PAGE_HISTORY) PAGE_LIBRARY else 0
            }

            Theme(themeProvider = themeProvider) {
                Box(Modifier.fillMaxSize()) {
                    // All screens live in composition always.
                    // Switching is instant — only alpha changes via graphicsLayer.
                    @Composable
                    fun PageLayer(page: Int, label: String, content: @Composable () -> Unit) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (activePageIndex == page) 1f else 0f }
                                .zIndex(if (activePageIndex == page) 1f else 0f)
                                .then(
                                    if (activePageIndex != page) Modifier.pointerInput(label) {
                                        awaitPointerEventScope { while (true) { awaitPointerEvent() } }
                                    } else Modifier
                                )
                        ) {
                            content()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        PageLayer(PAGE_DISCOVER, "discover") {
                            DiscoverScreen(
                                onSwitchTab = { activePageIndex = it },
                                onOpenSearch = {
                                    navigationRoutes.globalSearch(
                                        context = this@MainActivity,
                                        text = "",
                                        contentType = "",
                                    ).let(this@MainActivity::startActivity)
                                },
                                onOpenTheme = { showThemeSheet = true },
                            )
                        }

                        PageLayer(PAGE_LIBRARY, "library") {
                            LibraryScreen(
                                onOpenHistory = { activePageIndex = PAGE_HISTORY },
                            )
                        }

                        PageLayer(PAGE_FINDER, "finder") {
                            CatalogExplorerScreen()
                        }

                        PageLayer(PAGE_HISTORY, "history") {
                            HistoryScreen(
                                onClose = { activePageIndex = PAGE_LIBRARY },
                            )
                        }

                        PageLayer(PAGE_SETTINGS, "settings") {
                            SettingsScreen(
                                navigationRoutes = navigationRoutes,
                                onRestartApp = {
                                    recreate()
                                },
                                onOpenHomepageFeeds = { showFeedSheet = true },
                                onOpenSearchPriority = { showSearchPrioritySheet = true },
                            )
                        }
                    }

                    // Redesign bottom navigation — docked, hairline top border, icon-in-pill + label
                    // (prototype .bottom-nav: surface bg, 4 columns, active = accent-soft pill + ink label)
                    // Hidden on the History overlay page (like detail/extensions in the prototype).
                    if (activePageIndex != PAGE_HISTORY) {
                        val navHairline = MaterialTheme.colorScheme.outlineVariant
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .drawBehind {
                                    drawLine(
                                        color = navHairline,
                                        start = Offset(0f, 0.5f),
                                        end = Offset(size.width, 0.5f),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                },
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    // Keep nav items clear of landscape display cutouts
                                    // (3-button vs gesture nav is covered by navigationBarsPadding above).
                                    .windowInsetsPadding(
                                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                                    )
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                pages.forEachIndexed { pageIndex, page ->
                                    val isSelected = activePageIndex == pageIndex
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .selectable(
                                                selected = isSelected,
                                                onClick = { activePageIndex = pageIndex },
                                                role = Role.Tab,
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .width(38.dp)
                                                .height(26.dp),
                                            shape = RoundedCornerShape(99.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        ) {
                                            Icon(
                                                painter = painterResource(id = page.iconRes),
                                                contentDescription = stringResource(id = page.stringRes),
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                tint = if (isSelected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = stringResource(id = page.stringRes),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Redesign home appearance sheet (theme + dark mode, live)
                    if (showThemeSheet) {
                        val discoverModel: DiscoverViewModel = viewModel()
                        DiscoverThemeSheet(
                            currentTheme = discoverModel.theme.collectAsState().value,
                            currentDarkMode = discoverModel.darkMode.collectAsState().value,
                            onDismiss = { showThemeSheet = false },
                            onSetTheme = discoverModel::setTheme,
                            onSetDarkMode = discoverModel::setDarkMode,
                        )
                    }

                    // Homepage feed settings (per-category feeds + contributing sources),
                    // openable from Settings as well as from the Home page itself.
                    if (showFeedSheet) {
                        val discoverModel: DiscoverViewModel = viewModel()
                        DiscoverFeedSheet(
                            state = discoverModel.uiState.collectAsState().value,
                            onDismiss = { showFeedSheet = false },
                            onSetFeedSource = discoverModel::setFeedSource,
                            onSetVisibleFeed = discoverModel::setVisibleFeed,
                        )
                    }

                    // Global-search source priority (drag-to-reorder).
                    if (showSearchPrioritySheet) {
                        SearchSourcePrioritySheet(onDismiss = { showSearchPrioritySheet = false })
                    }
                }
            }
        }

        handleIntent(intent)
    }

    private fun requestPushNotificationPermission() {
        // check if sdk level is more than 33
        if (VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
            return
        }

        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (result != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return
        val type = intent.type

        when (action) {
            Intent.ACTION_SEND -> {
                if (type == "application/epub+zip"
                    || type == "application/octet-stream"
                    || type == "application/fb2"
                    || type == "application/x-fictionbook+xml"
                ) {
                    handleSharedEpub(intent)
                }
            }

            Intent.ACTION_VIEW -> {
                handleViewedEpub(intent)
            }
        }
    }

    private fun handleViewedEpub(intent: Intent) {
        val epubUri: Uri? = intent.data
        if (epubUri != null) {
            BookImportService.start(ctx = this, uri = epubUri)
        }
    }

    private fun handleSharedEpub(intent: Intent) {
        val epubUri: Uri? = IntentCompat.getParcelableExtra(
            intent, Intent.EXTRA_STREAM, Uri::class.java
        )
        if (epubUri != null) {
            BookImportService.start(ctx = this, uri = epubUri)
        }
    }
}
