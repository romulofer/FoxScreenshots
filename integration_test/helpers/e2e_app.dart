import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/core/storage/clipboard_service.dart';
import 'package:foxscreenshots/core/storage/output_service.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/core/tray/tray_service.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/editor/editor_compositor.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../test/helpers/fake_capture_service.dart';
import '../../test/helpers/fake_capture_window.dart';
import '../../test/helpers/fake_clipboard.dart';

/// The app booted end to end with every OS-facing edge replaced by a fake
/// (SPEC §6: e2e flows run against a mocked capture service, with no real
/// display dependency).
///
/// Everything above those edges is the production wiring: the real
/// `FoxScreenShotsApp`, the real navigator, controllers, and screens.
class E2EApp {
  E2EApp({
    required this.container,
    required this.capture,
    required this.window,
    required this.clipboard,
    required this.output,
    required this.desktop,
  });

  final ProviderContainer container;
  final FakeScreenCaptureService capture;
  final FakeCaptureWindow window;
  final RecordingClipboardService clipboard;
  final TempDirOutputService output;
  final FakeDesktopIntegration desktop;
}

/// Boots the app for an end-to-end test.
///
/// [screenWidth]/[screenHeight] describe the fake desktop the capture backend
/// reports; the test window is 800×600, so a 400×300 desktop maps overlay
/// coordinates to image pixels at half scale. Pass [failure] to boot with a
/// backend that refuses every grab.
///
/// The UI starts in pt-BR unless [preferences] overrides `locale_tag`.
Future<E2EApp> pumpE2EApp(
  WidgetTester tester, {
  int screenWidth = 400,
  int screenHeight = 300,
  Map<String, Object> preferences = const {},
  CaptureFailure? failure,
  List<Override> extraOverrides = const [],
}) async {
  // Pin the language: the assertions read user-facing strings, and the host's
  // OS locale would otherwise decide which ones the app renders.
  SharedPreferences.setMockInitialValues({'locale_tag': 'pt', ...preferences});
  final prefs = await SharedPreferences.getInstance();

  final capture = FakeScreenCaptureService(
    screenWidth: screenWidth,
    screenHeight: screenHeight,
    failure: failure,
  );
  final window = FakeCaptureWindow();
  final clipboard = RecordingClipboardService();
  final output = TempDirOutputService();
  final desktop = FakeDesktopIntegration();
  addTearDown(output.cleanUp);

  final container = ProviderContainer(
    overrides: [
      settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
      screenCaptureServiceProvider.overrideWithValue(capture),
      captureWindowControllerProvider.overrideWithValue(window),
      desktopIntegrationProvider.overrideWithValue(desktop),
      clipboardServiceProvider.overrideWithValue(clipboard),
      outputServiceProvider.overrideWithValue(output),
      // Isolates and engine decodes never complete under the test clock.
      pngCodecProvider.overrideWithValue(const PngCodec.inline()),
      imageDecoderProvider.overrideWithValue(
        syncImageDecoder(width: screenWidth, height: screenHeight),
      ),
      imageFlattenerProvider.overrideWithValue(markerFlattener),
      ...extraOverrides,
    ],
  );
  addTearDown(container.dispose);

  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: const FoxScreenShotsApp(),
    ),
  );
  await tester.pumpAndSettle();

  return E2EApp(
    container: container,
    capture: capture,
    window: window,
    clipboard: clipboard,
    output: output,
    desktop: desktop,
  );
}

/// Decodes to a blank image of a known size without the engine's async codec.
ImageDecoder syncImageDecoder({required int width, required int height}) {
  return (Uint8List bytes) async {
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder).drawRect(
      ui.Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
      ui.Paint()..color = const ui.Color(0xFF336699),
    );
    final picture = recorder.endRecording();
    try {
      return picture.toImageSync(width, height);
    } finally {
      picture.dispose();
    }
  };
}

