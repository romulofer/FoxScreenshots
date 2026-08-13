import 'dart:ui';

import 'package:foxscreenshots/core/window/capture_window_controller.dart';

/// Records the window moves a capture flow makes, without touching a real
/// desktop window (`flutter test` has no embedder).
///
/// [granted] stands in for what a window manager hands back from
/// `revealOverlay`; it defaults to the whole requested area.
class FakeCaptureWindow implements CaptureWindowController {
  FakeCaptureWindow({
    this.virtualScreen = const Rect.fromLTWH(0, 0, 800, 600),
    Rect? granted,
  }) : granted = granted ?? virtualScreen;

  final Rect virtualScreen;
  final Rect granted;
  final List<String> calls = <String>[];

  @override
  Future<void> hideForCapture() async => calls.add('hideForCapture');

  /// Whether the last [enterOverlay] asked for a see-through window.
  bool? lastTransparent;

  @override
  Future<OverlayPlacement> enterOverlay({bool transparent = false}) async {
    lastTransparent = transparent;
    calls.add('enterOverlay');
    return OverlayPlacement(
      window: virtualScreen,
      virtualScreen: virtualScreen,
    );
  }

  @override
  Future<OverlayPlacement> revealOverlay() async {
    calls.add('revealOverlay');
    return OverlayPlacement(window: granted, virtualScreen: virtualScreen);
  }

  @override
  Future<void> leaveOverlay() async => calls.add('leaveOverlay');

  @override
  Future<void> restore() async => calls.add('restore');
}
