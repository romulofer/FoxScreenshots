package com.foxdevelops.foxscreenshots.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Loads persisted settings and writes changes back through [SettingsStore]. */
class SettingsController(private val store: SettingsStore) {

    private val mutableState = MutableStateFlow(load())
    val state: StateFlow<Settings> = mutableState.asStateFlow()

    private fun load(): Settings = Settings(
        theme = ThemePreference.fromTag(store.getString(KEY_THEME)),
        language = LanguagePreference.fromTag(store.getString(KEY_LOCALE)),
        timerDelaySeconds = (store.getString(KEY_DELAY)?.toIntOrNull() ?: Settings.DEFAULT_TIMER_DELAY_SECONDS)
            .coerceIn(Settings.MIN_TIMER_DELAY_SECONDS, Settings.MAX_TIMER_DELAY_SECONDS),
        hotkey = store.getString(KEY_HOTKEY) ?: Settings.DEFAULT_HOTKEY,
        timerHotkey = store.getString(KEY_TIMER_HOTKEY) ?: Settings.DEFAULT_TIMER_HOTKEY,
        repeatHotkey = store.getString(KEY_REPEAT_HOTKEY) ?: Settings.DEFAULT_REPEAT_HOTKEY,
        outputDir = store.getString(KEY_OUTPUT_DIR),
        autoSave = store.getString(KEY_AUTO_SAVE) == "true",
    )

    fun setTheme(theme: ThemePreference) {
        store.putString(KEY_THEME, theme.tag)
        mutableState.update { it.copy(theme = theme) }
    }

    fun setLanguage(language: LanguagePreference) {
        store.putString(KEY_LOCALE, language.tag)
        mutableState.update { it.copy(language = language) }
    }

    fun setTimerDelaySeconds(seconds: Int) {
        val clamped = seconds.coerceIn(Settings.MIN_TIMER_DELAY_SECONDS, Settings.MAX_TIMER_DELAY_SECONDS)
        store.putString(KEY_DELAY, clamped.toString())
        mutableState.update { it.copy(timerDelaySeconds = clamped) }
    }

    fun setHotkey(binding: String) {
        store.putString(KEY_HOTKEY, binding)
        mutableState.update { it.copy(hotkey = binding) }
    }

    fun setTimerHotkey(binding: String) {
        store.putString(KEY_TIMER_HOTKEY, binding)
        mutableState.update { it.copy(timerHotkey = binding) }
    }

    fun setRepeatHotkey(binding: String) {
        store.putString(KEY_REPEAT_HOTKEY, binding)
        mutableState.update { it.copy(repeatHotkey = binding) }
    }

    fun setOutputDir(dir: String) {
        store.putString(KEY_OUTPUT_DIR, dir)
        mutableState.update { it.copy(outputDir = dir) }
    }

    /** Turning auto-save on without an output folder is a no-op: the toggle stays off until a folder exists. */
    fun setAutoSave(enabled: Boolean) {
        if (enabled && mutableState.value.outputDir == null) return
        store.putString(KEY_AUTO_SAVE, enabled.toString())
        mutableState.update { it.copy(autoSave = enabled) }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LOCALE = "locale_tag"
        const val KEY_HOTKEY = "capture_hotkey"
        const val KEY_TIMER_HOTKEY = "timer_hotkey"
        const val KEY_REPEAT_HOTKEY = "repeat_hotkey"
        const val KEY_DELAY = "timer_delay_seconds"
        const val KEY_OUTPUT_DIR = "output_dir"
        const val KEY_AUTO_SAVE = "auto_save"
    }
}
