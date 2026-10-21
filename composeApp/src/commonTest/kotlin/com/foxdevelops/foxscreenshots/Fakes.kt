package com.foxdevelops.foxscreenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.capture.ScreenSize
import com.foxdevelops.foxscreenshots.core.storage.ClipboardService
import com.foxdevelops.foxscreenshots.core.storage.OutputService
import com.foxdevelops.foxscreenshots.features.capture.CaptureSurface
import com.foxdevelops.foxscreenshots.features.capture.FrozenFrame
import com.foxdevelops.foxscreenshots.features.editor.Annotation
import com.foxdevelops.foxscreenshots.features.editor.ImageProcessor
import com.foxdevelops.foxscreenshots.features.editor.ProcessedImage
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlin.time.Duration

/** A valid PNG signature followed by filler, enough for code that only checks the magic. */
val fakePngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

fun fakeCapture(id: String = "shot-1", width: Int = 200, height: Int = 100) =
    CaptureResult(id, fakePngBytes, width, height, takenAtMillis = 0)

/** Records calls and returns [ImageBitmap]s of the asked size, with no rasterizer involved. */
class FakeImageProcessor : ImageProcessor {
    val released = mutableListOf<ImageBitmap>()
    var cropped = mutableListOf<Rect>()
    var flattenedWith = mutableListOf<List<Annotation>>()
    var decodeSize = 200 to 100

    override fun decode(png: ByteArray): ImageBitmap = ImageBitmap(decodeSize.first, decodeSize.second)

    override suspend fun crop(base: ImageBitmap, rect: Rect): ProcessedImage {
        cropped += rect
        return ProcessedImage(ImageBitmap(rect.width.toInt(), rect.height.toInt()), fakePngBytes)
    }

    override suspend fun flatten(base: ImageBitmap, annotations: List<Annotation>): ProcessedImage {
        flattenedWith += annotations
        return ProcessedImage(ImageBitmap(base.width, base.height), fakePngBytes + 9)
    }

    override fun thumbnail(png: ByteArray, maxWidth: Int): ImageBitmap = ImageBitmap(maxWidth, maxWidth / 2)

    override fun release(image: ImageBitmap) {
        released += image
    }
}

class FakeCaptureService(
    private val screen: ScreenSize = ScreenSize(200, 100),
    var activeWindow: CaptureRegion? = CaptureRegion(10, 10, 50, 40),
    /** The bytes every grab returns; UI tests that really decode them pass a real PNG. */
    private val png: ByteArray = fakePngBytes,
) : ScreenCaptureService {
    val regions = mutableListOf<CaptureRegion>()
    var fullGrabs = 0

    override suspend fun grabFullVirtualScreen(): CaptureResult {
        fullGrabs++
        return CaptureResult("full-$fullGrabs", png, screen.width, screen.height, takenAtMillis = 0)
    }

    override suspend fun grabRegion(region: CaptureRegion): CaptureResult {
        regions += region
        return CaptureResult("region-${regions.size}", png, region.width, region.height, takenAtMillis = 0)
    }

    override suspend fun activeWindowRegion(): CaptureRegion? = activeWindow
    override suspend fun virtualScreenSize(): ScreenSize = screen
}

class FakeSurface(var selection: CaptureRegion? = CaptureRegion(10, 20, 30, 40)) : CaptureSurface {
    val events = mutableListOf<String>()
    var countdownFor: Duration? = null

    /** What the next countdown resolves to: `true` = ran out, `false` = the user cancelled. */
    var countdownCompletes = true

    override suspend fun hideHub() {
        events += "hide"
    }

    override suspend fun restoreHub() {
        events += "restore"
    }

    override suspend fun selectRegion(frame: FrozenFrame): CaptureRegion? {
        events += "select"
        return selection
    }

    override suspend fun countdown(wait: Duration): Boolean {
        events += "countdown"
        countdownFor = wait
        return countdownCompletes
    }
}

class FakeClipboard(private val works: Boolean = true) : ClipboardService {
    val copied = mutableListOf<ByteArray>()
    override suspend fun copyPng(pngBytes: ByteArray): Boolean {
        if (!works) throw IllegalStateException("no clipboard")
        copied += pngBytes
        return true
    }
}

class FakeOutput : OutputService {
    val saved = mutableListOf<Pair<String, ByteArray>>()
    var failing = false
    override suspend fun savePngWithDialog(pngBytes: ByteArray, initialDirectory: String?, suggestedName: String?) =
        "/tmp/${suggestedName ?: "x.png"}"

    var pickedImage: ByteArray? = null
    var lastPattern: String? = null

    override suspend fun pickImage(initialDirectory: String?): ByteArray? = pickedImage

    override suspend fun savePngToDir(pngBytes: ByteArray, directory: String, namePattern: String): String {
        lastPattern = namePattern
        if (failing) throw IllegalStateException("read-only")
        saved += directory to pngBytes
        return "$directory/shot.png"
    }

    var revealable = true
    val revealed = mutableListOf<String>()

    override suspend fun reveal(path: String): Boolean {
        if (revealable) revealed += path
        return revealable
    }

    override suspend fun pickDirectory(initialDirectory: String?): String? = "/picked"
}
