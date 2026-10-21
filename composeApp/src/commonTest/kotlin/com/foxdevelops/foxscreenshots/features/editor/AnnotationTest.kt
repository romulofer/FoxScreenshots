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

    private fun arrow(from: Offset, to: Offset) = ArrowAnnotation("a", Color.Red, 4f, from, to)

    @Test
    fun `an arrow is hit along its line, not in the empty corner of its box`() {
        val arrow = arrow(Offset(0f, 0f), Offset(100f, 100f))
        assertTrue(arrow.hitTest(Offset(50f, 52f), tolerance = 4f))
        assertFalse(arrow.hitTest(Offset(90f, 10f), tolerance = 4f))
    }

    @Test
    fun `an outlined rectangle is hit on its edge but not inside`() {
        val rect = RectangleAnnotation("r", Color.Red, 4f, Offset(0f, 0f), Offset(200f, 100f))
        assertTrue(rect.hitTest(Offset(100f, 1f), tolerance = 4f))
        assertTrue(rect.hitTest(Offset(199f, 50f), tolerance = 4f))
        assertFalse(rect.hitTest(Offset(100f, 50f), tolerance = 4f))
    }

    @Test
    fun `a filled rectangle is hit anywhere inside`() {
        val rect = RectangleAnnotation("r", Color.Red, 4f, Offset(0f, 0f), Offset(200f, 100f), filled = true)
        assertTrue(rect.hitTest(Offset(100f, 50f), tolerance = 4f))
        assertFalse(rect.hitTest(Offset(300f, 50f), tolerance = 4f))
    }

    @Test
    fun `an ellipse is hit on its outline`() {
        val ellipse = EllipseAnnotation("e", Color.Red, 4f, Offset(0f, 0f), Offset(200f, 100f))
        assertTrue(ellipse.hitTest(Offset(200f, 50f), tolerance = 4f))
        assertTrue(ellipse.hitTest(Offset(100f, 0f), tolerance = 4f))
        assertFalse(ellipse.hitTest(Offset(100f, 50f), tolerance = 4f))
    }

    @Test
    fun `a pen stroke is hit near its path only`() {
        val pen = PenAnnotation("p", Color.Red, 4f, listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f)))
        assertTrue(pen.hitTest(Offset(50f, 3f), tolerance = 4f))
        assertTrue(pen.hitTest(Offset(101f, 60f), tolerance = 4f))
        assertFalse(pen.hitTest(Offset(40f, 60f), tolerance = 4f))
    }

    @Test
    fun `a single-dot pen stroke can be hit`() {
        val dot = PenAnnotation("p", Color.Red, 6f, listOf(Offset(10f, 10f)))
        assertTrue(dot.hitTest(Offset(12f, 11f), tolerance = 2f))
        assertFalse(dot.hitTest(Offset(40f, 40f), tolerance = 2f))
    }

    @Test
    fun `text and steps fall back to their box`() {
        val text = TextAnnotation("t", Color.Red, 4f, Offset(10f, 10f), "hello", fontSize = 20f)
        assertTrue(text.hitTest(Offset(30f, 20f), tolerance = 2f))
        val step = StepAnnotation("s", Color.Red, 4f, Offset(50f, 50f), 1)
        assertTrue(step.hitTest(Offset(55f, 55f), tolerance = 2f))
        assertFalse(step.hitTest(Offset(200f, 200f), tolerance = 2f))
    }

    @Test
    fun `a multi-line caption is as tall as its lines and as wide as its longest`() {
        val one = TextAnnotation("t", Color.Red, 4f, Offset.Zero, "abc", fontSize = 10f)
        val three = TextAnnotation("t", Color.Red, 4f, Offset.Zero, "abc\nabcdef\nab", fontSize = 10f)
        assertEquals(one.bounds.height * 3f, three.bounds.height, 0.001f)
        assertEquals(6 * 10f * 0.6f, three.bounds.width, 0.001f)
    }

    @Test
    fun `distance to a segment is measured to its nearest point`() {
        assertEquals(5f, distanceToSegment(Offset(5f, 5f), Offset(0f, 0f), Offset(10f, 0f)), 0.001f)
        assertEquals(5f, distanceToSegment(Offset(-3f, 4f), Offset(0f, 0f), Offset(10f, 0f)), 0.001f)
        assertEquals(5f, distanceToSegment(Offset(3f, 4f), Offset(0f, 0f), Offset(0f, 0f)), 0.001f)
    }
}
