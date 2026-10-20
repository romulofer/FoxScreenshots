package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.core.storage.ClipboardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Puts the capture on the system clipboard as an image, through AWT — no native dependency. */
class AwtClipboardService : ClipboardService {

    override suspend fun copyPng(pngBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (GraphicsEnvironment.isHeadless()) return@withContext false
        val image = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: return@withContext false
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
        true
    }

    private class ImageSelection(private val image: Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}
