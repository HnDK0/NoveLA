package my.noveldokusha.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.models.RegexRule
import javax.inject.Inject

data class RegexCleanupUiState(
    val searchQuery: String = "",
    val rules: List<RegexRule> = emptyList(),
    val isBottomSheetOpen: Boolean = false,
    val editingRule: RegexRule? = null,
    val editingIndex: Int? = null,
    val validationError: String? = null,
    val previewText: String = "",
    val deleteConfirmationPattern: String? = null
)

@HiltViewModel
class RegexCleanupSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    var uiState = mutableStateOf(RegexCleanupUiState())
        private set

    private val _searchQuery = mutableStateOf("")

    val filteredRules: List<RegexRule>
        get() {
            val query = _searchQuery.value.trim().lowercase()
            if (query.isEmpty()) return uiState.value.rules
            return uiState.value.rules.filter {
                it.pattern.lowercase().contains(query) ||
                it.description.lowercase().contains(query)
            }
        }

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            val rules = appPreferences.USER_REGEX_CLEANUP_RULES.value
            uiState.value = uiState.value.copy(rules = rules)
        }
    }

    // ── Callbacks ──────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onAddRule() {
        uiState.value = uiState.value.copy(
            isBottomSheetOpen = true,
            editingRule = RegexRule("", "", isEnabled = true),
            editingIndex = null,
            validationError = null
        )
    }

    fun onEditRule(index: Int) {
        val rule = uiState.value.rules.getOrNull(index) ?: return
        uiState.value = uiState.value.copy(
            isBottomSheetOpen = true,
            editingRule = rule,
            editingIndex = index,
            validationError = null
        )
    }

    fun onDismissBottomSheet() {
        uiState.value = uiState.value.copy(
            isBottomSheetOpen = false,
            editingRule = null,
            editingIndex = null,
            validationError = null
        )
    }

    fun onSaveRule(pattern: String, replacement: String, enabled: Boolean, description: String) {
        if (!validateRegex(pattern)) {
            uiState.value = uiState.value.copy(
                validationError = pattern
            )
            return
        }

        uiState.value = uiState.value.copy(validationError = null)

        viewModelScope.launch {
            val currentRules = appPreferences.USER_REGEX_CLEANUP_RULES.value.toMutableList()
            val newRule = RegexRule(
                pattern = pattern.trim(),
                replacement = replacement.trim(),
                isEnabled = enabled,
                description = description.trim()
            )

            val editingIndex = uiState.value.editingIndex
            if (editingIndex != null && editingIndex in currentRules.indices) {
                currentRules[editingIndex] = newRule
            } else {
                currentRules.add(newRule)
            }

            appPreferences.USER_REGEX_CLEANUP_RULES.value = currentRules
            loadRules()
            onDismissBottomSheet()
        }
    }

    fun onDeleteRule(pattern: String) {
        uiState.value = uiState.value.copy(deleteConfirmationPattern = pattern)
    }

    fun onConfirmDelete() {
        val pattern = uiState.value.deleteConfirmationPattern ?: return
        viewModelScope.launch {
            val currentRules = appPreferences.USER_REGEX_CLEANUP_RULES.value.toMutableList()
            currentRules.removeAll { it.pattern == pattern }
            appPreferences.USER_REGEX_CLEANUP_RULES.value = currentRules
            loadRules()
            uiState.value = uiState.value.copy(deleteConfirmationPattern = null)
        }
    }

    fun onDismissDelete() {
        uiState.value = uiState.value.copy(deleteConfirmationPattern = null)
    }

    fun onToggleRule(index: Int) {
        viewModelScope.launch {
            val currentRules = appPreferences.USER_REGEX_CLEANUP_RULES.value.toMutableList()
            val rule = currentRules.getOrNull(index) ?: return@launch
            currentRules[index] = rule.copy(isEnabled = !rule.isEnabled)
            appPreferences.USER_REGEX_CLEANUP_RULES.value = currentRules
            loadRules()
        }
    }

    fun onMoveRule(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentRules = appPreferences.USER_REGEX_CLEANUP_RULES.value.toMutableList()
            if (fromIndex in currentRules.indices && toIndex in currentRules.indices) {
                val item = currentRules.removeAt(fromIndex)
                currentRules.add(toIndex, item)
                appPreferences.USER_REGEX_CLEANUP_RULES.value = currentRules
            }
            loadRules()
        }
    }

    fun updatePreview(text: String) {
        uiState.value = uiState.value.copy(previewText = text)
    }

    fun getPreviewResult(rule: RegexRule, text: String): String {
        if (!rule.isEnabled) return text
        return try {
            text.replace(Regex(rule.pattern), rule.replacement)
        } catch (e: Exception) {
            text
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────

    private fun validateRegex(pattern: String): Boolean {
        return try {
            Regex(pattern)
            true
        } catch (e: Exception) {
            false
        }
    }
}