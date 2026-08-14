import 'dart:ui';

/// Maps between the widget's logical pixels and the image's own pixels.
///
/// The editor stores every annotation in image coordinates, so the preview
/// needs exactly one place that knows how the
/// image was letterboxed into the available space — the painter and the
/// gesture handler must agree, or marks land where the user did not click.
class CanvasFit {
  const CanvasFit({
    required this.scale,
    required this.offset,
    required this.imageSize,
  });

  /// Largest scale that fits [imageSize] inside [viewport] without cropping,
  /// never magnifying past 1:1 (a small capture blown up is just blurry).
  factory CanvasFit.contain({required Size imageSize, required Size viewport}) {
    if (imageSize.isEmpty || viewport.isEmpty) {
      return CanvasFit(scale: 1, offset: Offset.zero, imageSize: imageSize);
    }
    final scale = [
      viewport.width / imageSize.width,
      viewport.height / imageSize.height,
      1.0,
    ].reduce((a, b) => a < b ? a : b);
    final painted = imageSize * scale;
    return CanvasFit(
      scale: scale,
      offset: Offset(
        (viewport.width - painted.width) / 2,
        (viewport.height - painted.height) / 2,
      ),
      imageSize: imageSize,
    );
  }

  final double scale;

  /// Top-left of the painted image inside the widget.
  final Offset offset;

  final Size imageSize;

  Rect get destination => offset & (imageSize * scale);

  /// Widget-local point to image pixels, clamped to the image: a drag that
  /// runs off the edge should stop at the border, not annotate thin air.
  Offset toImage(Offset local) {
    if (scale == 0) return Offset.zero;
    final unscaled = (local - offset) / scale;
    return Offset(
      unscaled.dx.clamp(0, imageSize.width),
      unscaled.dy.clamp(0, imageSize.height),
    );
  }

  @override
  bool operator ==(Object other) =>
      other is CanvasFit &&
      other.scale == scale &&
      other.offset == offset &&
      other.imageSize == imageSize;

  @override
  int get hashCode => Object.hash(scale, offset, imageSize);
}
