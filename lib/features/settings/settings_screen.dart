import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/l10n/gen/app_localizations.dart';
import 'settings_controller.dart';

/// Settings screen (SPEC §2.7, §2.6): theme, language, timer delay, hotkey,
/// output folder. Changes persist and apply live.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final settings = ref.watch(settingsControllerProvider);
    final controller = ref.read(settingsControllerProvider.notifier);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.settingsTitle)),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          _SectionHeader(l10n.settingsSectionAppearance),
          ListTile(
            title: Text(l10n.settingsTheme),
            trailing: DropdownButton<ThemeMode>(
              value: settings.themeMode,
              onChanged: (m) => m == null ? null : controller.setThemeMode(m),
              items: [
                DropdownMenuItem(
                  value: ThemeMode.system,
                  child: Text(l10n.settingsThemeSystem),
                ),
                DropdownMenuItem(
                  value: ThemeMode.light,
                  child: Text(l10n.settingsThemeLight),
                ),
                DropdownMenuItem(
                  value: ThemeMode.dark,
                  child: Text(l10n.settingsThemeDark),
                ),
              ],
            ),
          ),
          ListTile(
            title: Text(l10n.settingsLanguage),
            trailing: DropdownButton<String>(
              value: settings.locale?.languageCode ?? 'system',
              onChanged: (tag) => controller.setLocale(switch (tag) {
                'pt' => const Locale('pt'),
                'en' => const Locale('en'),
                _ => null,
              }),
              items: [
                DropdownMenuItem(
                  value: 'system',
                  child: Text(l10n.settingsLanguageSystem),
                ),
                DropdownMenuItem(
                  value: 'pt',
                  child: Text(l10n.settingsLanguagePt),
                ),
                DropdownMenuItem(
                  value: 'en',
                  child: Text(l10n.settingsLanguageEn),
                ),
              ],
            ),
          ),
          _SectionHeader(l10n.settingsSectionCapture),
          ListTile(
            title: Text(l10n.settingsCaptureDelay),
            trailing: SizedBox(
              width: 200,
              child: Row(
                children: [
                  Expanded(
                    child: Slider(
                      value: settings.timerDelaySeconds.toDouble(),
                      min: 1,
                      max: 15,
                      divisions: 14,
                      label: '${settings.timerDelaySeconds}',
                      onChanged: (v) =>
                          controller.setTimerDelaySeconds(v.round()),
                    ),
                  ),
                  SizedBox(
                    width: 28,
                    child: Text(
                      '${settings.timerDelaySeconds}',
                      textAlign: TextAlign.end,
                      style: Theme.of(context).textTheme.labelLarge,
                    ),
                  ),
                ],
              ),
            ),
          ),
          ListTile(
            title: Text(l10n.settingsHotkey),
            trailing: DropdownButton<String>(
              value: _hotkeyOptions.contains(settings.hotkey)
                  ? settings.hotkey
                  : 'PrintScreen',
              onChanged: (value) {
                if (value != null) controller.setHotkey(value);
              },
              items: [
                for (final option in _hotkeyOptions)
                  DropdownMenuItem(value: option, child: Text(option)),
              ],
            ),
          ),
          _SectionHeader(l10n.settingsSectionOutput),
          ListTile(
            leading: const Icon(Icons.folder_outlined),
            title: Text(l10n.settingsOutputFolder),
            subtitle: Text(settings.outputDir ?? l10n.settingsOutputFolderNone),
            trailing: TextButton(
              onPressed: () => _pickOutputDir(controller),
              child: Text(l10n.actionChange),
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.save_alt_outlined),
            title: Text(l10n.settingsAutoSave),
            subtitle: Text(l10n.settingsAutoSaveSubtitle),
            value: settings.autoSave,
            // No target folder means nothing to write into; guide the user to
            // pick one first rather than flipping a toggle that no-ops.
            onChanged: (on) async {
              var dir = settings.outputDir;
              if (on && dir == null) {
                dir = await getDirectoryPath();
                if (dir == null) return;
                await controller.setOutputDir(dir);
              }
              await controller.setAutoSave(on);
            },
          ),
          const SizedBox(height: 24),
          Consumer(
            builder: (context, ref, _) {
              final version = ref.watch(appVersionProvider);
              return Center(
                child: Text(
                  version.when(
                    data: (v) => l10n.settingsVersion(v),
                    loading: () => '',
                    error: (_, _) => '',
                  ),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Future<void> _pickOutputDir(SettingsController controller) async {
    final dir = await getDirectoryPath();
    if (dir != null) await controller.setOutputDir(dir);
  }
}

/// A group label above a run of related settings rows.
class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        title,
        style: theme.textTheme.labelLarge?.copyWith(
          color: theme.colorScheme.primary,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

const _hotkeyOptions = <String>[
  'PrintScreen',
  'F8',
  'F9',
  'F10',
  'F12',
  'Ctrl+Shift+S',
  'Ctrl+Shift+X',
];
