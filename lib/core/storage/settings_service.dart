import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persisted user preferences, backed by [SharedPreferences] (local only).
///
/// Thin, testable wrapper: no UI, no Flutter imports. The settings controller
/// (SPEC §4 `settings_controller.dart`) reads/writes through this.
class SettingsService {
  SettingsService(this._prefs);

  final SharedPreferences _prefs;

  static const _kThemeMode = 'theme_mode'; // system | light | dark
  static const _kLocale = 'locale_tag'; // system | pt | en
  static const _kHotkey = 'capture_hotkey';
  static const _kDelaySeconds = 'timer_delay_seconds';
  static const _kOutputDir = 'output_dir';

  String get themeMode => _prefs.getString(_kThemeMode) ?? 'system';
  Future<void> setThemeMode(String value) =>
      _prefs.setString(_kThemeMode, value);

  String get localeTag => _prefs.getString(_kLocale) ?? 'system';
  Future<void> setLocaleTag(String value) => _prefs.setString(_kLocale, value);

  String get hotkey => _prefs.getString(_kHotkey) ?? 'PrintScreen';
  Future<void> setHotkey(String value) => _prefs.setString(_kHotkey, value);

  int get timerDelaySeconds => _prefs.getInt(_kDelaySeconds) ?? 3;
  Future<void> setTimerDelaySeconds(int value) =>
      _prefs.setInt(_kDelaySeconds, value);

  String? get outputDir => _prefs.getString(_kOutputDir);
  Future<void> setOutputDir(String value) =>
      _prefs.setString(_kOutputDir, value);
}

/// Bound to a concrete [SharedPreferences] in `main()` via [overrideWithValue].
final settingsServiceProvider = Provider<SettingsService>((ref) {
  throw UnimplementedError(
    'settingsServiceProvider must be overridden in main',
  );
});
