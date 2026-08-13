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
        padding: const EdgeInsets.all(16),
        children: [
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
          ListTile(
            title: Text(l10n.settingsCaptureDelay),
            trailing: SizedBox(
              width: 160,
              child: Slider(
                value: settings.timerDelaySeconds.toDouble(),
                min: 1,
                max: 15,
                divisions: 14,
                label: '${settings.timerDelaySeconds}',
                onChanged: (v) => controller.setTimerDelaySeconds(v.round()),
              ),
            ),
          ),
          ListTile(
            title: Text(l10n.settingsHotkey),
            trailing: Text(settings.hotkey),
          ),
        ],
      ),
    );
  }
}
