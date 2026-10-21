package com.foxdevelops.foxscreenshots.core

import com.foxdevelops.foxscreenshots.core.storage.defaultFileName
import com.foxdevelops.foxscreenshots.core.storage.renderFileName
import com.foxdevelops.foxscreenshots.core.storage.requirePng
import com.foxdevelops.foxscreenshots.core.storage.uniqueFileName
import com.foxdevelops.foxscreenshots.fakePngBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageHelpersTest {
    @Test
    fun `default file name is timestamped and zero padded`() {
        assertEquals("foxshot_20260813_084205.png", defaultFileName(2026, 8, 13, 8, 42, 5))
    }

    @Test
    fun `pattern tokens are replaced and padded`() {
        assertEquals("shot-2026-08-13_08h42.png", renderFileName("shot-{year}-{month}-{day}_{hour}h{minute}", 2026, 8, 13, 8, 42, 5))
        assertEquals("20260813-084205.png", renderFileName("{date}-{time}", 2026, 8, 13, 8, 42, 5))
    }

    @Test
    fun `pattern cannot carry path segments or reserved characters`() {
        assertEquals("etcpasswd.png", renderFileName("../etc/passwd", 2026, 8, 13, 8, 42, 5))
        assertEquals("ab.png", renderFileName("a:b*?\"<>|\\", 2026, 8, 13, 8, 42, 5))
    }

    @Test
    fun `an empty or unusable pattern falls back to the default and an explicit png suffix is not doubled`() {
        assertEquals("foxshot_20260813_084205.png", renderFileName("///", 2026, 8, 13, 8, 42, 5))
        assertEquals("foxshot_20260813_084205.png", renderFileName("", 2026, 8, 13, 8, 42, 5))
        assertEquals("x.png", renderFileName("x.png", 2026, 8, 13, 8, 42, 5))
    }

    @Test
    fun `unique name keeps the first and numbers the rest`() {
        val taken = mutableSetOf<String>()
        fun next(): String = uniqueFileName("a.png") { it in taken }.also { taken += it }
        assertEquals(listOf("a.png", "a_2.png", "a_3.png"), List(3) { next() })
    }

    @Test
    fun `unique name works without an extension`() {
        assertEquals("a_2", uniqueFileName("a") { it == "a" })
    }

    @Test
    fun `only PNG bytes are accepted`() {
        requirePng(fakePngBytes)
        assertFailsWith<IllegalArgumentException> { requirePng(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { requirePng(ByteArray(16)) }
    }
}
