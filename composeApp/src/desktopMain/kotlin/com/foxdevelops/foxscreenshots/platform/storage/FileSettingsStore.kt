package com.foxdevelops.foxscreenshots.platform.storage

import com.foxdevelops.foxscreenshots.core.settings.SettingsStore
import com.foxdevelops.foxscreenshots.platform.session.HostOs
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Settings in one small properties file under the OS's per-user config
 * directory. Local only; written atomically so a crash cannot leave half a file.
 */
class FileSettingsStore(private val file: Path, importFrom: Map<String, String> = emptyMap()) : SettingsStore {
    private val values = Properties()

    init {
        if (Files.exists(file)) {
            runCatching { Files.newBufferedReader(file).use(values::load) }
        } else if (importFrom.isNotEmpty()) {
            importFrom.forEach { (key, value) -> values.setProperty(key, value) }
            save()
        }
    }

    @Synchronized
    override fun getString(key: String): String? = values.getProperty(key)

    @Synchronized
    override fun putString(key: String, value: String) {
        values.setProperty(key, value)
        save()
    }

    private fun save() {
        try {
            Files.createDirectories(file.parent)
            val temp = Files.createTempFile(file.parent, "settings", ".tmp")
            Files.newBufferedWriter(temp).use { values.store(it, null) }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            // Preferences are a convenience: a read-only home must not stop the app.
        }
    }

    companion object {
        private const val DIR_NAME = "foxscreenshots"

        /** `~/.config/foxscreenshots`, `%APPDATA%\FoxScreenShots`, or `~/Library/Application Support/FoxScreenShots`. */
        fun defaultFile(
            os: HostOs = HostOs.detect(),
            env: Map<String, String> = System.getenv(),
            home: String = System.getProperty("user.home"),
        ): Path = when (os) {
            HostOs.Windows -> Paths.get(env["APPDATA"] ?: "$home\\AppData\\Roaming", "FoxScreenShots", "settings.properties")
            HostOs.MacOs -> Paths.get(home, "Library", "Application Support", "FoxScreenShots", "settings.properties")
            else -> Paths.get(env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() } ?: "$home/.config", DIR_NAME, "settings.properties")
        }

        /**
         * Preferences the Flutter version of the app left behind (Linux and Windows
         * keep them as flat JSON with a `flutter.` prefix), so an upgrade keeps the
         * user's hotkeys, folder and theme.
         */
        fun legacyFlutterPreferences(
            os: HostOs = HostOs.detect(),
            env: Map<String, String> = System.getenv(),
            home: String = System.getProperty("user.home"),
        ): Map<String, String> {
            val path = when (os) {
                HostOs.Linux ->
                    Paths.get(env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() } ?: "$home/.local/share", APP_ID, "shared_preferences.json")
                HostOs.Windows ->
                    Paths.get(env["APPDATA"] ?: "$home\\AppData\\Roaming", "com.foxdevelops", "foxscreenshots", "shared_preferences.json")
                else -> return emptyMap()
            }
            val text = runCatching { Files.readString(path) }.getOrNull() ?: return emptyMap()
            return parseFlatJson(text)
                .filterKeys { it.startsWith("flutter.") }
                .mapKeys { it.key.removePrefix("flutter.") }
                .filterKeys { it in IMPORTED_KEYS }
        }

        private const val APP_ID = "com.foxdevelops.foxscreenshots"

        private val IMPORTED_KEYS = setOf(
            "theme_mode", "locale_tag", "capture_hotkey", "timer_hotkey", "repeat_hotkey",
            "timer_delay_seconds", "output_dir", "auto_save",
        )

        /** Parses `{"key": "text" | number | true | false, ...}`; nested values are not part of Flutter's file. */
        internal fun parseFlatJson(text: String): Map<String, String> {
            val entry = Regex(""""((?:[^"\\]|\\.)*)"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|(-?\d+(?:\.\d+)?|true|false))""")
            return entry.findAll(text).associate { match ->
                match.groupValues[1] to unescape(match.groups[2]?.value ?: match.groupValues[3])
            }
        }

        private fun unescape(raw: String): String = raw
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
    }
}
