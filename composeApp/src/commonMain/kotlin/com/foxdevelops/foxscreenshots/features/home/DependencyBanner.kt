package com.foxdevelops.foxscreenshots.features.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.desktop.DependencySeverity
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.core.desktop.SystemIssues
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.Strings

/**
 * Tells the user which system components are missing, instead of leaving them
 * with a capture button that silently fails. Blocking issues are styled as
 * errors, degraded ones as a milder notice; renders nothing when the machine
 * has everything.
 */
@Composable
fun DependencyBanner(
    issues: SystemIssues,
    dismissed: Set<String>,
    onDismiss: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by issues.state.collectAsState()
    // Dismissed for good, per kind of issue: a *new* problem still shows, an old one does not nag on every launch.
    val pending = current.filter { it.dependency.name !in dismissed }
    if (pending.isEmpty()) return

    val strings = LocalStrings.current
    val blocking = pending.any { it.severity == DependencySeverity.Blocking }
    val container = if (blocking) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (blocking) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Card(modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(if (blocking) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, contentDescription = null, tint = content)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (blocking) strings.dependencyTitleBlocking else strings.dependencyTitleDegraded,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
                Spacer(Modifier.height(6.dp))
                for (issue in pending) {
                    Text(
                        "• ${dependencyMessage(strings, issue.dependency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = content,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            TextButton(onClick = { onDismiss(pending.map { it.dependency.name }.toSet()) }) { Text(strings.dependencyDismiss, color = content) }
        }
    }
}

/** Localized explanation (plus what to install) for one missing component. */
fun dependencyMessage(strings: Strings, dependency: SystemDependency): String = when (dependency) {
    SystemDependency.X11Library -> strings.dependencyX11Library
    SystemDependency.XDisplay -> strings.dependencyXDisplay
    SystemDependency.WaylandSession -> strings.dependencyWaylandSession
    SystemDependency.Hotkeys -> strings.dependencyHotkeys
    SystemDependency.Tray -> strings.dependencyTray
}