/// Bytes the fake compositor returns, so a test can tell an edited capture
/// apart from the original one at a glance.
final Uint8List flattenedMarkerBytes = solidPng(8, 8);

/// Stand-in for the real compositor.
///
/// `Picture.toImage` and `Image.toByteData` are engine work that never
/// completes under the test clock, and the taps that trigger them happen inside
/// app code, where the test cannot wrap them in `runAsync`. Pixel-level
/// compositing is covered by `test/features/editor/editor_compositor_test.dart`;
/// here the flow only needs the export to be observable.
Future<FlattenedImage> markerFlattener({
  required ui.Image base,
  required List<Annotation> annotations,
  required TextDirection textDirection,
}) async {
  return FlattenedImage(image: base, pngBytes: flattenedMarkerBytes);
}

/// Writes to a temp directory instead of opening a save dialog (which needs a
/// real desktop portal).
class TempDirOutputService implements OutputService {
  Directory? _dir;
  final List<String> savedPaths = <String>[];

  /// Set to simulate the user dismissing the save dialog.
  bool cancelled = false;

  Directory get directory =>
      _dir ??= Directory.systemTemp.createTempSync('foxshots_e2e');

  @override
  Future<String?> savePngWithDialog(
    Uint8List pngBytes, {
    String? initialDirectory,
    String? suggestedName,
  }) async {
    if (cancelled) return null;
    return savePngToDir(pngBytes, directory.path);
  }

  @override
  Future<String> savePngToDir(Uint8List pngBytes, String directory) async {
    final file = File('$directory/shot_${savedPaths.length}.png');
    await file.writeAsBytes(pngBytes, flush: true);
    savedPaths.add(file.path);
    return file.path;
  }

  void cleanUp() {
    if (_dir?.existsSync() ?? false) _dir!.deleteSync(recursive: true);
  }
}

/// Captures the tray/hotkey callbacks the shell registers, so a test can fire
/// them the way the desktop would.
class FakeDesktopIntegration implements DesktopIntegration {
  VoidCallback? _onHotkey;
  void Function(TrayAction action)? _onTrayAction;
  VoidCallback? _onOpenWindow;

  final List<String> calls = <String>[];
  String? attachedHotkey;
  Map<TrayAction, String> labels = const {};

  @override
  Future<void> attach({
    required String iconPath,
    required String tooltip,
    required Map<TrayAction, String> labels,
    required VoidCallback onOpenWindow,
    required void Function(TrayAction action) onTrayAction,
    required VoidCallback onHotkey,
    String hotkey = 'PrintScreen',
  }) async {
    calls.add('attach');
    attachedHotkey = hotkey;
    this.labels = labels;
    _onHotkey = onHotkey;
    _onTrayAction = onTrayAction;
    _onOpenWindow = onOpenWindow;
  }

  /// Fires the global capture hotkey.
  void pressHotkey() => _onHotkey?.call();

  /// Picks an entry from the tray context menu.
  void chooseTrayAction(TrayAction action) => _onTrayAction?.call(action);

  /// Left-clicks the tray icon.
  void clickTrayIcon() => _onOpenWindow?.call();

  @override
  Future<void> hideWindow() async => calls.add('hideWindow');

  @override
  Future<void> showWindow() async => calls.add('showWindow');

  @override
  Future<void> quit() async => calls.add('quit');
}

/// Drags a rectangle, in logical pixels of the test window.
///
/// Moves in steps rather than one jump: a single move past the drag slop is
/// reported as the *start* of the pan with no update behind it, which would
/// leave a zero-size shape and never reach the canvas as a real drag.
Future<void> dragRect(
  WidgetTester tester,
  Offset from,
  Offset to, {
  int steps = 4,
}) async {
  final gesture = await tester.startGesture(from);
  await tester.pump();
  for (var i = 1; i <= steps; i++) {
    await gesture.moveTo(Offset.lerp(from, to, i / steps)!);
    await tester.pump();
  }
  await gesture.up();
  await tester.pumpAndSettle();
}
