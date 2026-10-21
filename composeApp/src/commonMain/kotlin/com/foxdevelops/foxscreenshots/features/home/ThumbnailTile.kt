package com.foxdevelops.foxscreenshots.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.formatClock
import com.foxdevelops.foxscreenshots.features.editor.ImageProcessor
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.ui.FoxIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Width a gallery thumbnail is decoded at; a full 4K frame would blow GPU memory. */
const val THUMBNAIL_WIDTH = 480

/**
 * A single session capture in the gallery grid. Clicking the image previews it;
 * the footer has Edit / Copy / Save / Reveal / Delete. Reveal needs a file, so it
 * stays off until the capture has been saved ([savedPath]).
 */
@Composable
fun ThumbnailTile(
    capture: CaptureResult,
    processor: ImageProcessor,
    savedPath: String?,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onReveal: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var thumbnail by remember(capture.id, capture.pngBytes) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(capture.id, capture.pngBytes) {
        thumbnail = withContext(Dispatchers.Default) { processor.thumbnail(capture.pngBytes, THUMBNAIL_WIDTH) }
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.medium)
                    // Letterbox colour: a wide or tall capture is shown whole instead of cropped to 4:3.
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = onPreview),
            ) {
                thumbnail?.let {
                    Image(
                        bitmap = it,
                        contentDescription = strings.actionPreview,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                strings.captureMeta(capture.width, capture.height, formatClock(capture.takenAtMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TileAction(strings.actionEdit, Icons.Outlined.Edit, onEdit)
                TileAction(strings.actionCopy, Icons.Outlined.ContentCopy, onCopy)
                TileAction(strings.actionSave, Icons.Outlined.Save, onSave)
                TileAction(
                    label = if (savedPath != null) strings.actionReveal else strings.revealNeedsSave,
                    description = strings.actionReveal,
                    icon = Icons.Outlined.FolderOpen,
                    onClick = onReveal,
                    enabled = savedPath != null,
                )
                TileAction(strings.actionDelete, Icons.Outlined.Delete, onDelete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** One action in the tile footer, sized to an equal share of the row: fixed icon buttons overflow once tiles get narrow. */
@Composable
private fun RowScope.TileAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    /** Accessibility name when the tooltip says something longer than the action ("Save to reveal…"). */
    description: String = label,
) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        FoxIconButton(
            icon = icon,
            label = label,
            onClick = onClick,
            enabled = enabled,
            tint = tint,
            iconSize = 20.dp,
            buttonSize = 36.dp,
            description = description,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        )
    }
}
