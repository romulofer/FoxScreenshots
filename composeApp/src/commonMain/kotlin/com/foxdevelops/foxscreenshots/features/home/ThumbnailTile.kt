package com.foxdevelops.foxscreenshots.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.features.editor.ImageProcessor
import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Width a gallery thumbnail is decoded at; a full 4K frame would blow GPU memory. */
const val THUMBNAIL_WIDTH = 480

/** A single session capture in the gallery grid. Clicking the image previews it; the footer has Edit / Copy / Save / Delete. */
@Composable
fun ThumbnailTile(
    capture: CaptureResult,
    processor: ImageProcessor,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
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
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium).clickable(onClick = onPreview),
            ) {
                thumbnail?.let {
                    Image(
                        bitmap = it,
                        contentDescription = strings.actionPreview,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                TileAction(strings.actionEdit, Icons.Outlined.Edit, onEdit)
                TileAction(strings.actionCopy, Icons.Outlined.ContentCopy, onCopy)
                TileAction(strings.actionSave, Icons.Outlined.Save, onSave)
                TileAction(strings.actionDelete, Icons.Outlined.Delete, onDelete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** One action in the tile footer, sized to an equal share of the row: four fixed icon buttons overflow once tiles get narrow. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.TileAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
            )
        }
    }
}
