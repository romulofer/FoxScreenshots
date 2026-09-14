import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Builds the light and dark [ThemeData] from the [FoxColors] tokens.
///
/// Both themes are derived from the same token set so the UI never references
/// raw colors (SPEC §5). Follows the OS theme by default; overridable in
/// Settings via the app's `ThemeMode`.
abstract final class AppTheme {
  // Built once, lazily. `ColorScheme.fromSeed` runs the HCT tonal-palette
  // computation; the tokens never change at runtime, so rebuilding the same
  // ThemeData on every MaterialApp build (i.e. every settings change) is pure
  // waste. Cached here instead.
  static final ThemeData _light = _build(Brightness.light, FoxColors.light);
  static final ThemeData _dark = _build(Brightness.dark, FoxColors.dark);

  static ThemeData light() => _light;

  static ThemeData dark() => _dark;

  static ThemeData _build(Brightness brightness, FoxColors c) {
    final scheme = ColorScheme.fromSeed(
      seedColor: c.brand,
      brightness: brightness,
      primary: c.brand,
      secondary: c.accent,
      surface: c.surface,
    ).copyWith(onSurface: c.textPrimary);

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: c.appBackground,
      extensions: <ThemeExtension<dynamic>>[c],
      appBarTheme: AppBarTheme(
        backgroundColor: c.appBackground,
        foregroundColor: c.textPrimary,
        elevation: 0,
      ),
      textTheme: Typography.material2021(platform: TargetPlatform.linux).black
          .apply(bodyColor: c.textPrimary, displayColor: c.textPrimary),
    );
  }
}
