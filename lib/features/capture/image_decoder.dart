import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Turns PNG bytes into a GPU image for the selection overlay backdrop.
typedef ImageDecoder = Future<ui.Image> Function(Uint8List pngBytes);

/// Real decoding goes through the engine codec. Widget tests override this:
/// engine decoding is real async work that never completes under the fake
/// clock `flutter test` installs.
final imageDecoderProvider = Provider<ImageDecoder>((ref) {
  return decodeImageFromList;
});
