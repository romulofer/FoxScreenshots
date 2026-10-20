package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference
import com.foxdevelops.foxscreenshots.platform.session.HostOs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSettingsStoreTest {
    private fun tempDir(): Path = Files.createTempDirectory("fox-settings")

    @Test
    fun `values survive a new instance`() {
        val file = tempDir().resolve("deep/er/settings.properties")
        FileSettingsStore(file).putString("hotkey", "F9")
        assertEquals("F9", FileSettingsStore(file).getString("hotkey"))
    }

    @Test
    fun `a missing key is null and a corrupt file is survivable`() {
        val file = tempDir().resolve("settings.properties")
        Files.writeString(file, "\\uZZZZ=broken")
        assertNull(FileSettingsStore(file).getString("anything"))
    }

    @Test
    fun `settings controller persists through the file`() {
        val file = tempDir().resolve("settings.properties")
        SettingsController(FileSettingsStore(file)).setTheme(ThemePreference.Dark)
        assertEquals(ThemePreference.Dark, SettingsController(FileSettingsStore(file)).state.value.theme)
    }

    @Test
    fun `imported values seed a fresh file only`() {
        val file = tempDir().resolve("settings.properties")
        assertEquals("F9", FileSettingsStore(file, importFrom = mapOf("capture_hotkey" to "F9")).getString("capture_hotkey"))
        // Now the file exists: a later import must not overwrite what the user changed since.
        FileSettingsStore(file).putString("capture_hotkey", "F10")
        assertEquals("F10", FileSettingsStore(file, importFrom = mapOf("capture_hotkey" to "F9")).getString("capture_hotkey"))
    }

    @Test
    fun `config location follows each operating system`() {
        assertEquals(
            Paths.get("/home/u/.config/foxscreenshots/settings.properties"),
            FileSettingsStore.defaultFile(HostOs.Linux, emptyMap(), "/home/u"),
        )
        assertEquals(
            Paths.get("/xdg/foxscreenshots/settings.properties"),
            FileSettingsStore.defaultFile(HostOs.Linux, mapOf("XDG_CONFIG_HOME" to "/xdg"), "/home/u"),
        )
        assertEquals(
            Paths.get("/home/u/Library/Application Support/FoxScreenShots/settings.properties"),
            FileSettingsStore.defaultFile(HostOs.MacOs, emptyMap(), "/home/u"),
        )
        assertTrue(
            FileSettingsStore.defaultFile(HostOs.Windows, mapOf("APPDATA" to "C:\\Users\\u\\AppData\\Roaming"), "C:\\Users\\u")
                .toString().endsWith("settings.properties"),
        )
    }

    @Test
    fun `flat json from the flutter version is parsed`() {
        val json = """{"flutter.theme_mode":"light","flutter.locale_tag":"pt","flutter.timer_delay_seconds":5,""" +
            """"flutter.output_dir":"/home/u/Pictures","flutter.auto_save":true}"""
        assertEquals(
            mapOf(
                "flutter.theme_mode" to "light",
                "flutter.locale_tag" to "pt",
                "flutter.timer_delay_seconds" to "5",
                "flutter.output_dir" to "/home/u/Pictures",
                "flutter.auto_save" to "true",
            ),
            FileSettingsStore.parseFlatJson(json),
        )
    }

    @Test
    fun `escaped characters in the old file are restored`() {
        assertEquals("a\"b\\c", FileSettingsStore.parseFlatJson("""{"k":"a\"b\\c"}""")["k"])
    }

    @Test
    fun `legacy flutter preferences are found, prefix stripped, unknown keys dropped`() {
        val data = tempDir()
        val dir = Files.createDirectories(data.resolve("com.foxdevelops.foxscreenshots"))
        Files.writeString(
            dir.resolve("shared_preferences.json"),
            """{"flutter.capture_hotkey":"F9","flutter.unrelated":"x","other":"y"}""",
        )
        val legacy = FileSettingsStore.legacyFlutterPreferences(HostOs.Linux, mapOf("XDG_DATA_HOME" to data.toString()), "/nowhere")
        assertEquals(mapOf("capture_hotkey" to "F9"), legacy)
    }

    @Test
    fun `no legacy file means nothing to import`() {
        assertTrue(FileSettingsStore.legacyFlutterPreferences(HostOs.Linux, mapOf("XDG_DATA_HOME" to "/nonexistent"), "/x").isEmpty())
        assertTrue(FileSettingsStore.legacyFlutterPreferences(HostOs.MacOs).isEmpty())
    }
}
