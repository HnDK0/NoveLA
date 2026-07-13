package my.noveldokusha.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes

/**
 * A compact list item component inspired by Momogram/Telegram.
 * Features reduced vertical padding and a slim profile for higher information density.
 */
@Composable
fun SlimListItem(
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            Box(Modifier.padding(end = 12.dp)) {
                leadingContent()
            }
        }

        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                headlineContent()
            }
            if (supportingContent != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    supportingContent()
                }
            }
        }

        if (trailingContent != null) {
            Box(Modifier.padding(start = 12.dp)) {
                trailingContent()
            }
        }
    }
}

/**
 * String-based overload for [SlimListItem] for common use cases.
 */
@Composable
fun SlimListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    SlimListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick
    )
}

@PreviewThemes
@Composable
private fun SlimListItemPreview() {
    InternalTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            SlimListItem(
                title = "Contact Name",
                subtitle = "Last seen recently",
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CN", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                onClick = {}
            )
            SlimListItem(
                title = "Another Contact",
                subtitle = "Online",
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AC", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                },
                onClick = {}
            )
            SlimListItem(
                title = "Settings",
                subtitle = "Account, Privacy and Security",
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                },
                onClick = {}
            )
        }
    }
}
