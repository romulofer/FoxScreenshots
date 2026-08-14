import 'dart:ui';

/// Ink colors offered by the editor.
///
/// These are *content*, not chrome: they end up baked into the exported PNG, so
/// unlike UI colors they must not follow the light/dark theme — a red arrow has
/// to stay the same red in the file the user shares. Kept vivid and mutually
/// distinguishable over busy screenshots.
abstract final class AnnotationPalette {
  static const Color red = Color(0xFFE53935);
  static const Color orange = Color(0xFFD9531E);
  static const Color yellow = Color(0xFFFFC107);
  static const Color green = Color(0xFF43A047);
  static const Color blue = Color(0xFF1E88E5);
  static const Color black = Color(0xFF111111);
  static const Color white = Color(0xFFFFFFFF);

  static const List<Color> colors = [
    red,
    orange,
    yellow,
    green,
    blue,
    black,
    white,
  ];

  /// Stroke widths in image pixels, from hairline to marker.
  static const double minStrokeWidth = 1;
  static const double maxStrokeWidth = 24;
}
