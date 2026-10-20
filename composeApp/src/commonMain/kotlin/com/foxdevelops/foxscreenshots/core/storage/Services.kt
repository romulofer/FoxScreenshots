package com.foxdevelops.foxscreenshots.core.storage

/** Puts a captured image on the system clipboard as PNG. */
interface ClipboardService {
    /** Returns `false` if the platform exposes no writable clipboard. */
    suspend fun copyPng(pngBytes: ByteArray): Boolean
}

/** Saves screenshots to disk as PNG. Local filesystem only. */
interface OutputService {
    /** Prompts for a location and writes [pngBytes]. Returns the saved path, or `null` if the user cancelled. */
    suspend fun savePngWithDialog(
        pngBytes: ByteArray,
        initialDirectory: String? = null,
        suggestedName: String? = null,
    ): String?

    /**
     * Writes [pngBytes] straight to [directory] with a timestamped name,
     * creating the directory when missing. Only the app-chosen file name is
     * written under the resolved directory — callers cannot inject path
     * segments into it.
     */
    suspend fun savePngToDir(pngBytes: ByteArray, directory: String): String

    /** Asks the user for a folder. Returns `null` if cancelled. */
    suspend fun pickDirectory(initialDirectory: String? = null): String?
}

/** Timestamped file name, e.g. `foxshot_20260813_084200.png`. */
fun defaultFileName(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String {
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "foxshot_${year}${two(month)}${two(day)}_${two(hour)}${two(minute)}${two(second)}.png"
}

/** Timestamped names collide when several shots are written in the same second; append `_2`, `_3`… so none is overwritten. */
fun uniqueFileName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name
    val dot = name.lastIndexOf('.')
    val stem = if (dot >= 0) name.substring(0, dot) else name
    val ext = if (dot >= 0) name.substring(dot) else ""
    var n = 2
    while (exists("${stem}_$n$ext")) n++
    return "${stem}_$n$ext"
}

private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/** Throws unless [bytes] starts with the PNG signature. */
fun requirePng(bytes: ByteArray) {
    require(bytes.size >= PNG_MAGIC.size) { "too short for PNG (${bytes.size} bytes)" }
    for (i in PNG_MAGIC.indices) require(bytes[i] == PNG_MAGIC[i]) { "not a PNG" }
}
