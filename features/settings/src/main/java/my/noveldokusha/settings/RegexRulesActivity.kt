package my.noveldokusha.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.utils.Extra_StringNullable
import my.noveldokusha.coreui.BaseActivity
import my.noveldokusha.coreui.theme.Theme

/**
 * Редактор регэксп-правил.
 * Без [IntentData.bookUrl] — глобальные правила;
 * с bookUrl — персональные правила конкретной новеллы.
 */
@AndroidEntryPoint
class RegexRulesActivity : BaseActivity() {

    class IntentData : Intent {
        var bookUrl by Extra_StringNullable()

        constructor(context: Context, bookUrl: String? = null) : super(
            context,
            RegexRulesActivity::class.java
        ) {
            this.bookUrl = bookUrl
        }
    }

    private val viewModel: RegexCleanupSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme(themeProvider = themeProvider) {
                RegexCleanupSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
