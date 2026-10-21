package com.foxdevelops.foxscreenshots.features.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.toHotkeyBinding
import com.foxdevelops.foxscreenshots.core.settings.HotkeyRejection
import com.foxdevelops.foxscreenshots.core.settings.LanguagePreference
import com.foxdevelops.foxscreenshots.core.settings.Settings
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference
import com.foxdevelops.foxscreenshots.core.theme.foxSwitchColors
import com.foxdevelops.foxscreenshots.core.theme.foxTextFieldColors
import com.foxdevelops.foxscreenshots.ui.AppContainer
import com.foxdevelops.foxscreenshots.core.storage.renderFileName
import com.foxdevelops.foxscreenshots.ui.FoxIconButton
import com.foxdevelops.foxscreenshots.ui.FoxSlider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Settings: theme, language, timer delay, hotkeys, output folder. Changes persist and apply live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AppContainer, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val settings by app.settings.state.collectAsState()
    val controller = app.settings
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    app.navigator.back()
                    true
                } else {
                    false
                }
            },
    ) {
        TopAppBar(
            title = { Text(strings.settingsTitle) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            navigationIcon = {
                FoxIconButton(
                    Icons.AutoMirrored.Outlined.ArrowBack, strings.actionClose, shortcut = "Esc",
                    onClick = { app.navigator.back() },
                )
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            SectionHeader(strings.settingsSectionAppearance)
            SettingRow(strings.settingsTheme) {
                Dropdown(
                    options = ThemePreference.entries,
                    selected = settings.theme,
                    label = {
                        when (it) {
                            ThemePreference.System -> strings.settingsThemeSystem
                            ThemePreference.Light -> strings.settingsThemeLight
                            ThemePreference.Dark -> strings.settingsThemeDark
                        }
                    },
                    onSelect = controller::setTheme,
                )
            }
            SettingRow(strings.settingsLanguage) {
                Dropdown(
                    options = LanguagePreference.entries,
                    selected = settings.language,
                    label = {
                        when (it) {
                            LanguagePreference.System -> strings.settingsLanguageSystem
                            LanguagePreference.Portuguese -> strings.settingsLanguagePt
                            LanguagePreference.English -> strings.settingsLanguageEn
                        }
                    },
                    onSelect = controller::setLanguage,
                )
            }

            SectionHeader(strings.settingsSectionCapture)
            SettingRow(strings.settingsCaptureDelay) {
                Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoxSlider(
                        value = settings.timerDelaySeconds.toFloat(),
                        onValueChange = { controller.setTimerDelaySeconds(it.roundToInt()) },
                        valueRange = Settings.MIN_TIMER_DELAY_SECONDS.toFloat()..Settings.MAX_TIMER_DELAY_SECONDS.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        settings.timerDelaySeconds.toString(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(28.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            ListItem(
                leadingContent = { Icon(Icons.Outlined.CropFree, contentDescription = null) },
                headlineContent = { Text(strings.settingsSelectionConfirm) },
                supportingContent = { Text(strings.settingsSelectionConfirmSubtitle) },
                trailingContent = {
                    Switch(
                        colors = foxSwitchColors(),
                        checked = settings.selectionConfirm,
                        onCheckedChange = controller::setSelectionConfirm,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HotkeyRow(app, strings.settingsHotkey, settings.hotkey, HotkeyAction.Instant)
            HotkeyRow(app, strings.settingsHotkeyTimer, settings.timerHotkey, HotkeyAction.Timer)
            HotkeyRow(app, strings.settingsHotkeyRepeat, settings.repeatHotkey, HotkeyAction.Repeat)

            SectionHeader(strings.settingsSectionOutput)
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                headlineContent = { Text(strings.settingsOutputFolder) },
                supportingContent = { Text(settings.outputDir ?: strings.settingsOutputFolderNone) },
                trailingContent = {
                    TextButton(onClick = {
                        scope.launch { app.output.pickDirectory(settings.outputDir)?.let(controller::setOutputDir) }
                    }) { Text(strings.actionChange) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            FileNameRow(settings.fileNamePattern, controller::setFileNamePattern)
            ListItem(
                leadingContent = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                headlineContent = { Text(strings.settingsAutoSave) },
                supportingContent = { Text(strings.settingsAutoSaveSubtitle) },
                trailingContent = {
                    Switch(
                        colors = foxSwitchColors(),
                        checked = settings.autoSave,
                        onCheckedChange = { on ->
                            scope.launch {
                                // No target folder means nothing to write into; guide the user to pick one first
                                // rather than flipping a toggle that does nothing.
                                if (on && settings.outputDir == null) {
                                    val dir = app.output.pickDirectory() ?: return@launch
                                    controller.setOutputDir(dir)
                                }
                                controller.setAutoSave(on)
                            }
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            Spacer(Modifier.height(24.dp))
            if (app.appVersion.isNotEmpty()) {
                Text(
                    strings.settingsVersion(app.appVersion),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A hotkey row: pick a preset, or press "Record" and then the combination you want. A clash with another
 * capture hotkey, or a combination that cannot be global, is refused with the reason.
 */
@Composable
private fun HotkeyRow(app: AppContainer, title: String, current: String, action: HotkeyAction) {
    val strings = LocalStrings.current
    var recording by remember { mutableStateOf(false) }
    // A stored binding the list no longer offers still shows up, instead of silently snapping to another key.
    val options = if (current in Settings.HOTKEY_OPTIONS) Settings.HOTKEY_OPTIONS else Settings.HOTKEY_OPTIONS + current

    fun bind(label: String) {
        when (val rejection = app.settings.bindHotkey(action, label)) {
            null -> Unit
            HotkeyRejection.NotAllowed -> app.messenger.show(strings.hotkeyNotAllowed)
            is HotkeyRejection.InUse -> app.messenger.show(
                strings.hotkeyInUse(
                    when (rejection.by) {
                        HotkeyAction.Instant -> strings.settingsHotkey
                        HotkeyAction.Timer -> strings.settingsHotkeyTimer
                        HotkeyAction.Repeat, HotkeyAction.Cancel -> strings.settingsHotkeyRepeat
                    },
                ),
            )
        }
    }

    SettingRow(title) {
        if (recording) {
            HotkeyRecorder(
                onRecorded = { label ->
                    recording = false
                    bind(label)
                },
                onCancel = { recording = false },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dropdown(options, current, label = { it }, onSelect = ::bind)
                TextButton(onClick = { recording = true }) { Text(strings.settingsHotkeyRecord) }
            }
        }
    }
}

/**
 * Waits, focused, for the next key press and hands it back as a hotkey label. Esc, the Cancel button or
 * clicking elsewhere cancels (Esc is reserved, so it cannot be recorded).
 */
@Composable
private fun HotkeyRecorder(onRecorded: (String) -> Unit, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focus)
                .onFocusChanged { state ->
                    // Losing the focus (a click elsewhere) abandons the recording; the first, not-yet-focused callback is not a loss.
                    if (state.isFocused) wasFocused = true else if (wasFocused) onCancel()
                }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.Escape) {
                        onCancel()
                        return@onPreviewKeyEvent true
                    }
                    // A lone modifier is not a combination yet: keep waiting.
                    val binding = event.toHotkeyBinding() ?: return@onPreviewKeyEvent true
                    onRecorded(binding.toLabel())
                    true
                },
        ) { Text(strings.hotkeyRecordHint, style = MaterialTheme.typography.bodyMedium) }
        TextButton(onClick = onCancel) { Text(strings.actionCancel) }
    }
}

@Composable
private fun SettingRow(title: String, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun <T> Dropdown(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** A group label above a run of related settings rows. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Edits the file name template. Commits on every keystroke; a blank field resets to the default once it is read back. */
@Composable
private fun FileNameRow(pattern: String, onChange: (String) -> Unit) {
    val strings = LocalStrings.current
    var text by remember(pattern) { mutableStateOf(pattern) }
    ListItem(
        headlineContent = { Text(strings.settingsFileNamePattern) },
        supportingContent = {
            Column {
                Text(strings.settingsFileNamePatternHint)
                Text(
                    strings.settingsFileNamePreview(renderFileName(text.ifBlank { pattern }, 2026, 8, 13, 8, 42, 5)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.take(Settings.MAX_FILE_NAME_PATTERN_LENGTH)
                    if (text.isNotBlank()) onChange(text)
                },
                singleLine = true,
                colors = foxTextFieldColors(),
                suffix = { Text(".png") },
                modifier = Modifier.width(280.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
