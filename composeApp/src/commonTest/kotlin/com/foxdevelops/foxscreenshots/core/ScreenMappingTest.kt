package com.foxdevelops.foxscreenshots.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.foxdevelops.foxscreenshots.core.capture.ScreenMapping
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenMappingTest {
    @Test
    fun `a window the size of the screenshot maps 1 to 1`() {
        val mapping = ScreenMapping.fitted(Size(1920f, 1080f), Size(1920f, 1080f))
        assertEquals(Offset(100f, 50f), mapping.toImage(Offset(100f, 50f)))
    }

    @Test
    fun `a smaller window shows the screenshot shrunk and maps the drag back to exact pixels`() {
        val mapping = ScreenMapping.fitted(Size(2000f, 1000f), Size(1000f, 500f))
        assertEquals(2f, mapping.imagePixelsPerCanvasPixel)
        assertEquals(Offset(200f, 100f), mapping.toImage(Offset(100f, 50f)))
    }

    @Test
    fun `letterbox margins shift the origin`() {
        // 2000x1000 into 1000x1000 leaves 250px bars above and below.
        val mapping = ScreenMapping.fitted(Size(2000f, 1000f), Size(1000f, 1000f))
        assertEquals(Offset(0f, 0f), mapping.toImage(Offset(0f, 250f)))
    }

    @Test
    fun `toRegion clips to the screenshot`() {
        val mapping = ScreenMapping.fitted(Size(100f, 100f), Size(100f, 100f))
        val region = mapping.toRegion(Offset(-20f, 50f), Offset(60f, 200f), 100, 100)
        assertEquals(CaptureRegion(0, 50, 60, 50), region)
    }

    @Test
    fun `empty sizes fall back to identity`() {
        assertEquals(ScreenMapping.Identity, ScreenMapping.fitted(Size.Zero, Size(10f, 10f)))
    }
}
