import 'dart:typed_data';

/// The product of a capture: raw PNG bytes plus metadata.
///
/// Immutable. Held in the in-memory session list (SPEC §2.5) and passed to the
/// editor, clipboard, and output services. Never leaves the device.
class CaptureResult {
  CaptureResult({
    required this.id,
    required this.pngBytes,
    required this.width,
    required this.height,
    required this.takenAt,
  });

  final String id;
  final Uint8List pngBytes;
  final int width;
  final int height;
  final DateTime takenAt;

  int get byteLength => pngBytes.length;

  @override
  String toString() =>
      'CaptureResult($id, $width×$height, ${pngBytes.length} bytes)';
}
