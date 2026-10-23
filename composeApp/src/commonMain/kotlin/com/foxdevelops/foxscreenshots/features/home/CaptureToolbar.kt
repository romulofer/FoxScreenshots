package com.foxdevelops.foxscreenshots.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.ui.FoxTooltip

/**
 * Top capture bar of the hub: Instant, Timer, Full screen, Active window, Repeat last. Emits the chosen [CaptureMode].
 * Laid out like the mobile app's home cards: surface fill, rounded corners, and the main action outlined in the brand colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureToolbar(
    onCapture: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
    /** Global hotkey of each mode, by label (e.g. `PrintScreen`); a mode without one gets no tooltip. */
    shortcuts: Map<CaptureMode, String> = emptyMap(),
    /** Wayland cannot name the focused window: the mode asks the system's picker, and says so. */
    windowPicksViaSystem: Boolean = false,
) {
    val strings = LocalStrings.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CaptureCard(Icons.Outlined.CropFree, strings.captureInstant, shortcuts[CaptureMode.Instant], primary = true) { onCapture(CaptureMode.Instant) }
        CaptureCard(Icons.Outlined.Timer, strings.captureTimer, shortcuts[CaptureMode.Timer]) { onCapture(CaptureMode.Timer) }
        CaptureCard(Icons.Outlined.Fullscreen, strings.captureFullScreen, shortcuts[CaptureMode.FullScreen]) { onCapture(CaptureMode.FullScreen) }
        CaptureCard(
            Icons.Outlined.WebAsset,
            if (windowPicksViaSystem) strings.captureActiveWindowPick else strings.captureActiveWindow,
            shortcuts[CaptureMode.ActiveWindow],
        ) { onCapture(CaptureMode.ActiveWindow) }
        CaptureCard(Icons.Outlined.Replay, strings.captureRepeat, shortcuts[CaptureMode.Repeat]) { onCapture(CaptureMode.Repeat) }
    }
}

@Composable
private fun CaptureCard(icon: ImageVector, label: String, shortcut: String?, primary: Boolean = false, onClick: () -> Unit) {
    val accent = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    // The label is already on screen: a tooltip only earns its place when it adds the hotkey.
    if (shortcut == null) {
        CaptureCardBody(icon, label, accent, primary, onClick)
    } else {
        FoxTooltip("$label ($shortcut)") { CaptureCardBody(icon, label, accent, primary, onClick) }
    }
}

@Composable
private fun CaptureCardBody(icon: ImageVector, label: String, accent: Color, primary: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (primary) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent)
            Spacer(Modifier.width(10.dp))
            Text(label, color = accent, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}
