import 'dart:isolate';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image/image.dart' as img;

import '../../models/capture_region.dart';

/// PNG encode/decode helpers for captured frames (SPEC §2.3).
///
/// Screen-sized bitmaps are expensive to encode, so every entry point hops to a
/// background isolate: the UI keeps painting while a 4K frame is compressed.
/// Everything stays in memory on the user's machine (SPEC §7).
class PngCodec {
  const PngCodec() : _inline = false;

  /// Runs on the calling isolate instead of spawning one.
  ///
  /// For widget tests: `flutter test` drives a fake clock, and a real isolate
  /// hop never completes there unless the test wraps it in `runAsync`.
  const PngCodec.inline() : _inline = true;

  final bool _inline;

  /// Compression level for freeze-frame captures. 3 trades a slightly larger
  /// file for a much faster encode, which matters while the screen is frozen
  /// and the user is waiting to drag a selection.
  static const int fastLevel = 3;

  /// Encodes tightly-packed RGBA8888 [rgba] as PNG.
  Future<Uint8List> encodeRgba(
    Uint8List rgba, {
    required int width,
    required int height,
    int level = 6,
  }) {
    if (_inline) {
      return Future.value(encodeRgbaSync(rgba, width, height, level));
    }
    return Isolate.run(() => encodeRgbaSync(rgba, width, height, level));
  }

  /// Crops [pngBytes] to [region] and re-encodes it.
  ///
  /// [region] is in image pixels and is clamped to the image, so a selection
  /// dragged past the edge yields the overlapping part instead of throwing.
  /// Returns `null` when the region does not overlap the image at all.
  Future<CroppedPng?> crop(Uint8List pngBytes, CaptureRegion region) {
    if (_inline) return Future.value(cropSync(pngBytes, region));
    return Isolate.run(() => cropSync(pngBytes, region));
  }
}

/// Overridden with [PngCodec.inline] in widget tests.
final pngCodecProvider = Provider<PngCodec>((ref) => const PngCodec());

/// A cropped frame plus its new dimensions.
class CroppedPng {
  const CroppedPng({
    required this.pngBytes,
    required this.width,
    required this.height,
  });

  final Uint8List pngBytes;
  final int width;
  final int height;
}

/// Synchronous body of [PngCodec.encodeRgba]; top-level so it can run inside an
/// isolate. Prefer the [PngCodec] wrapper in app code.
Uint8List encodeRgbaSync(Uint8List rgba, int width, int height, int level) {
  final image = img.Image.fromBytes(
    width: width,
    height: height,
    bytes: rgba.buffer,
    bytesOffset: rgba.offsetInBytes,
    numChannels: 4,
    order: img.ChannelOrder.rgba,
  );
  return img.encodePng(image, level: level);
}

/// Synchronous body of [PngCodec.crop]; top-level so it can run inside an
/// isolate. Prefer the [PngCodec] wrapper in app code.
CroppedPng? cropSync(Uint8List pngBytes, CaptureRegion region) {
  final decoded = img.decodePng(pngBytes);
  if (decoded == null) throw const FormatException('Not a valid PNG');

  final area = region.clampedTo(decoded.width, decoded.height);
  if (area.isEmpty) return null;

  final cropped = img.copyCrop(
    decoded,
    x: area.x,
    y: area.y,
    width: area.width,
    height: area.height,
  );
  return CroppedPng(
    pngBytes: img.encodePng(cropped),
    width: cropped.width,
    height: cropped.height,
  );
}
