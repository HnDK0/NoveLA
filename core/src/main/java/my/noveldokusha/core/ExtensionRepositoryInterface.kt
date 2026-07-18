package my.noveldokusha.core

import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс для управления расширениями
 * Вынесен в core чтобы избежать циклических зависимостей
 */
interface ExtensionRepositoryInterface {
    suspend fun getInstalledExtensions(): List<Extension>
    suspend fun getEnabledExtensions(): List<Extension>
    fun getInstalledExtensionsFlow(): Flow<List<Extension>>
    suspend fun isExtensionInstalled(extensionId: String): Boolean
    suspend fun getExtensionSettings(extensionId: String): String?
}
