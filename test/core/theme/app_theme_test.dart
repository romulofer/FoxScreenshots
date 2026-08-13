import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/theme/app_colors.dart';
import 'package:foxscreenshots/core/theme/app_theme.dart';

void main() {
  group('AppTheme', () {
    test('light theme exposes the light FoxColors tokens', () {
      final theme = AppTheme.light();
      final tokens = theme.extension<FoxColors>();

      expect(theme.brightness, Brightness.light);
      expect(tokens, isNotNull);
      // Light brand is the darker #A63F10 for 4.5:1 contrast (SPEC §2.7).
      expect(tokens!.brand, const Color(0xFFA63F10));
      expect(tokens.appBackground, const Color(0xFFFFFCF9));
    });

    test('dark theme exposes the dark FoxColors tokens', () {
      final theme = AppTheme.dark();
      final tokens = theme.extension<FoxColors>();

      expect(theme.brightness, Brightness.dark);
      expect(tokens!.brand, const Color(0xFFD9531E));
      expect(tokens.appBackground, const Color(0xFF000000));
    });
  });
}
