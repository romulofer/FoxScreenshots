package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.core.storage.OutputService
import com.foxdevelops.foxscreenshots.core.storage.DEFAULT_FILE_NAME_PATTERN
import com.foxdevelops.foxscreenshots.core.storage.renderFileName
import com.foxdevelops.foxscreenshots.core.storage.requirePng
import com.foxdevelops.foxscreenshots.core.storage.uniqueFileName
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

/** Saves screenshots to the local filesystem, with the system's native dialogs. */
class DesktopOutputService(private val now: () -> LocalDateTime = LocalDateTime::now) : OutputService {

    override suspend fun savePngWithDialog(
        pngBytes: ByteArray,
        initialDirectory: String?,
        suggestedName: String?,
    ): String? {
        requirePng(pngBytes)
        val baseName = (suggestedName ?: defaultName()).removeSuffix(".png")
        val target = FileKit.openFileSaver(
            suggestedName = baseName,
            extension = "png",
            directory = initialDirectory?.let(::PlatformFile),
        ) ?: return null
        return withContext(Dispatchers.IO) { write(Paths.get(target.absolutePath()), pngBytes).toString() }
    }

    override suspend fun pickImage(initialDirectory: String?): ByteArray? {
        val file = FileKit.openFilePicker(
            type = FileKitType.Image,
            directory = initialDirectory?.let(::PlatformFile),
        ) ?: return null
        return withContext(Dispatchers.IO) {
            val path = Paths.get(file.absolutePath())
            require(Files.size(path) <= MAX_IMAGE_BYTES) { "image too large" }
            Files.readAllBytes(path)
        }
    }

    override suspend fun savePngToDir(pngBytes: ByteArray, directory: String, namePattern: String): String {
        requirePng(pngBytes)
        return withContext(Dispatchers.IO) {
            val dir = Paths.get(directory)
            Files.createDirectories(dir)
            val root = dir.toRealPath()
            val name = uniqueFileName(defaultName(namePattern)) { Files.exists(root.resolve(it)) }
            write(root.resolve(name), pngBytes).toString()
        }
    }

    override suspend fun pickDirectory(initialDirectory: String?): String? =
        FileKit.openDirectoryPicker(directory = initialDirectory?.let(::PlatformFile))?.absolutePath()

    private fun write(path: Path, bytes: ByteArray): Path = Files.write(path, bytes)

    private fun defaultName(pattern: String = DEFAULT_FILE_NAME_PATTERN): String {
        val t = now()
        return renderFileName(pattern, t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second)
    }

    private companion object {
        /** A screenshot larger than this is not a screenshot; refuse it instead of filling the heap. */
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
    }
}
