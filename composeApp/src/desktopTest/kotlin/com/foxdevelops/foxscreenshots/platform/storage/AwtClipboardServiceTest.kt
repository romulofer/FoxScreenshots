package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.platform.image.Png
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
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

    // Writes to the real system clipboard, so it only runs on a throwaway display (xvfb-run), never on a developer's session.
    private fun requireIsolatedDisplay() =
        assumeTrue("set FOX_ISOLATED_DISPLAY=1 under xvfb-run", System.getenv("FOX_ISOLATED_DISPLAY") == "1")

    @Test
    fun `the clipboard receives the image`() = runTest {
        requireIsolatedDisplay()
        assertTrue(AwtClipboardService().copyPng(png))
        val image = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.imageFlavor) as BufferedImage
        assertEquals(6, image.width)
        assertEquals(4, image.height)
    }

    @Test
    fun `bytes that are not an image are not copied`() = runTest {
        requireIsolatedDisplay()
        assertFalse(AwtClipboardService().copyPng(ByteArray(16)))
    }
}
