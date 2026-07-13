package my.noveldokusha.globalsourcesearch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.coreui.BaseActivity
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.core.utils.Extra_String
import my.noveldokusha.navigation.NavigationRoutes
import javax.inject.Inject

@AndroidEntryPoint
class GlobalSourceSearchActivity : BaseActivity() {
    class IntentData : Intent, GlobalSourceSearchStateBundle {
        override var initialInput by Extra_String()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, input: String) : super(
            ctx,
            GlobalSourceSearchActivity::class.java
        ) {
            this.initialInput = input
        }
    }

    @Inject
    internal lateinit var navigationRoutes: NavigationRoutes

    private val viewModel by viewModels<GlobalSourceSearchViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Обработка нажатия кнопки "назад"
                finish()
            }
        }
        addOnBackPressedCallback(backPressedCallback)

        setContent {
            Theme(themeProvider = themeProvider) {
                GlobalSourceSearchScreen(
                    searchInput = viewModel.searchInput.value,
                    listSources = viewModel.sourcesResults,
                    onBookClick = { navigationRoutes.chapters(this, it).let(::startActivity) },
                    onPressBack = { backPressedCallback.handleOnBackPressed() },
                    onSearchInputChange = viewModel.searchInput::value::set,
                    onSearchInputSubmit = viewModel::search,
                )
            }
        }
    }
}