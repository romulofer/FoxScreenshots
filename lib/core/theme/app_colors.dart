import 'package:flutter/material.dart';

/// Brand color tokens ported from `~/development/mobile/foxdevelops`
/// (`values/colors.xml` + `values-night`). See SPEC §2.7.
///
/// Registered as a [ThemeExtension] so widgets read tokens via
/// `Theme.of(context).extension<FoxColors>()`. Never reference raw hex outside
/// this file.
@immutable
class FoxColors extends ThemeExtension<FoxColors> {
  const FoxColors({
    required this.appBackground,
    required this.surface,
    required this.textPrimary,
    required this.textSecondary,
    required this.brand,
    required this.accent,
  });

  final Color appBackground;
  final Color surface;
  final Color textPrimary;
  final Color textSecondary;

  /// Primary brand color. On light backgrounds this is the *darker* `#A63F10`
  /// (the orange `#D9531E` fails 4.5:1 on white). SPEC §2.7.
  final Color brand;
  final Color accent;

  static const FoxColors light = FoxColors(
    appBackground: Color(0xFFFFFCF9),
    surface: Color(0xFFF2EBE5),
    textPrimary: Color(0xFF1B1411),
    textSecondary: Color(0xFF6A5D56),
    brand: Color(0xFFA63F10),
    accent: Color(0xFFA65A00),
  );

  static const FoxColors dark = FoxColors(
    appBackground: Color(0xFF000000),
    surface: Color(0xFF241C18),
    textPrimary: Color(0xFFFFFFFF),
    textSecondary: Color(0xFFB8ADA6),
    brand: Color(0xFFD9531E),
    accent: Color(0xFFFFB74D),
  );

  @override
  FoxColors copyWith({
    Color? appBackground,
    Color? surface,
    Color? textPrimary,
    Color? textSecondary,
    Color? brand,
    Color? accent,
  }) {
    return FoxColors(
      appBackground: appBackground ?? this.appBackground,
      surface: surface ?? this.surface,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      brand: brand ?? this.brand,
      accent: accent ?? this.accent,
    );
  }

  @override
  FoxColors lerp(covariant ThemeExtension<FoxColors>? other, double t) {
    if (other is! FoxColors) return this;
    return FoxColors(
      appBackground: Color.lerp(appBackground, other.appBackground, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      brand: Color.lerp(brand, other.brand, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
    );
  }
}
