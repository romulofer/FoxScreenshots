package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.foxdevelops.foxscreenshots.FakeImageProcessor
import com.foxdevelops.foxscreenshots.fakeCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorControllerTest {
    private val processor = FakeImageProcessor()

    private fun controller() = EditorController(fakeCapture(), processor, Dispatchers.Unconfined)

    private suspend fun EditorController.drag(tool: EditorTool, from: Offset, to: Offset) {
        selectTool(tool)
        startDraft(from)
        updateDraft(to)
        endDraft()
    }

    @Test
    fun `opens clean with the arrow tool and no history`() = runTest {
        val editor = controller()
        editor.load()
        val state = editor.state.value
        assertNotNull(state.document.image)
        assertEquals(EditorTool.Arrow, state.tool)
        assertFalse(state.isDirty)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun `dragging commits an annotation and marks the editor dirty`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Rectangle, Offset(10f, 10f), Offset(80f, 60f))
        val state = editor.state.value
        assertEquals(1, state.annotations.size)
        assertTrue(state.annotations.single() is RectangleAnnotation)
        assertTrue(state.isDirty)
        assertTrue(state.canUndo)
        assertNull(state.draft)
    }

    @Test
    fun `the draft is visible while dragging but not yet in the history`() = runTest {
        val editor = controller()
        editor.load()
        editor.selectTool(EditorTool.Ellipse)
        editor.startDraft(Offset(5f, 5f))
        editor.updateDraft(Offset(50f, 50f))
        assertNotNull(editor.state.value.draft)
        assertTrue(editor.state.value.annotations.isEmpty())
        assertFalse(editor.state.value.canUndo)
    }

    @Test
    fun `a stray click leaves nothing behind`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Rectangle, Offset(10f, 10f), Offset(11f, 11f))
        assertTrue(editor.state.value.annotations.isEmpty())
        assertFalse(editor.state.value.isDirty)
    }

    @Test
    fun `undo and redo walk the history and restore the clean state`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Arrow, Offset(0f, 0f), Offset(50f, 50f))
        editor.undo()
        assertTrue(editor.state.value.annotations.isEmpty())
        assertFalse(editor.state.value.isDirty)
        assertTrue(editor.state.value.canRedo)
        editor.redo()
        assertEquals(1, editor.state.value.annotations.size)
        assertTrue(editor.state.value.isDirty)
        assertFalse(editor.state.value.canRedo)
    }

    @Test
    fun `a new edit drops the redo branch`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Arrow, Offset(0f, 0f), Offset(50f, 50f))
        editor.undo()
        editor.drag(EditorTool.Ellipse, Offset(0f, 0f), Offset(20f, 20f))
        assertFalse(editor.state.value.canRedo)
    }

    @Test
    fun `history is capped`() = runTest {
        val editor = controller()
        editor.load()
        repeat(EditorController.MAX_HISTORY + 10) { editor.addStep(Offset(it.toFloat(), 0f)) }
        var undone = 0
        while (editor.state.value.canUndo) {
            editor.undo()
            undone++
        }
        assertEquals(EditorController.MAX_HISTORY, undone)
    }

    @Test
    fun `text tool adds a caption sized from the stroke and ignores blanks`() = runTest {
        val editor = controller()
        editor.load()
        editor.setStrokeWidth(5f)
        editor.addText(Offset(3f, 4f), "hello")
        editor.addText(Offset(3f, 4f), "   ")
        val text = editor.state.value.annotations.single() as TextAnnotation
        assertEquals("hello", text.text)
        assertEquals(30f, text.fontSize)
    }

    @Test
    fun `step markers count up from one`() = runTest {
        val editor = controller()
        editor.load()
        editor.addStep(Offset(1f, 1f))
        editor.addStep(Offset(2f, 2f))
        assertEquals(listOf(1, 2), editor.state.value.annotations.map { (it as StepAnnotation).number })
    }

    @Test
    fun `tap tools do not start a drag`() = runTest {
        val editor = controller()
        editor.load()
        editor.selectTool(EditorTool.Text)
        editor.startDraft(Offset(5f, 5f))
        assertNull(editor.state.value.draft)
    }

    @Test
    fun `style choices flow into the next annotation`() = runTest {
        val editor = controller()
        editor.load()
        editor.selectColor(AnnotationPalette.Blue)
        editor.setStrokeWidth(9f)
        editor.setFilled(true)
        editor.drag(EditorTool.Rectangle, Offset(0f, 0f), Offset(40f, 40f))
        val rect = editor.state.value.annotations.single() as RectangleAnnotation
        assertEquals(AnnotationPalette.Blue, rect.color)
        assertEquals(9f, rect.strokeWidth)
        assertTrue(rect.filled)
    }

    @Test
    fun `blur and pixelate create redactions of the right style`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Blur, Offset(0f, 0f), Offset(40f, 40f))
        editor.drag(EditorTool.Pixelate, Offset(0f, 0f), Offset(40f, 40f))
        val styles = editor.state.value.annotations.map { (it as RedactionAnnotation).style }
        assertEquals(listOf(RedactionStyle.Blur, RedactionStyle.Pixelate), styles)
    }

    @Test
    fun `crop trims the base and moves the annotations with it`() = runTest {
        val editor = controller()
        editor.load()
        editor.addStep(Offset(60f, 40f))
        editor.drag(EditorTool.Crop, Offset(50f, 30f), Offset(150f, 90f))

        val state = editor.state.value
        assertEquals(listOf(Rect(50f, 30f, 150f, 90f)), processor.cropped)
        assertEquals(100, state.document.width)
        assertEquals(60, state.document.height)
        assertEquals(Offset(10f, 10f), (state.annotations.single() as StepAnnotation).center)
        assertFalse(state.isBusy)

        editor.undo()
        assertEquals(Offset(60f, 40f), (editor.state.value.annotations.single() as StepAnnotation).center)
    }

    @Test
    fun `a tiny crop drag is ignored`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Crop, Offset(10f, 10f), Offset(12f, 12f))
        assertTrue(processor.cropped.isEmpty())
    }

    @Test
    fun `flatten returns the original bytes when nothing was drawn`() = runTest {
        val editor = controller()
        editor.load()
        val flat = assertNotNull(editor.flatten())
        assertTrue(processor.flattenedWith.isEmpty())
        assertEquals(fakeCapture().pngBytes.toList(), flat.pngBytes.toList())
    }

    @Test
    fun `flatten bakes the annotations`() = runTest {
        val editor = controller()
        editor.load()
        editor.addStep(Offset(5f, 5f))
        assertNotNull(editor.flatten())
        assertEquals(1, processor.flattenedWith.single().size)
    }

    @Test
    fun `flatten waits for the decode`() = runTest {
        assertNull(controller().flatten())
    }

    @Test
    fun `markApplied clears the dirty flag until the next edit`() = runTest {
        val editor = controller()
        editor.load()
        editor.addStep(Offset(5f, 5f))
        editor.markApplied()
        assertFalse(editor.state.value.isDirty)
        editor.addStep(Offset(9f, 9f))
        assertTrue(editor.state.value.isDirty)
    }

    @Test
    fun `close releases every bitmap it owns`() = runTest {
        val editor = controller()
        editor.load()
        editor.close()
        assertEquals(1, processor.released.size)
    }

    @Test
    fun `a crop discarded by a new edit frees its bitmap immediately`() = runTest {
        val editor = controller()
        editor.load()
        editor.drag(EditorTool.Crop, Offset(0f, 0f), Offset(100f, 50f))
        editor.undo()
        assertTrue(processor.released.isEmpty(), "still reachable through redo")
        editor.addStep(Offset(1f, 1f))
        assertEquals(1, processor.released.size)
    }
}
