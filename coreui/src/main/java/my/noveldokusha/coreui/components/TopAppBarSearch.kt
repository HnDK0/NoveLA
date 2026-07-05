package my.noveldokusha.coreui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import my.noveldokusha.coreui.R
import my.noveldokusha.coreui.theme.clickableNoIndicator

enum class ToolbarMode { MAIN, SEARCH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSearch(
    focusRequester: FocusRequester,
    searchTextInput: String,
    onSearchTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onTextDone: (String) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    placeholderText: String = stringResource(R.string.search_here),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    inputEnabled: Boolean = true,
    labelText: String? = null,
    modifier: Modifier = Modifier,
    showMenuButton: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    dropdownContent: @Composable (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Many hacks going on here to make it scrollBehavior compatible
    TopAppBar(
        modifier = modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null
        ) {
            focusRequester.requestFocus()
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
        ),
        navigationIcon = {
            IconButton(onClick = {
                keyboardController?.hide()
                onClose()
            }, modifier = Modifier.padding(start = 2.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        title = {
            TextField(
                value = searchTextInput,
                onValueChange = onSearchTextChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                maxLines = 1,
                enabled = inputEnabled,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = RoundedCornerShape(16.dp),
                label = labelText?.let {
                    { Text(text = it) }
                },
                keyboardActions = KeyboardActions(onDone = {
                    if (searchTextInput.isNotBlank()) {
                        onTextDone(searchTextInput)
                    }
                }),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            keyboardController?.hide()
                        }
                    }
                    .fillMaxWidth(),
                placeholder = {
                    Text(
                        text = placeholderText,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                trailingIcon = {
                    if (searchTextInput.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
                        ) {
                            IconButton(onClick = {
                                onSearchTextChange("")
                                focusRequester.requestFocus()
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null
                                )
                            }
                        }
                    } else if (showMenuButton) {
                        Box {
                            IconButton(onClick = {
                                if (dropdownContent != null) {
                                    dropdownExpanded = !dropdownExpanded
                                } else {
                                    onMenuClick?.invoke()
                                }
                            }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = null
                                )
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                dropdownContent?.invoke()
                            }
                        }
                    }
                }
            )
        }
    )
}
