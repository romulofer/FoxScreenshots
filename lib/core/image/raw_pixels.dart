import 'dart:typed_data';

/// Byte order of a source framebuffer, per pixel.
///
/// X11 `ZPixmap` data on a little-endian host with the usual `0x00FF0000` red
/// mask arrives as B, G, R, X — hence [bgra] is the desktop default.
enum RawPixelOrder { bgra, rgba }

/// Repacks a raw framebuffer into tightly-packed RGBA8888.
///
/// Pure and platform-free so the conversion can be unit-tested without a
/// display (SPEC §6). Handles the two shapes desktop capture backends return:
///
/// * `bitsPerPixel == 32` — 4 bytes per pixel, the 4th being padding (X11) or a
///   real alpha channel; padding is replaced with an opaque alpha.
/// * `bitsPerPixel == 24` — 3 packed bytes per pixel, alpha forced to 255.
///
/// [bytesPerLine] is the source stride, which is commonly larger than
/// `width * bytesPerPixel` because rows are padded for alignment.
Uint8List rgbaFromRaw({
  required Uint8List source,
  required int width,
  required int height,
  required int bytesPerLine,
  required int bitsPerPixel,
  RawPixelOrder order = RawPixelOrder.bgra,
}) {
  if (width <= 0 || height <= 0) {
    throw ArgumentError('Invalid size ${width}x$height');
  }
  if (bitsPerPixel != 32 && bitsPerPixel != 24) {
    throw ArgumentError('Unsupported bitsPerPixel: $bitsPerPixel');
  }

  final bytesPerPixel = bitsPerPixel ~/ 8;
  if (bytesPerLine < width * bytesPerPixel) {
    throw ArgumentError(
      'bytesPerLine $bytesPerLine is shorter than a $width-pixel row',
    );
  }
  if (source.length < (height - 1) * bytesPerLine + width * bytesPerPixel) {
    throw ArgumentError('source is too short for ${width}x$height');
  }

  final blueFirst = order == RawPixelOrder.bgra;
  final out = Uint8List(width * height * 4);
  var o = 0;
  for (var y = 0; y < height; y++) {
    var i = y * bytesPerLine;
    for (var x = 0; x < width; x++) {
      final first = source[i];
      final middle = source[i + 1];
      final last = source[i + 2];
      out[o] = blueFirst ? last : first; // R
      out[o + 1] = middle; // G
      out[o + 2] = blueFirst ? first : last; // B
      out[o + 3] = 255; // X11 leaves the 4th byte undefined; force opaque.
      i += bytesPerPixel;
      o += 4;
    }
  }
  return out;
}
