import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/l10n/gen/app_localizations.dart';
import 'settings_controller.dart';

/// Settings screen (SPEC §2.7, §2.6): theme, language, timer delay, hotkey,
/// output folder. Changes persist and apply live.
///
/// Each row watches only the field it draws, via `.select` — dragging the
/// timer-delay slider used to rebuild every row in this screen on every drag
/// frame because a single top-level `watch` pulled in the whole
/// [SettingsState].
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.settingsTitle)),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          _SectionHeader(l10n.settingsSectionAppearance),
          const _ThemeRow(),
          const _LanguageRow(),
          _SectionHeader(l10n.settingsSectionCapture),
          const _TimerDelayRow(),
          const _HotkeyRow(),
          _SectionHeader(l10n.settingsSectionOutput),
          const _OutputFolderRow(),
          const _AutoSaveRow(),
          const SizedBox(height: 24),
          const _VersionFooter(),
        ],
      ),
    );
  }
}

class _ThemeRow extends ConsumerWidget {
  const _ThemeRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final themeMode = ref.watch(
      settingsControllerProvider.select((s) => s.themeMode),
    );
    final controller = ref.read(settingsControllerProvider.notifier);
    return ListTile(
      title: Text(l10n.settingsTheme),
      trailing: DropdownButton<ThemeMode>(
        value: themeMode,
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
    );
  }
}

class _LanguageRow extends ConsumerWidget {
  const _LanguageRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final languageCode = ref.watch(
      settingsControllerProvider.select((s) => s.locale?.languageCode),
    );
    final controller = ref.read(settingsControllerProvider.notifier);
    return ListTile(
      title: Text(l10n.settingsLanguage),
      trailing: DropdownButton<String>(
        value: languageCode ?? 'system',
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
          DropdownMenuItem(value: 'pt', child: Text(l10n.settingsLanguagePt)),
          DropdownMenuItem(value: 'en', child: Text(l10n.settingsLanguageEn)),
        ],
      ),
    );
  }
}

class _TimerDelayRow extends ConsumerWidget {
  const _TimerDelayRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final seconds = ref.watch(
      settingsControllerProvider.select((s) => s.timerDelaySeconds),
    );
    final controller = ref.read(settingsControllerProvider.notifier);
    return ListTile(
      title: Text(l10n.settingsCaptureDelay),
      trailing: SizedBox(
        width: 200,
        child: Row(
          children: [
            Expanded(
              child: Slider(
                value: seconds.toDouble(),
                min: 1,
                max: 15,
                divisions: 14,
                label: '$seconds',
                onChanged: (v) => controller.setTimerDelaySeconds(v.round()),
              ),
            ),
            SizedBox(
              width: 28,
              child: Text(
                '$seconds',
                textAlign: TextAlign.end,
                style: Theme.of(context).textTheme.labelLarge,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _HotkeyRow extends ConsumerWidget {
  const _HotkeyRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final hotkey = ref.watch(
      settingsControllerProvider.select((s) => s.hotkey),
    );
    final controller = ref.read(settingsControllerProvider.notifier);
    return ListTile(
      title: Text(l10n.settingsHotkey),
      trailing: DropdownButton<String>(
        value: _hotkeyOptions.contains(hotkey) ? hotkey : 'PrintScreen',
        onChanged: (value) {
          if (value != null) controller.setHotkey(value);
        },
        items: [
          for (final option in _hotkeyOptions)
            DropdownMenuItem(value: option, child: Text(option)),
        ],
      ),
    );
  }
}

class _OutputFolderRow extends ConsumerWidget {
  const _OutputFolderRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final outputDir = ref.watch(
      settingsControllerProvider.select((s) => s.outputDir),
    );
    final controller = ref.read(settingsControllerProvider.notifier);
    return ListTile(
      leading: const Icon(Icons.folder_outlined),
      title: Text(l10n.settingsOutputFolder),
      subtitle: Text(outputDir ?? l10n.settingsOutputFolderNone),
      trailing: TextButton(
        onPressed: () => _pickOutputDir(controller),
        child: Text(l10n.actionChange),
      ),
    );
  }

  Future<void> _pickOutputDir(SettingsController controller) async {
    final dir = await getDirectoryPath();
    if (dir != null) await controller.setOutputDir(dir);
  }
}

class _AutoSaveRow extends ConsumerWidget {
  const _AutoSaveRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final autoSave = ref.watch(
      settingsControllerProvider.select((s) => s.autoSave),
    );
    final controller = ref.read(settingsControllerProvider.notifier);
    return SwitchListTile(
      secondary: const Icon(Icons.save_alt_outlined),
      title: Text(l10n.settingsAutoSave),
      subtitle: Text(l10n.settingsAutoSaveSubtitle),
      value: autoSave,
      // No target folder means nothing to write into; guide the user to
      // pick one first rather than flipping a toggle that no-ops.
      onChanged: (on) async {
        var dir = ref.read(settingsControllerProvider).outputDir;
        if (on && dir == null) {
          dir = await getDirectoryPath();
          if (dir == null) return;
          await controller.setOutputDir(dir);
        }
        await controller.setAutoSave(on);
      },
    );
  }
}

class _VersionFooter extends ConsumerWidget {
  const _VersionFooter();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final version = ref.watch(appVersionProvider);
    return Center(
      child: Text(
        version.when(
          data: (v) => l10n.settingsVersion(v),
          loading: () => '',
          error: (_, _) => '',
        ),
        style: Theme.of(context).textTheme.bodySmall
            ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
      ),
    );
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
