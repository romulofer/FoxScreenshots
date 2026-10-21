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

    /** Prompts for an image file (PNG, JPEG, BMP, GIF) and returns its bytes, or `null` if the user cancelled. */
    suspend fun pickImage(initialDirectory: String? = null): ByteArray?

    /**
     * Writes [pngBytes] straight to [directory] with a timestamped name,
     * creating the directory when missing. Only the app-chosen file name is
     * written under the resolved directory — callers cannot inject path
     * segments into it. [namePattern] is rendered with [renderFileName].
     */
    suspend fun savePngToDir(pngBytes: ByteArray, directory: String, namePattern: String = DEFAULT_FILE_NAME_PATTERN): String

    /** Asks the user for a folder. Returns `null` if cancelled. */
    suspend fun pickDirectory(initialDirectory: String? = null): String?
}

/** File name pattern used until the user picks another; `{date}` and `{time}` are replaced by the moment of capture. */
const val DEFAULT_FILE_NAME_PATTERN = "foxshot_{date}_{time}"

/** Timestamped file name, e.g. `foxshot_20260813_084200.png`. */
fun defaultFileName(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String =
    renderFileName(DEFAULT_FILE_NAME_PATTERN, year, month, day, hour, minute, second)

/**
 * Turns a user [pattern] into a `.png` file name. `{date}` becomes `yyyyMMdd`,
 * `{time}` becomes `HHmmss`, `{year}`, `{month}`, `{day}`, `{hour}`, `{minute}`
 * and `{second}` give the single fields. Characters a file name cannot carry
 * (path separators, reserved Windows characters, control characters) are
 * dropped, so a pattern can never reach outside the output folder. An empty
 * result falls back to [DEFAULT_FILE_NAME_PATTERN].
 */
fun renderFileName(pattern: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String {
    fun two(n: Int) = n.toString().padStart(2, '0')
    fun render(text: String) = text
        .replace("{date}", "$year${two(month)}${two(day)}")
        .replace("{time}", "${two(hour)}${two(minute)}${two(second)}")
        .replace("{year}", year.toString())
        .replace("{month}", two(month))
        .replace("{day}", two(day))
        .replace("{hour}", two(hour))
        .replace("{minute}", two(minute))
        .replace("{second}", two(second))
        .filter { it.code >= 32 && it !in FORBIDDEN_FILE_NAME_CHARS }
        .trim().trim('.')
        .removeSuffix(".png").trim()
    val stem = render(pattern).ifEmpty { render(DEFAULT_FILE_NAME_PATTERN) }
    return "$stem.png"
}

private const val FORBIDDEN_FILE_NAME_CHARS = "/\\:*?\"<>|"

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
