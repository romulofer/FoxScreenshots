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

  /// Clips this region to a `bounds` sized image anchored at the origin.
  ///
  /// A selection dragged past the edge of the frozen screenshot would make the
  /// capture backend read out of bounds, so every crop goes through this first.
  /// Returns an empty region when there is no overlap.
  CaptureRegion clampedTo(int boundsWidth, int boundsHeight) {
    final left = x < 0 ? 0 : x;
    final top = y < 0 ? 0 : y;
    final right = (x + width) > boundsWidth ? boundsWidth : x + width;
    final bottom = (y + height) > boundsHeight ? boundsHeight : y + height;
    return CaptureRegion(
      x: left,
      y: top,
      width: right - left < 0 ? 0 : right - left,
      height: bottom - top < 0 ? 0 : bottom - top,
    );
  }

  /// Scales this region by [factor], for converting between the overlay's
  /// logical pixels and the screenshot's physical pixels.
  CaptureRegion scaled(double factor) => CaptureRegion(
    x: (x * factor).round(),
    y: (y * factor).round(),
    width: (width * factor).round(),
    height: (height * factor).round(),
  );

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
