package my.noveldokusha.tooling.local_source

import timber.log.Timber

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.noveldokusha.core.AppCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSourcesDirectories @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appCoroutineScope: AppCoroutineScope,
) {
    val list: List<Uri>
        get() = appContext.contentResolver.persistedUriPermissions
            .map { it.uri }

    private val _listState = MutableStateFlow(list)
    val listState = _listState.asStateFlow()

    fun add(uri: Uri) {
        appContext.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        updateState()
    }

    fun remove(uri: Uri) {
        // Get actual permissions for this URI and release all of them
        val permission = appContext.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri }
        val flags = if (permission != null) {
            var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (permission.isWritePermission) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            flags
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            appContext.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Timber.e(e, "Failed to release URI permission")
            try {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to release URI permission even with READ flag")
            }
        }
        updateState()
    }

    private fun updateState() {
        appCoroutineScope.launch(Dispatchers.Default) {
            _listState.update { list }
        }
    }
}
