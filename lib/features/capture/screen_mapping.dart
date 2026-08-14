import 'dart:ui';

import '../../core/window/capture_window_controller.dart';
import '../../models/capture_region.dart';

/// Maps the overlay's logical coordinates onto the frozen screenshot.
///
/// The overlay window is not guaranteed to cover the whole virtual screen —
/// a window manager may clamp it to one monitor — so the backdrop must be
/// drawn from the slice of the screenshot that lies under the window, at 1:1.
/// Stretching the full image over the window instead is what makes the frozen
/// desktop look shifted next to the real one.
class ScreenMapping {
  const ScreenMapping({
    required this.imageOrigin,
    required this.imagePixelsPerLogical,
  });

  /// Screenshot pixel that sits under the overlay's top-left corner.
  final Offset imageOrigin;

  /// Screenshot pixels per logical pixel (the display scale factor).
  final double imagePixelsPerLogical;

  /// Derives the mapping from where the overlay window landed.
  ///
  /// [imageWidth] is the screenshot width in physical pixels; the virtual
  /// screen width is in logical pixels, so their ratio is the scale factor.
  ///
  /// A measured placement is used as-is — it is already in screenshot pixels
  /// and, unlike the window manager's own answer, it is never stale.
  factory ScreenMapping.fromPlacement(
    OverlayPlacement placement, {
    required int imageWidth,
  }) {
    final virtual = placement.virtualScreen;
    final scale = virtual.width > 0 ? imageWidth / virtual.width : 1.0;
    final measured = placement.physicalWindow;
    return ScreenMapping(
      imageOrigin: measured != null
          ? measured.topLeft - virtual.topLeft * scale
          : Offset(
              (placement.window.left - virtual.left) * scale,
              (placement.window.top - virtual.top) * scale,
            ),
      imagePixelsPerLogical: scale,
    );
  }

  /// Screenshot pixel under a point in overlay coordinates.
  Offset toImage(Offset local) => imageOrigin + local * imagePixelsPerLogical;

  /// Screenshot region for a rectangle dragged in overlay coordinates,
  /// clipped to a [imageWidth]×[imageHeight] screenshot.
  CaptureRegion toRegion(
    Offset a,
    Offset b, {
    required int imageWidth,
    required int imageHeight,
  }) {
    return CaptureRegion.fromPoints(
      toImage(a),
      toImage(b),
    ).clampedTo(imageWidth, imageHeight);
  }

  @override
  bool operator ==(Object other) =>
      other is ScreenMapping &&
      other.imageOrigin == imageOrigin &&
      other.imagePixelsPerLogical == imagePixelsPerLogical;

  @override
  int get hashCode => Object.hash(imageOrigin, imagePixelsPerLogical);

  @override
  String toString() =>
      'ScreenMapping(origin: $imageOrigin, scale: $imagePixelsPerLogical)';
}
