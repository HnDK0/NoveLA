@file:Suppress("PropertyName")

package my.noveldokusha.core.appPreferences

import kotlinx.serialization.Serializable

/**
 * Упрощённая версия ExtensionInfo для хранения в кеше (SharedPreferences).
 * Не содержит вычисляемых полей (isInstalled, isEnabled, etc.)
 */
@Serializable
data class ExtensionInfoCached(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val remoteVersion: String = "",
    val codeUrl: String,
    val iconUrl: String,
    val language: String
)