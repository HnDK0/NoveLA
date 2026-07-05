package my.noveldokusha.coreui

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.ThemeProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.Toasty
import javax.inject.Inject

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {

    val appPreferences: AppPreferences by lazy { AppPreferences(applicationContext) }

    @Inject
    lateinit var themeProvider: ThemeProvider

    @Inject
    lateinit var toasty: Toasty

    private val defaultBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Вызываем стандартное поведение onBackPressed
            if (hasWindowFocus()) {
                finish()
            }
        }
    }

    init {
        // Добавляем стандартный callback при создании активности
        onBackPressedDispatcher.addCallback(this, defaultBackPressedCallback)
    }

    private fun getAppTheme(): Int {
        val darkMode = runCatching {
            enumValueOf<DarkMode>(appPreferences.THEME_DARK_MODE.value)
        }.getOrDefault(DarkMode.SYSTEM)

        val isSystemDark = isSystemInDarkTheme()

        return when (darkMode) {
            DarkMode.LIGHT -> R.style.AppTheme_Light
            DarkMode.DARK -> R.style.AppTheme_BaseDark_Dark
            DarkMode.BLACK -> R.style.AppTheme_BaseDark_Black
            DarkMode.SYSTEM -> if (isSystemDark) R.style.AppTheme_BaseDark_Dark else R.style.AppTheme_Light
        }
    }

    private fun isSystemInDarkTheme(): Boolean {
        val uiMode = resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    // This will remain until Reader Screen has no View XML usages
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getAppTheme())
        super.onCreate(savedInstanceState)
    }

    protected fun addOnBackPressedCallback(callback: OnBackPressedCallback) {
        onBackPressedDispatcher.addCallback(this, callback)
    }

    protected fun removeOnBackPressedCallback(callback: OnBackPressedCallback) {
        callback.remove()
    }
}