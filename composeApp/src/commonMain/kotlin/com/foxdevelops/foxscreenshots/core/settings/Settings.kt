package com.foxdevelops.foxscreenshots.core.settings

import com.foxdevelops.foxscreenshots.core.storage.DEFAULT_FILE_NAME_PATTERN

enum class ThemePreference(val tag: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromTag(tag: String?): ThemePreference = entries.firstOrNull { it.tag == tag } ?: System
    }
}

/** UI language choice. [System] follows the OS, falling back to pt-BR. */
enum class LanguagePreference(val tag: String) {
    System("system"),
    Portuguese("pt"),
    English("en");

    companion object {
        fun fromTag(tag: String?): LanguagePreference = entries.firstOrNull { it.tag == tag } ?: System
    }
}

/** User-facing app preferences. Immutable. */
data class Settings(
    val theme: ThemePreference = ThemePreference.System,
    val language: LanguagePreference = LanguagePreference.System,
    val timerDelaySeconds: Int = DEFAULT_TIMER_DELAY_SECONDS,
    val hotkey: String = DEFAULT_HOTKEY,
    val timerHotkey: String = DEFAULT_TIMER_HOTKEY,
    val repeatHotkey: String = DEFAULT_REPEAT_HOTKEY,
    /** Folder auto-save writes into; `null` until the user picks one. */
    val outputDir: String? = null,
    val autoSave: Boolean = false,
    /** Name template for auto-saved files and the save dialog's suggestion; see `renderFileName`. */
    val fileNamePattern: String = DEFAULT_FILE_NAME_PATTERN,
    /** Folder of the last file saved through the dialog; the dialog opens here next time. */
    val lastSaveDir: String? = null,
) {
    companion object {
        const val MIN_TIMER_DELAY_SECONDS = 1
        const val MAX_TIMER_DELAY_SECONDS = 60
        const val DEFAULT_TIMER_DELAY_SECONDS = 3
        const val DEFAULT_HOTKEY = "PrintScreen"
        const val DEFAULT_TIMER_HOTKEY = "F7"
        const val DEFAULT_REPEAT_HOTKEY = "F6"
        const val MAX_FILE_NAME_PATTERN_LENGTH = 80

        /** Delays offered as one-click chips on the hub. */
        val QUICK_DELAYS_SECONDS = listOf(1, 3, 5, 10)

        /** Bindings offered in the settings screen. */
        val HOTKEY_OPTIONS = listOf(
            "PrintScreen", "F6", "F7", "F8", "F9", "F10", "F12", "Ctrl+Shift+S", "Ctrl+Shift+X",
        )
    }
}
