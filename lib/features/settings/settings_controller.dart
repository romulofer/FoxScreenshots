import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../core/storage/settings_service.dart';

/// App version string (e.g. "0.3.3"), read from the platform package info.
final appVersionProvider = FutureProvider<String>((ref) async {
  final info = await PackageInfo.fromPlatform();
  return info.version;
});

/// User-facing app preferences (SPEC §2.6, §2.7). Immutable.
@immutable
class SettingsState {
  const SettingsState({
    required this.themeMode,
    required this.locale,
    required this.timerDelaySeconds,
    required this.hotkey,
  });

  /// `null` [locale] means "follow the OS" (falling back to pt-BR).
  final ThemeMode themeMode;
  final Locale? locale;
  final int timerDelaySeconds;
  final String hotkey;

  SettingsState copyWith({
    ThemeMode? themeMode,
    Locale? locale,
    bool clearLocale = false,
    int? timerDelaySeconds,
    String? hotkey,
  }) {
    return SettingsState(
      themeMode: themeMode ?? this.themeMode,
      locale: clearLocale ? null : (locale ?? this.locale),
      timerDelaySeconds: timerDelaySeconds ?? this.timerDelaySeconds,
      hotkey: hotkey ?? this.hotkey,
    );
  }
}

/// Loads persisted settings and writes changes back through [SettingsService].
class SettingsController extends Notifier<SettingsState> {
  @override
  SettingsState build() {
    final s = ref.watch(settingsServiceProvider);
    return SettingsState(
      themeMode: _themeModeFromString(s.themeMode),
      locale: _localeFromTag(s.localeTag),
      timerDelaySeconds: s.timerDelaySeconds,
      hotkey: s.hotkey,
    );
  }

  SettingsService get _service => ref.read(settingsServiceProvider);

  Future<void> setThemeMode(ThemeMode mode) async {
    await _service.setThemeMode(mode.name);
    state = state.copyWith(themeMode: mode);
  }

  Future<void> setLocale(Locale? locale) async {
    await _service.setLocaleTag(locale?.languageCode ?? 'system');
    state = state.copyWith(locale: locale, clearLocale: locale == null);
  }

  Future<void> setTimerDelaySeconds(int seconds) async {
    final clamped = seconds.clamp(
      SettingsService.minTimerDelaySeconds,
      SettingsService.maxTimerDelaySeconds,
    );
    await _service.setTimerDelaySeconds(clamped);
    state = state.copyWith(timerDelaySeconds: clamped);
  }

  Future<void> setHotkey(String hotkey) async {
    await _service.setHotkey(hotkey);
    state = state.copyWith(hotkey: hotkey);
  }

  static ThemeMode _themeModeFromString(String value) => switch (value) {
    'light' => ThemeMode.light,
    'dark' => ThemeMode.dark,
    _ => ThemeMode.system,
  };

  static Locale? _localeFromTag(String tag) => switch (tag) {
    'pt' => const Locale('pt'),
    'en' => const Locale('en'),
    _ => null,
  };
}

final settingsControllerProvider =
    NotifierProvider<SettingsController, SettingsState>(SettingsController.new);
