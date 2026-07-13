package my.noveldokusha.webview

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
private fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label)
        Text(text = label, fontSize = 9.sp, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : View> WebViewScreen(
    toolbarTitle: String,
    isReady: Boolean,
    onDoneClicked: () -> Unit,
    onNavigateToUrl: (String) -> Unit,
    webViewFactory: (Context) -> T,
    onBackClicked: () -> Unit,
    onReloadClicked: () -> Unit,
    onClearCookiesClicked: () -> Unit,
    onCopyUrlClicked: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var editingUrl by remember(toolbarTitle) { mutableStateOf(toolbarTitle) }
    var isFocused by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Строка 1: кнопка "назад" + поле URL (на всю ширину)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        TextButton(onClick = onBackClicked) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Close", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedTextField(
                            value = editingUrl,
                            onValueChange = { editingUrl = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .onFocusChanged { isFocused = it.isFocused },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val url = editingUrl.trim().let {
                                        if (!it.startsWith("http")) "https://$it" else it
                                    }
                                    onNavigateToUrl(url)
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    }

                    // Строка 2: кнопки действий с подписями
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        if (isReady) {
                            Button(
                                onClick = onDoneClicked,
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 4.dp)
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "DONE",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        LabeledIconButton(
                            icon = Icons.Default.ContentPaste,
                            label = "Paste",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val pastedUrl = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: return@LabeledIconButton
                                val url = if (!pastedUrl.startsWith("http")) "https://$pastedUrl" else pastedUrl
                                editingUrl = url
                                onNavigateToUrl(url)
                            }
                        )

                        LabeledIconButton(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy",
                            onClick = onCopyUrlClicked
                        )

                        LabeledIconButton(
                            icon = Icons.Default.Refresh,
                            label = "Reload",
                            onClick = onReloadClicked
                        )

                        LabeledIconButton(
                            icon = Icons.Default.Delete,
                            label = "Clear",
                            onClick = onClearCookiesClicked
                        )
                    }
                }
            }
        },
        content = { padding ->
            AndroidView(
                modifier = Modifier.padding(padding),
                factory = webViewFactory
            )
        }
    )
}