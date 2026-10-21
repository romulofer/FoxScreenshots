package com.foxdevelops.foxscreenshots.core.settings

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.storage.DEFAULT_FILE_NAME_PATTERN
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
        fileNamePattern = store.getString(KEY_FILE_NAME_PATTERN)?.takeIf { it.isNotBlank() } ?: DEFAULT_FILE_NAME_PATTERN,
        lastSaveDir = store.getString(KEY_LAST_SAVE_DIR),
        selectionConfirm = store.getString(KEY_SELECTION_CONFIRM) != "false",
        dismissedIssues = store.getString(KEY_DISMISSED_ISSUES)?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
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

    /** Blank patterns reset to the default; overlong ones are cut. */
    fun setFileNamePattern(pattern: String) {
        val cleaned = pattern.trim().take(Settings.MAX_FILE_NAME_PATTERN_LENGTH).ifEmpty { DEFAULT_FILE_NAME_PATTERN }
        store.putString(KEY_FILE_NAME_PATTERN, cleaned)
        mutableState.update { it.copy(fileNamePattern = cleaned) }
    }

    fun setLastSaveDir(dir: String) {
        store.putString(KEY_LAST_SAVE_DIR, dir)
        mutableState.update { it.copy(lastSaveDir = dir) }
    }

    fun setSelectionConfirm(enabled: Boolean) {
        store.putString(KEY_SELECTION_CONFIRM, enabled.toString())
        mutableState.update { it.copy(selectionConfirm = enabled) }
    }

    /** Remembers that the user does not want to see these issues announced again. */
    fun dismissIssues(names: Set<String>) {
        val all = mutableState.value.dismissedIssues + names
        store.putString(KEY_DISMISSED_ISSUES, all.sorted().joinToString(","))
        mutableState.update { it.copy(dismissedIssues = all) }
    }

    /**
     * Binds [action] to [label] unless another action already uses the same key
     * press or the binding cannot be a global hotkey. Returns `null` when the
     * binding was taken, otherwise why not.
     */
    fun bindHotkey(action: HotkeyAction, label: String): HotkeyRejection? {
        val binding = HotkeyBinding.parse(label)
        if (!binding.isAllowedAsCaptureHotkey) return HotkeyRejection.NotAllowed
        val current = mutableState.value
        val others = mapOf(
            HotkeyAction.Instant to current.hotkey,
            HotkeyAction.Timer to current.timerHotkey,
            HotkeyAction.Repeat to current.repeatHotkey,
        ) - action
        val clash = others.entries.firstOrNull { HotkeyBinding.parse(it.value) == binding }
        if (clash != null) return HotkeyRejection.InUse(clash.key)
        when (action) {
            HotkeyAction.Instant -> setHotkey(label)
            HotkeyAction.Timer -> setTimerHotkey(label)
            HotkeyAction.Repeat -> setRepeatHotkey(label)
            HotkeyAction.Cancel -> return HotkeyRejection.NotAllowed
        }
        return null
    }

    private companion object {
        const val KEY_SELECTION_CONFIRM = "selection_confirm"
        const val KEY_DISMISSED_ISSUES = "dismissed_issues"
        const val KEY_FILE_NAME_PATTERN = "file_name_pattern"
        const val KEY_LAST_SAVE_DIR = "last_save_dir"
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

/** Why a hotkey change was refused. */
sealed interface HotkeyRejection {
    /** Another capture action already uses this key press. */
    data class InUse(val by: HotkeyAction) : HotkeyRejection

    /** A bare letter or Escape: it would fire while typing or clash with every dialog. */
    data object NotAllowed : HotkeyRejection
}
