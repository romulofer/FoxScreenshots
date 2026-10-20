package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnnotationTest {
    private val red = Color.Red

    @Test
    fun `a shape follows the pointer in any direction`() {
        val rect = RectangleAnnotation("a", red, 4f, Offset(50f, 50f), Offset(50f, 50f)).dragTo(Offset(10f, 20f))
        assertEquals(10f, rect.rect.left)
        assertEquals(20f, rect.rect.top)
        assertEquals(50f, rect.rect.right)
    }

    @Test
    fun `a click that never moved is not worth keeping`() {
        assertFalse(RectangleAnnotation("a", red, 4f, Offset(5f, 5f), Offset(6f, 6f)).isMeaningful)
        assertTrue(RectangleAnnotation("a", red, 4f, Offset(5f, 5f), Offset(30f, 6f)).isMeaningful)
    }

    @Test
    fun `a perfectly horizontal arrow still counts`() {
        assertTrue(ArrowAnnotation("a", red, 4f, Offset(0f, 10f), Offset(40f, 10f)).isMeaningful)
        assertFalse(ArrowAnnotation("a", red, 4f, Offset(0f, 10f), Offset(2f, 10f)).isMeaningful)
    }

    @Test
    fun `arrow head scales with the stroke`() {
        assertEquals(16f, ArrowAnnotation("a", red, 4f, Offset.Zero, Offset(50f, 0f)).headLength)
    }

    @Test
    fun `pen folds tiny moves into the previous point`() {
        val pen = PenAnnotation("p", red, 4f, listOf(Offset(0f, 0f)))
        assertSame(pen, pen.dragTo(Offset(0.5f, 0.5f)))
        assertEquals(2, pen.dragTo(Offset(10f, 0f)).points.size)
    }

    @Test
    fun `a pen dot is meaningful`() {
        assertTrue(PenAnnotation("p", red, 4f, listOf(Offset(3f, 3f))).isMeaningful)
    }

    @Test
    fun `text ignores drags and rejects blank captions`() {
        val text = TextAnnotation("t", red, 4f, Offset(1f, 1f), "hi", 24f)
        assertSame(text, text.dragTo(Offset(50f, 50f)))
        assertFalse(text.copy(text = "  ").isMeaningful)
        assertEquals(24f, TextAnnotation.fontSizeFor(4f))
    }

    @Test
    fun `step radius scales with the stroke`() {
        val step = StepAnnotation("s", red, 3f, Offset(10f, 10f), 1)
        assertEquals(12f, step.radius)
        assertEquals(-2f, step.bounds.left)
    }

    @Test
    fun `redaction strength follows the stroke slider and pixelation never drops below 2`() {
        val blur = RedactionAnnotation("r", red, 4f, Offset.Zero, Offset(10f, 10f), RedactionStyle.Blur)
        assertEquals(10f, blur.sigma)
        assertEquals(2f, blur.copy(strokeWidth = 0.5f).blockSize)
    }

    @Test
    fun `translating moves every kind of annotation`() {
        val shift = Offset(-10f, -5f)
        val all = listOf(
            ArrowAnnotation("1", red, 4f, Offset(20f, 20f), Offset(40f, 40f)),
            RectangleAnnotation("2", red, 4f, Offset(20f, 20f), Offset(40f, 40f)),
            EllipseAnnotation("3", red, 4f, Offset(20f, 20f), Offset(40f, 40f)),
            HighlightAnnotation("4", red, 4f, Offset(20f, 20f), Offset(40f, 40f)),
            RedactionAnnotation("5", red, 4f, Offset(20f, 20f), Offset(40f, 40f), RedactionStyle.Blur),
            PenAnnotation("6", red, 4f, listOf(Offset(20f, 20f), Offset(40f, 40f))),
            TextAnnotation("7", red, 4f, Offset(20f, 20f), "x", 24f),
            StepAnnotation("8", red, 4f, Offset(20f, 20f), 1),
        )
        for (annotation in all) {
            val moved = annotation.translated(shift)
            assertEquals(annotation.id, moved.id)
            assertEquals(annotation.bounds.left + shift.x, moved.bounds.left, "${annotation::class.simpleName}")
            assertEquals(annotation.bounds.top + shift.y, moved.bounds.top, "${annotation::class.simpleName}")
        }
    }

    @Test
    fun `tools know whether they drag and whether they annotate`() {
        assertTrue(EditorTool.Arrow.isDragTool)
        assertFalse(EditorTool.Text.isDragTool)
        assertFalse(EditorTool.Step.isDragTool)
        assertFalse(EditorTool.Crop.createsAnnotation)
        assertTrue(EditorTool.Pen.createsAnnotation)
    }
}
