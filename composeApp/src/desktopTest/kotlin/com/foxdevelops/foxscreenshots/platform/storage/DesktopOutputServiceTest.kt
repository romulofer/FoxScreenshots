package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.fakePngBytes
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOutputServiceTest {
    private val fixedNow = LocalDateTime.of(2026, 8, 13, 8, 42, 5)
    private val service = DesktopOutputService { fixedNow }

    @Test
    fun `saves into the folder with a timestamped name and creates it when missing`() = runTest {
        val dir = Files.createTempDirectory("fox-out").resolve("a/b")
        val path = service.savePngToDir(fakePngBytes, dir.toString())
        assertEquals("foxshot_20260813_084205.png", Paths.get(path).fileName.toString())
        assertContentEquals(fakePngBytes, Files.readAllBytes(Paths.get(path)))
    }

    @Test
    fun `a custom pattern names the file`() = runTest {
        val dir = Files.createTempDirectory("fox-out").toString()
        val path = service.savePngToDir(fakePngBytes, dir, "tela-{date}")
        assertEquals("tela-20260813.png", Paths.get(path).fileName.toString())
    }

    @Test
    fun `same-second saves never overwrite each other`() = runTest {
        val dir = Files.createTempDirectory("fox-out").toString()
        val names = List(3) { Paths.get(service.savePngToDir(fakePngBytes, dir)).fileName.toString() }
        assertEquals(
            listOf("foxshot_20260813_084205.png", "foxshot_20260813_084205_2.png", "foxshot_20260813_084205_3.png"),
            names,
        )
    }

    @Test
    fun `refuses bytes that are not a PNG`() = runTest {
        val dir = Files.createTempDirectory("fox-out").toString()
        assertFailsWith<IllegalArgumentException> { service.savePngToDir(ByteArray(32), dir) }
        assertTrue(Files.list(Paths.get(dir)).use { it.count() } == 0L, "nothing written")
    }

    @Test
    fun `the file always lands under the chosen folder, even through a symlink`() = runTest {
        val real = Files.createTempDirectory("fox-real")
        val link = Files.createTempDirectory("fox-link").resolve("shortcut")
        runCatching { Files.createSymbolicLink(link, real) }.onFailure { return@runTest }
        val path = Paths.get(service.savePngToDir(fakePngBytes, link.toString()))
        assertEquals(real.toRealPath(), path.parent)
    }

    @Test
    fun `revealing a file that is not there fails instead of throwing`() = runTest {
        assertFalse(service.reveal("/definitely/not/here.png"))
    }
}
