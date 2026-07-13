package my.noveldokusha.core.models

import kotlinx.serialization.Serializable

@Serializable
data class RegexRule(
    val pattern: String,
    val replacement: String = "",
    val isEnabled: Boolean = true,
    val description: String = ""
)