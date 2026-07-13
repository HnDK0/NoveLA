package my.noveldokusha.core

import kotlinx.coroutines.flow.Flow

// Simple extension model for core module
data class Extension(
    val id: String,
    val name: String,
    val version: String,
    val language: String,
    val enabled: Boolean,
    val installed: Boolean,
    val iconUrl: String? = null,
)

interface ExtensionManager {

    suspend fun getAllExtensions(): List<Extension>

    suspend fun getInstalledExtensions(): List<Extension>

    suspend fun getEnabledExtensions(): List<Extension>

    fun getInstalledExtensionsFlow(): Flow<List<Extension>>

    suspend fun installExtension(extension: Extension)

    suspend fun installExtensionFromInfo(id: String, name: String, version: String, language: String, imageUrl: String? = null, codeUrl: String? = null)

    suspend fun uninstallExtension(extensionId: String)

    suspend fun enableExtension(extensionId: String)

    suspend fun disableExtension(extensionId: String)

    suspend fun updateExtensionSettings(extensionId: String, settings: String)

    suspend fun isExtensionInstalled(extensionId: String): Boolean

    suspend fun getExtensionSettings(extensionId: String): String?
}