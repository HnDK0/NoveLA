package my.noveldokusha.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaEditorDialog(
    title: String,
    code: String,
    error: String?,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val compactBtn = Modifier.height(32.dp)
                val compactPadding = PaddingValues(horizontal = 10.dp)
                FilledTonalButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.copy), fontSize = 11.sp)
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { clipboardManager.getText()?.text?.let(onCodeChange) },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.paste), fontSize = 11.sp)
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { onCodeChange("") },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.clear), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.cancel), fontSize = 11.sp)
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onSave,
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.save), fontSize = 11.sp)
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                maxLines = Int.MAX_VALUE
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.top), fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.cancel), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                FilledTonalButton(
                    onClick = onSave,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.save), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
