package com.foxdevelops.foxscreenshots

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the style rule that members are imported, not written out in full in the middle of the code
 * (`import kotlin.math.abs`, never `kotlin.math.abs(x)`), and that no file uses a wildcard import.
 */
class SourceHygieneTest {
    private val sourceRoots = listOf("src/commonMain", "src/desktopMain")

    private val qualifiedName = Regex("""(?<![\w.])(?:kotlin|java|javax|androidx|org|com)(?:\.[a-z][a-z0-9_]*)+\.[A-Za-z_]\w*""")
    private val stringLiteral = Regex(""""(?:\\.|[^"\\])*"""")

    private fun sources(): List<File> = sourceRoots.map(::File).filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    /** Code lines only: no imports, package lines, comments, KDoc or string literals. */
    private fun codeLines(file: File): List<Pair<Int, String>> {
        var inBlock = false
        return file.readLines().mapIndexedNotNull { index, raw ->
            val line = raw.trim()
            when {
                inBlock -> { if ("*/" in line) inBlock = false; null }
                line.startsWith("/*") -> { if ("*/" !in line) inBlock = true; null }
                line.startsWith("import ") || line.startsWith("package ") || line.startsWith("*") || line.startsWith("//") -> null
                else -> (index + 1) to raw.replace(stringLiteral, "\"\"").substringBefore("//")
            }
        }
    }

    @Test
    fun `no member is written with its full package in the middle of the code`() {
        val files = sources()
        assumeTrue("run from the composeApp module so the sources are found", files.isNotEmpty())
        val offenders = files.flatMap { file ->
            codeLines(file).filter { (_, code) -> qualifiedName.containsMatchIn(code) }
                .map { (number, code) -> "${file.path}:$number ${code.trim()}" }
        }
        assertEquals(emptyList(), offenders, "import these instead of qualifying them inline")
    }

    @Test
    fun `no file uses a wildcard import`() {
        val files = sources()
        assumeTrue("run from the composeApp module so the sources are found", files.isNotEmpty())
        val offenders = files.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> line.startsWith("import ") && line.trim().endsWith(".*") }
                .map { (index, line) -> "${file.path}:${index + 1} $line" }
        }
        assertEquals(emptyList(), offenders)
    }
}
