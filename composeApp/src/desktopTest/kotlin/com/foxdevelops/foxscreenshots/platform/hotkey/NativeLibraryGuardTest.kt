package com.foxdevelops.foxscreenshots.platform.hotkey

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeLibraryGuardTest {
    private val trusted = byteArrayOf(1, 2, 3, 4, 5)
    private val dir = Files.createTempDirectory("fox-native")

    @Test
    fun `the shipped library is found for this machine`() {
        val library = NativeLibraryGuard.trustedLibrary()
        assertNotNull(library)
        assertTrue(library.size > 10_000, "a real native library, not a stub")
    }

    @Test
    fun `an identical copy stays`() {
        val file = dir.resolve("libJNativeHook-2.2.2.x86_64.so")
        Files.write(file, trusted)
        assertTrue(NativeLibraryGuard.purgeUntrusted(dir, trusted).isEmpty())
        assertContentEquals(trusted, Files.readAllBytes(file))
    }

    @Test
    fun `a tampered copy is deleted`() {
        val file = dir.resolve("libJNativeHook-2.2.2.x86_64.so")
        Files.write(file, byteArrayOf(9, 9, 9))
        assertEquals(listOf(file), NativeLibraryGuard.purgeUntrusted(dir, trusted))
        assertFalse(Files.exists(file))
    }

    @Test
    fun `a copy with one flipped byte is deleted`() {
        val file = dir.resolve("JNativeHook-2.2.2.x86_64.dll")
        Files.write(file, trusted.copyOf().also { it[4] = 6 })
        assertEquals(1, NativeLibraryGuard.purgeUntrusted(dir, trusted).size)
    }

    @Test
    fun `a link planted in place of the library is removed, not followed`() {
        val target = Files.write(dir.resolve("elsewhere"), trusted)
        val link = dir.resolve("libJNativeHook-2.2.2.x86_64.so")
        try {
            Files.createSymbolicLink(link, target)
        } catch (_: UnsupportedOperationException) {
            assumeTrue("no symbolic links here", false)
        }
        assertEquals(listOf(link), NativeLibraryGuard.purgeUntrusted(dir, trusted))
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(target), "what the link pointed at is left alone")
    }

    @Test
    fun `other files in the folder are not touched`() {
        val other = Files.write(dir.resolve("notes.txt"), byteArrayOf(7))
        assertTrue(NativeLibraryGuard.purgeUntrusted(dir, trusted).isEmpty())
        assertTrue(Files.exists(other))
    }

    @Test
    fun `a folder that does not exist yet has nothing to purge`() {
        assertTrue(NativeLibraryGuard.purgeUntrusted(dir.resolve("missing"), trusted).isEmpty())
    }
}
