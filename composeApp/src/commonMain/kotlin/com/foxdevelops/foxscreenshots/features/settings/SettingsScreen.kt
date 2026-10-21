package com.foxdevelops.foxscreenshots.features.settings

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
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.settings.LanguagePreference
import com.foxdevelops.foxscreenshots.core.settings.Settings
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference
import com.foxdevelops.foxscreenshots.ui.AppContainer
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

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(strings.settingsTitle) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            navigationIcon = {
                IconButton(onClick = { app.navigator.back() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = strings.actionClose)
                }
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
                        valueRange = 1f..15f,
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
            HotkeyRow(strings.settingsHotkey, settings.hotkey, controller::setHotkey)
            HotkeyRow(strings.settingsHotkeyTimer, settings.timerHotkey, controller::setTimerHotkey)
            HotkeyRow(strings.settingsHotkeyRepeat, settings.repeatHotkey, controller::setRepeatHotkey)

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

@Composable
private fun HotkeyRow(title: String, current: String, onSelect: (String) -> Unit) {
    // A stored binding the list no longer offers still shows up, instead of silently snapping to another key.
    val options = if (current in Settings.HOTKEY_OPTIONS) Settings.HOTKEY_OPTIONS else Settings.HOTKEY_OPTIONS + current
    SettingRow(title) { Dropdown(options, current, label = { it }, onSelect = onSelect) }
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
        supportingContent = { Text(strings.settingsFileNamePatternHint) },
        trailingContent = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.take(Settings.MAX_FILE_NAME_PATTERN_LENGTH)
                    if (text.isNotBlank()) onChange(text)
                },
                singleLine = true,
                suffix = { Text(".png") },
                modifier = Modifier.width(280.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
