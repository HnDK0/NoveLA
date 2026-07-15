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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import my.noveldokusha.coreui.theme.ThemeProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.R
import my.noveldokusha.catalogexplorer.CatalogExplorerScreen
import my.noveldokusha.libraryexplorer.LibraryScreen
import my.noveldokusha.settings.SettingsScreen
import my.noveldokusha.tooling.epub_importer.EpubImportService

private data class Page(
    @DrawableRes val iconRes: Int,
    @StringRes val stringRes: Int,
)

private val pages = listOf(
    Page(iconRes = R.drawable.ic_baseline_home_24, stringRes = R.string.title_library),
    Page(iconRes = R.drawable.ic_baseline_menu_book_24, stringRes = R.string.title_finder),
    Page(iconRes = R.drawable.ic_twotone_settings_24, stringRes = R.string.title_settings),
)


@AndroidEntryPoint
open class MainActivity : BaseActivity() {

    companion object {
        private var isColdStart = true
    }

    @Inject
    lateinit var periodicWorkersInitializer: PeriodicWorkersInitializer

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

            BackHandler(enabled = activePageIndex != 0) {
                activePageIndex = 0
            }

            Theme(themeProvider = themeProvider) {
                Box(Modifier.fillMaxSize()) {
                    // ponytail: previously all three top-level screens (Library / Catalog /
                    // Settings) were kept alive in composition simultaneously with
                    // graphicsLayer.alpha = 0f for the inactive ones. That caused every
                    // inactive screen to recompose whenever shared state (theme, app prefs,
                    // download manager flow, etc.) changed — wasting CPU and allocations
                    // even while not visible. Now only the active screen is composed; the
                    // other two are removed from composition entirely. HiltViewModel-backed
                    // state (library list, settings, etc.) is preserved across tab switches
                    // because the ViewModels survive composition removal.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        when (activePageIndex) {
                            0 -> LibraryScreen()
                            1 -> CatalogExplorerScreen()
                            2 -> SettingsScreen(onRestartApp = {
                                recreate()
                            })
                        }
                    }

                    // Floating slim icon-only bottom bar
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 64.dp, vertical = 16.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pages.forEachIndexed { pageIndex, page ->
                                val isSelected = activePageIndex == pageIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .selectable(
                                            selected = isSelected,
                                            onClick = { activePageIndex = pageIndex },
                                            role = Role.Tab,
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null // Clean click
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Animated background for selection
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ) {
                                        Icon(
                                            painter = painterResource(id = page.iconRes),
                                            contentDescription = stringResource(id = page.stringRes),
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .size(24.dp),
                                            tint = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
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
                if (type == "application/epub+zip") {
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
            EpubImportService.start(ctx = this, uri = epubUri)
        }
    }

    private fun handleSharedEpub(intent: Intent) {
        val epubUri: Uri? = IntentCompat.getParcelableExtra(
            intent, Intent.EXTRA_STREAM, Uri::class.java
        )
        if (epubUri != null) {
            EpubImportService.start(ctx = this, uri = epubUri)
        }
    }
}
