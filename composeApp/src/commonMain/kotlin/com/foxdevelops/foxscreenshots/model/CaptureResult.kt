package com.foxdevelops.foxscreenshots.model

/**
 * The product of a capture: PNG bytes plus metadata.
 *
 * Held in the in-memory session list and passed to the editor, clipboard and
 * output services. Never leaves the device.
 */
class CaptureResult(
    val id: String,
    val pngBytes: ByteArray,
    val width: Int,
    val height: Int,
    val takenAtMillis: Long,
) {
    val byteLength: Int get() = pngBytes.size

    override fun toString(): String = "CaptureResult($id, ${width}x$height, ${pngBytes.size} bytes)"
}
