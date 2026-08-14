import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:foxscreenshots/models/capture_result.dart';

/// A solid-color image built through `toImageSync`, so tests get a real
/// `ui.Image` without waiting on the engine's async decode path.
ui.Image solidImage({
  int width = 100,
  int height = 80,
  ui.Color color = const ui.Color(0xFF3366CC),
}) {
  final recorder = ui.PictureRecorder();
  ui.Canvas(recorder).drawRect(
    ui.Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
    ui.Paint()..color = color,
  );
  final picture = recorder.endRecording();
  try {
    return picture.toImageSync(width, height);
  } finally {
    picture.dispose();
  }
}

/// Image whose left half is [left] and right half is [right] — enough contrast
/// for a redaction or crop to be observable in the output pixels.
ui.Image splitImage({
  int width = 100,
  int height = 80,
  ui.Color left = const ui.Color(0xFF000000),
  ui.Color right = const ui.Color(0xFFFFFFFF),
}) {
  final recorder = ui.PictureRecorder();
  ui.Canvas(recorder)
    ..drawRect(
      ui.Rect.fromLTWH(0, 0, width / 2, height.toDouble()),
      ui.Paint()..color = left,
    )
    ..drawRect(
      ui.Rect.fromLTWH(width / 2, 0, width / 2, height.toDouble()),
      ui.Paint()..color = right,
    );
  final picture = recorder.endRecording();
  try {
    return picture.toImageSync(width, height);
  } finally {
    picture.dispose();
  }
}

/// A capture whose bytes are never decoded for real — editor tests override the
/// decoder with [solidImage] and friends.
CaptureResult fakeCapture({
  String id = 'shot-1',
  int width = 100,
  int height = 80,
}) {
  return CaptureResult(
    id: id,
    pngBytes: Uint8List.fromList(const [0x89, 0x50, 0x4E, 0x47]),
    width: width,
    height: height,
    takenAt: DateTime(2026, 1, 1),
  );
}
