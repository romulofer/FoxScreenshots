import 'dart:ui';

/// An immutable rectangular region of the virtual screen, in physical pixels.
///
/// Used by the selection overlay and the capture backend. Origin is the
/// top-left of the combined multi-monitor virtual desktop.
class CaptureRegion {
  const CaptureRegion({
    required this.x,
    required this.y,
    required this.width,
    required this.height,
  });

  /// Builds a normalized region from two drag corners (any order).
  factory CaptureRegion.fromPoints(Offset a, Offset b) {
    final left = a.dx < b.dx ? a.dx : b.dx;
    final top = a.dy < b.dy ? a.dy : b.dy;
    return CaptureRegion(
      x: left.round(),
      y: top.round(),
      width: (a.dx - b.dx).abs().round(),
      height: (a.dy - b.dy).abs().round(),
    );
  }

  final int x;
  final int y;
  final int width;
  final int height;

  bool get isEmpty => width <= 0 || height <= 0;

  Rect toRect() => Rect.fromLTWH(
    x.toDouble(),
    y.toDouble(),
    width.toDouble(),
    height.toDouble(),
  );

  @override
  bool operator ==(Object other) =>
      other is CaptureRegion &&
      other.x == x &&
      other.y == y &&
      other.width == width &&
      other.height == height;

  @override
  int get hashCode => Object.hash(x, y, width, height);

  @override
  String toString() => 'CaptureRegion($x, $y, $width×$height)';
}
