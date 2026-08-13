import 'package:foxscreenshots/core/window/capture_window_controller.dart';

/// Records the window moves a capture flow makes, without touching a real
/// desktop window (`flutter test` has no embedder).
class FakeCaptureWindow implements CaptureWindowController {
  final List<String> calls = <String>[];

  @override
  Future<void> hideForCapture() async => calls.add('hideForCapture');

  @override
  Future<void> enterOverlay() async => calls.add('enterOverlay');

  @override
  Future<void> leaveOverlay() async => calls.add('leaveOverlay');

  @override
  Future<void> restore() async => calls.add('restore');
}
