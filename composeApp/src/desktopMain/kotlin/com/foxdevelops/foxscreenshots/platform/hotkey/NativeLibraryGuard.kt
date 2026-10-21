package com.foxdevelops.foxscreenshots.platform.hotkey

import com.github.kwhat.jnativehook.NativeSystem
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Keeps the keyboard hook's native library trustworthy.
 *
 * The library is unpacked into a folder the user can write to (the install folder is read-only), and
 * what sits there gets loaded into the process and watches every key. So before it is loaded, anything
 * in that folder that is not byte-for-byte the copy shipped inside the jar is deleted, and the library's
 * own unpacking step then puts a fresh, trusted one back.
 *
 * This narrows the window rather than closing it: a file swapped *between* this check and the load would
 * still win, but that takes code already running as the same user, which could equally edit the app itself.
 */
internal object NativeLibraryGuard {
    /** The unpacked library, whatever the platform names it: `libJNativeHook-2.2.2.x86_64.so`, `JNativeHook-….dll`, … */
    private const val NAME_MARK = "JNativeHook"

    /** The library names the jar ships, one per operating system. */
    private val SHIPPED_NAMES = listOf("libJNativeHook.so", "JNativeHook.dll", "libJNativeHook.dylib")

    /** The copy of the library inside the jar for this machine, or `null` on a platform the hook does not support. */
    fun trustedLibrary(loader: ClassLoader = NativeSystem::class.java.classLoader): ByteArray? {
        val family = NativeSystem.getFamily().toString()
        val arch = NativeSystem.getArchitecture().toString()
        for (name in SHIPPED_NAMES) {
            val stream = loader.getResourceAsStream("com/github/kwhat/jnativehook/lib/$family/$arch/$name") ?: continue
            return stream.use { it.readBytes() }
        }
        return null
    }

    /** Deletes every library-looking file in [dir] that differs from [trusted] (or is not a plain file); returns what it removed. */
    fun purgeUntrusted(dir: Path, trusted: ByteArray): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        val expected = sha256(trusted)
        val removed = mutableListOf<Path>()
        Files.list(dir).use { entries ->
            for (entry in entries) {
                if (NAME_MARK !in entry.fileName.toString()) continue
                val plain = Files.isRegularFile(entry) && !Files.isSymbolicLink(entry)
                if (plain && MessageDigest.isEqual(sha256(Files.readAllBytes(entry)), expected)) continue
                Files.deleteIfExists(entry)
                removed.add(entry)
            }
        }
        return removed
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
