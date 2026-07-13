package my.noveldokusha.libraryexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.R
import my.noveldokusha.scraper.Scraper

private sealed class ValidationResult {
    data class Success(val urls: List<String>) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

private fun String.isValidUrl(): Boolean {
    return try {
        val uri = java.net.URI(this)
        uri.scheme in listOf("http", "https") && uri.host != null
    } catch (e: Exception) {
        false
    }
}

@Composable
fun AddByUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (urls: List<String>) -> Unit,
    scraper: Scraper
) {
    var urlsText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.add_by_url),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.enter_novel_urls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = urlsText,
                    onValueChange = {
                        urlsText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.urls)) },
                    placeholder = { Text("https://example.com/novel/123\nhttps://another.com/book/456") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val validationResult = validateUrls(urlsText, scraper)
                            when (validationResult) {
                                is ValidationResult.Success -> {
                                    onConfirm(validationResult.urls)
                                    onDismiss()
                                }
                                is ValidationResult.Error -> {
                                    errorMessage = validationResult.message
                                }
                            }
                        }
                    ),
                    minLines = 3,
                    maxLines = 10,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val validationResult = validateUrls(urlsText, scraper)
                        when (validationResult) {
                            is ValidationResult.Success -> {
                                onConfirm(validationResult.urls)
                                onDismiss()
                            }
                            is ValidationResult.Error -> {
                                errorMessage = validationResult.message
                            }
                        }
                    },
                    enabled = urlsText.isNotBlank(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.add),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        dismissButton = {}
    )
}

private fun validateUrls(urlsText: String, scraper: Scraper): ValidationResult {
    val lines = urlsText.lines().map { it.trim() }.filter { it.isNotBlank() }

    if (lines.isEmpty()) {
        return ValidationResult.Error("At least one URL is required")
    }

    val validUrls = mutableListOf<String>()
    val errors = mutableListOf<String>()

    for ((index, url) in lines.withIndex()) {
        if (!url.isValidUrl()) {
            errors.add("Line ${index + 1}: Invalid URL format")
            continue
        }

        if (!scraper.isUrlSupported(url)) {
            errors.add("Line ${index + 1}: Unsupported source")
            continue
        }

        validUrls.add(url)
    }

    if (errors.isNotEmpty()) {
        return ValidationResult.Error(errors.joinToString("\n"))
    }

    return ValidationResult.Success(validUrls)
}
