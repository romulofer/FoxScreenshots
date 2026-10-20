package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The editable document: the base image plus its annotation layer.
 *
 * One immutable snapshot per undo step. Crops create a new base image;
 * everything else shares the same one, so history costs a list copy, not a
 * bitmap copy.
 */
class EditorDocument(
    val pngBytes: ByteArray,
    /** Decoded base. Null only between opening the editor and the decode finishing. */
    val image: ImageBitmap?,
    val width: Int,
    val height: Int,
    val annotations: List<Annotation>,
) {
    fun copy(
        pngBytes: ByteArray = this.pngBytes,
        image: ImageBitmap? = this.image,
        width: Int = this.width,
        height: Int = this.height,
        annotations: List<Annotation> = this.annotations,
    ) = EditorDocument(pngBytes, image, width, height, annotations)
}

/** Everything the editor UI paints from. */
data class EditorState(
    val document: EditorDocument,
    val tool: EditorTool = EditorTool.Arrow,
    val color: Color = EditorController.DEFAULT_COLOR,
    val strokeWidth: Float = EditorController.DEFAULT_STROKE_WIDTH,
    /** Whether the next rectangle is drawn solid instead of just outlined. */
    val filled: Boolean = false,
    /** The annotation being dragged right now; not yet part of the undo history. */
    val draft: Annotation? = null,
    /** The rectangle being dragged with the crop tool. */
    val cropDraft: Rect? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** A crop or an export is rasterizing; the canvas ignores input meanwhile. */
    val isBusy: Boolean = false,
    /** Whether the document differs from the last version handed back to the session. */
    val isDirty: Boolean = false,
) {
    val annotations: List<Annotation> get() = document.annotations
}

/**
 * Drives the annotation editor: tool selection, the drag being drawn,
 * undo/redo, crop, and flattening for export. Scoped to one capture; call
 * [close] when the editor goes away so decoded pixels do not outlive the window.
 */
