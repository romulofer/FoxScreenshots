package com.foxdevelops.foxscreenshots.core

import com.foxdevelops.foxscreenshots.core.storage.defaultFileName
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
