package my.noveldokusha.core

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface Toasty {
    fun show(text: String, shortDuration: Boolean = true)
    fun show(@StringRes id: Int, shortDuration: Boolean = true)
}

@Singleton
class ToastyToast @Inject constructor(
    @ApplicationContext private val context: Context,
    // ponytail: was CoroutineScope(Dispatchers.Main).launch { ... } per call —
    // every toast created a new orphan scope that never got cancelled. Reuse the
    // singleton AppCoroutineScope instead.
    private val appScope: AppCoroutineScope,
) : Toasty {

    override fun show(text: String, shortDuration: Boolean) {
        appScope.launch {
            Toast.makeText(context, text, durationMapper(shortDuration)).show()
        }
    }

    override fun show(@StringRes id: Int, shortDuration: Boolean) {
        appScope.launch {
            Toast.makeText(context, id, durationMapper(shortDuration)).show()
        }
    }

    private fun durationMapper(shortDuration: Boolean) = when (shortDuration) {
        true -> Toast.LENGTH_SHORT
        false -> Toast.LENGTH_LONG
    }
}
