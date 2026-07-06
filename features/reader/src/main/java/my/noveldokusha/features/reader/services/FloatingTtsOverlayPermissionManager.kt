package my.noveldokusha.features.reader.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object FloatingTtsOverlayPermissionManager {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
}
