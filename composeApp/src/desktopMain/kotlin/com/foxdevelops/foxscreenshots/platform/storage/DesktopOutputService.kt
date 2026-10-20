package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.core.storage.OutputService
import com.foxdevelops.foxscreenshots.core.storage.defaultFileName
import com.foxdevelops.foxscreenshots.core.storage.requirePng
import com.foxdevelops.foxscreenshots.core.storage.uniqueFileName
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
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

    override suspend fun savePngToDir(pngBytes: ByteArray, directory: String): String {
        requirePng(pngBytes)
        return withContext(Dispatchers.IO) {
            val dir = Paths.get(directory)
            Files.createDirectories(dir)
            val root = dir.toRealPath()
            val name = uniqueFileName(defaultName()) { Files.exists(root.resolve(it)) }
            write(root.resolve(name), pngBytes).toString()
        }
    }

    override suspend fun pickDirectory(initialDirectory: String?): String? =
        FileKit.openDirectoryPicker(directory = initialDirectory?.let(::PlatformFile))?.absolutePath()

    private fun write(path: Path, bytes: ByteArray): Path = Files.write(path, bytes)

    private fun defaultName(): String {
        val t = now()
        return defaultFileName(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second)
    }
}
