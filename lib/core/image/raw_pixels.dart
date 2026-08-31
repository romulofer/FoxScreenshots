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

  // The common case (32bpp, 4-byte-aligned rows — X11 always pads scan lines
  // to a word boundary): swap red/blue a whole pixel at a time through one
  // 32-bit read plus a handful of bitwise ops, instead of three
  // bounds-checked byte reads and four byte writes per pixel. Every capture
  // this backend takes — instant, timer, full-screen or active-window —
  // funnels every pixel of the grab through this function, so the per-pixel
  // constant factor here is what the user waits through between the hotkey
  // and the frozen frame appearing.
  //
  // Typed-data views require the offset into the buffer to be a multiple of
  // the element size; `source` is always a zero-offset view in practice (a
  // fresh Uint8List, or `Pointer<Uint8>.asTypedList`), so this only ever
  // falls through on the untested 24bpp path.
  if (bytesPerPixel == 4 &&
      bytesPerLine % 4 == 0 &&
      source.offsetInBytes % 4 == 0) {
    final wordsPerLine = bytesPerLine ~/ 4;
    final srcWords = source.buffer.asUint32List(
      source.offsetInBytes,
      wordsPerLine * height,
    );
    final outWords = out.buffer.asUint32List();
    var o = 0;
    for (var y = 0; y < height; y++) {
      var i = y * wordsPerLine;
      for (var x = 0; x < width; x++) {
        final pixel = srcWords[i];
        outWords[o] = blueFirst
            ? (0xFF000000 |
                  (pixel & 0x0000FF00) |
                  ((pixel & 0x00FF0000) >> 16) |
                  ((pixel & 0x000000FF) << 16))
            : (0xFF000000 | (pixel & 0x00FFFFFF));
        i++;
        o++;
      }
    }
    return out;
  }

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
