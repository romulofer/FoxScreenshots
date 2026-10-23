package com.foxdevelops.foxscreenshots.features.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings

/**
 * Timer-mode countdown badge. Shown in a small always-on-top window while the
 * shot is pending, so the user knows when the frame fires and can still open
 * menus and tooltips underneath. Removed right before the grab, so it never
 * lands in the shot. A click on it (or Esc, handled globally) cancels the capture.
 */
@Composable
fun CountdownBadge(
    remainingSeconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether a global Esc also cancels. Not on Wayland: there the portal would grab Esc from every program. */
    escapeCancels: Boolean = true,
) {
    val strings = LocalStrings.current
    // Fills the whole window with a solid color: a transparent window needs a compositing window manager,
    // and on a plain X11 session it would come out as a black box around the badge.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onCancel)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = remainingSeconds.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            if (escapeCancels) strings.timerCancelHint else strings.timerCancelHintClickOnly,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