class EditorController(
    private val capture: CaptureResult,
    private val processor: ImageProcessor,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val past = ArrayDeque<EditorDocument>()
    private val future = ArrayDeque<EditorDocument>()

    /** Images this controller decoded or rendered; released together on [close]. */
    private val owned = mutableListOf<ImageBitmap>()

    private var savedDocument: EditorDocument
    private var cropAnchor: Offset? = null
    private var nextId = 0
    private var closed = false

    private val mutableState: MutableStateFlow<EditorState>
    val state: StateFlow<EditorState>

    init {
        val document = EditorDocument(capture.pngBytes, null, capture.width, capture.height, emptyList())
        savedDocument = document
        mutableState = MutableStateFlow(EditorState(document))
        state = mutableState.asStateFlow()
    }

    private val current: EditorState get() = mutableState.value

    /** Decodes the base image off the UI thread. */
    suspend fun load() {
        val image = withContext(workDispatcher) { processor.decode(capture.pngBytes) }
        if (closed) {
            processor.release(image)
            return
        }
        owned += image
        val document = current.document.copy(image = image, width = image.width, height = image.height)
        // Decoding is not an edit: carry the "saved" marker over so the editor does not open dirty.
        if (savedDocument === current.document) savedDocument = document
        mutableState.update { it.copy(document = document, isDirty = isDirty(document)) }
    }

    fun selectTool(tool: EditorTool) {
        if (current.isBusy) return
        cropAnchor = null
        mutableState.update { it.copy(tool = tool, draft = null, cropDraft = null) }
    }

    fun selectColor(color: Color) = mutableState.update { it.copy(color = color) }

    fun setStrokeWidth(width: Float) = mutableState.update { it.copy(strokeWidth = width) }

    fun setFilled(filled: Boolean) = mutableState.update { it.copy(filled = filled) }

    /** Starts a drag at [point] (image pixels). */
    fun startDraft(point: Offset) {
        val s = current
        if (s.isBusy) return
        if (s.tool == EditorTool.Crop) {
            cropAnchor = point
            mutableState.update { it.copy(cropDraft = rectFromPoints(point, point)) }
            return
        }
        if (!s.tool.isDragTool) return
        mutableState.update { it.copy(draft = newAnnotation(s.tool, point)) }
    }

    fun updateDraft(point: Offset) {
        val anchor = cropAnchor
        if (anchor != null) {
            // Anchored on where the drag began, not on the rectangle's top-left: a
            // normalized rect moves its own corner once the pointer goes up or left.
            mutableState.update { it.copy(cropDraft = rectFromPoints(anchor, point)) }
            return
        }
        val draft = current.draft ?: return
        mutableState.update { it.copy(draft = draft.dragTo(point)) }
    }

    /** Ends the drag: commits the annotation, or applies the crop. */
    suspend fun endDraft() {
        val crop = current.cropDraft
        if (crop != null) {
            cropAnchor = null
            mutableState.update { it.copy(cropDraft = null) }
            if (crop.width >= Annotation.MIN_DRAG_EXTENT && crop.height >= Annotation.MIN_DRAG_EXTENT) {
                applyCrop(crop)
            }
            return
        }
        val draft = current.draft
        mutableState.update { it.copy(draft = null) }
        if (draft == null || !draft.isMeaningful) return
        commit(current.document.copy(annotations = current.annotations + draft))
    }

    /** Places a caption at [position]. No-op for blank text, so cancelling the dialog leaves nothing behind. */
    fun addText(position: Offset, text: String) {
        val s = current
        if (closed || s.isBusy || text.isBlank()) return
        commit(
            s.document.copy(
                annotations = s.annotations + TextAnnotation(
                    id = newId(),
                    color = s.color,
                    strokeWidth = s.strokeWidth,
                    position = position,
                    text = text,
                    fontSize = TextAnnotation.fontSizeFor(s.strokeWidth),
                ),
            ),
        )
    }

    /** Drops the next badge in the 1, 2, 3… sequence at [center]. */
    fun addStep(center: Offset) {
        val s = current
        if (s.isBusy) return
        val next = s.annotations.count { it is StepAnnotation } + 1
        commit(
            s.document.copy(
                annotations = s.annotations + StepAnnotation(newId(), s.color, s.strokeWidth, center, next),
            ),
        )
    }

    /**
     * Trims the base image to [rect] and moves the annotations with it, so a
     * crop never silently relocates a mark relative to what it points at.
     */
    suspend fun applyCrop(rect: Rect) {
        val s = current
        val image = s.document.image ?: return
        if (s.isBusy) return

        val baseAnnotations = s.annotations
        mutableState.update { it.copy(isBusy = true) }
        try {
            val cropped = withContext(workDispatcher) { processor.crop(image, rect) }
            if (closed) {
                processor.release(cropped.image)
                return
            }
            owned += cropped.image
            val shift = Offset(-floor(rect.left), -floor(rect.top))
            commit(
                EditorDocument(
                    pngBytes = cropped.pngBytes,
                    image = cropped.image,
                    width = cropped.width,
                    height = cropped.height,
                    annotations = baseAnnotations.map { it.translated(shift) },
                ),
            )
        } finally {
            if (!closed) mutableState.update { it.copy(isBusy = false) }
        }
    }

    fun undo() {
        val s = current
        if (past.isEmpty() || s.isBusy) return
        future.addLast(s.document)
        val previous = past.removeLast()
        mutableState.update {
            it.copy(
                document = previous,
                draft = null,
                cropDraft = null,
                canUndo = past.isNotEmpty(),
                canRedo = true,
                isDirty = isDirty(previous),
            )
        }
    }

    fun redo() {
        val s = current
        if (future.isEmpty() || s.isBusy) return
        past.addLast(s.document)
        val next = future.removeLast()
        mutableState.update {
            it.copy(
                document = next,
                draft = null,
                cropDraft = null,
                canUndo = true,
                canRedo = future.isNotEmpty(),
                isDirty = isDirty(next),
            )
        }
    }

    /**
     * Bakes the annotations into the image and returns the result, ready for
     * the clipboard, a file, or the session gallery. Null while the base image
     * is still decoding.
     */
    suspend fun flatten(): ProcessedImage? {
        val s = current
        val image = s.document.image ?: return null
        // Nothing to bake: hand back the bytes we already have instead of re-encoding.
        if (s.annotations.isEmpty()) return ProcessedImage(image, s.document.pngBytes)
        return withContext(workDispatcher) { processor.flatten(image, s.annotations) }
    }

    /** Records that the current document is what the session now holds, so closing needs no "discard?" prompt. */
    fun markApplied() {
        savedDocument = current.document
        mutableState.update { it.copy(isDirty = false) }
    }

    /** Releases every bitmap this controller owns. */
    fun close() {
        closed = true
        owned.forEach(processor::release)
        owned.clear()
    }

    /** Pushes [document] as the new present and clears the redo branch. */
    private fun commit(document: EditorDocument) {
        val dropped = future.toMutableList()
        future.clear()
        past.addLast(current.document)
        mutableState.update {
            it.copy(document = document, draft = null, canUndo = true, canRedo = false, isDirty = isDirty(document))
        }
        if (past.size > MAX_HISTORY) dropped += past.removeFirst()
        dropped.forEach { releaseImage(it.image) }
    }

    /**
     * Frees a bitmap the moment the last snapshot holding it leaves the
     * history: a screenshot-sized crop is megabytes, and screen pixels may be
     * sensitive.
     */
    private fun releaseImage(image: ImageBitmap?) {
        if (image == null) return
        val reachable = listOf(current.document) + past + future
        if (reachable.any { it.image === image }) return
        processor.release(image)
        owned.remove(image)
    }

    private fun isDirty(document: EditorDocument) = document !== savedDocument

    private fun newAnnotation(tool: EditorTool, point: Offset): Annotation {
        val id = newId()
        val s = current
        val color = s.color
        val width = s.strokeWidth
        return when (tool) {
            EditorTool.Arrow -> ArrowAnnotation(id, color, width, point, point)
            EditorTool.Rectangle -> RectangleAnnotation(id, color, width, point, point, s.filled)
            EditorTool.Ellipse -> EllipseAnnotation(id, color, width, point, point)
            EditorTool.Highlight -> HighlightAnnotation(id, color, width, point, point)
            EditorTool.Blur -> RedactionAnnotation(id, color, width, point, point, RedactionStyle.Blur)
            EditorTool.Pixelate -> RedactionAnnotation(id, color, width, point, point, RedactionStyle.Pixelate)
            // Tap tools never start a drag; crop is not an annotation.
            EditorTool.Pen, EditorTool.Text, EditorTool.Step, EditorTool.Crop ->
                PenAnnotation(id, color, width, listOf(point))
        }
    }

    private fun newId(): String = "ann-${nextId++}"

    companion object {
        /** Cap on undo steps; a crop-heavy session would otherwise pin every intermediate bitmap in RAM. */
        const val MAX_HISTORY = 50
        const val DEFAULT_STROKE_WIDTH = 4f
        val DEFAULT_COLOR = AnnotationPalette.Red
    }
}

/** Whole-pixel bounds: a fractional source rect would resample the crop and soften text. */
fun Rect.roundedOut(): Rect = Rect(floor(left), floor(top), ceil(right), ceil(bottom))
