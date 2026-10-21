package com.foxdevelops.foxscreenshots.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings

/** Help ▸ About: name, version, the privacy promise and the license. */
@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.aboutTitle) },
        text = {
            Column {
                if (version.isNotEmpty()) {
                    Text(strings.settingsVersion(version), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                }
                Text(strings.aboutTagline)
                Spacer(Modifier.height(8.dp))
                Text(strings.aboutPrivacy, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(strings.aboutLicense, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.actionClose) } },
    )
}
