package com.foxdevelops.foxscreenshots.features.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings

/** Top capture bar of the hub: Instant, Timer, Full screen, Active window. Emits the chosen [CaptureMode]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureToolbar(onCapture: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaptureButton(Icons.Outlined.CropFree, strings.captureInstant) { onCapture(CaptureMode.Instant) }
        CaptureButton(Icons.Outlined.Timer, strings.captureTimer) { onCapture(CaptureMode.Timer) }
        CaptureButton(Icons.Outlined.Fullscreen, strings.captureFullScreen) { onCapture(CaptureMode.FullScreen) }
        CaptureButton(Icons.Outlined.WebAsset, strings.captureActiveWindow) { onCapture(CaptureMode.ActiveWindow) }
    }
}

@Composable
private fun CaptureButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
