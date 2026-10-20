package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.platform.image.Png
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwtClipboardServiceTest {
    private val pixels = ByteArray(6 * 4 * 4) { if (it % 4 == 3) 0 else 0x40 }
    private val png = Png.encodeOpaque(pixels, 6, 4)

    @Test
    fun `the clipboard receives the image`() = runTest {
        assumeFalse("needs a display", GraphicsEnvironment.isHeadless())
        assertTrue(AwtClipboardService().copyPng(png))
        val image = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.imageFlavor) as BufferedImage
        assertEquals(6, image.width)
        assertEquals(4, image.height)
    }

    @Test
    fun `bytes that are not an image are not copied`() = runTest {
        assumeFalse("needs a display", GraphicsEnvironment.isHeadless())
        assertFalse(AwtClipboardService().copyPng(ByteArray(16)))
    }
}
