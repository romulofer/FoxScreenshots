import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/storage/clipboard_service.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/editor/editor_compositor.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'dart:ui' as ui;

import '../helpers/fake_capture_service.dart';
import '../helpers/fake_capture_window.dart';
import '../helpers/fake_clipboard.dart';
import '../helpers/fake_desktop_integration.dart';

/// A capture fired from the global hotkey (or tray) runs with the hub hidden,
/// so it confirms itself on the app-wide messenger. The toolbar path, where the
/// new thumbnail is the confirmation, stays quiet.
void main() {
  ImageDecoder fakeDecoder(FakeScreenCaptureService service) {
    return (_) async {
      final recorder = ui.PictureRecorder();
      ui.Canvas(recorder);
      return recorder.endRecording().toImageSync(
        service.screenWidth,
        service.screenHeight,
      );
    };
  }

  Future<FlattenedImage> fakeCropper({
    required ui.Image base,
    required ui.Rect rect,
  }) async {
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder);
    final image = recorder.endRecording().toImageSync(
      rect.width.round(),
      rect.height.round(),
    );
    return FlattenedImage(
      image: image,
      pngBytes: Uint8List.fromList(const [1, 2, 3]),
    );
  }

  testWidgets('captura por hotkey confirma na barra de mensagens', (
    tester,
  ) async {
    tester.platformDispatcher.localesTestValue = const [Locale('pt')];
    addTearDown(tester.platformDispatcher.clearLocalesTestValue);

    final service = FakeScreenCaptureService(
      screenWidth: 400,
      screenHeight: 300,
    );
    final desktop = FakeDesktopIntegration();

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final container = ProviderContainer(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
        screenCaptureServiceProvider.overrideWithValue(service),
        captureWindowControllerProvider.overrideWithValue(FakeCaptureWindow()),
        clipboardServiceProvider.overrideWithValue(RecordingClipboardService()),
        desktopIntegrationProvider.overrideWithValue(desktop),
        imageDecoderProvider.overrideWithValue(fakeDecoder(service)),
        imageCropperProvider.overrideWithValue(fakeCropper),
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

    // AppShell wired its callbacks on the first build; fire the hotkey as the
    // real key handler would.
    expect(desktop.onHotkey, isNotNull);
    desktop.onHotkey!.call();
    await tester.pumpAndSettle();

    // Hotkey capture is instant mode: it needs a region drag to complete.
    final gesture = await tester.startGesture(const Offset(20, 40));
    await tester.pump();
    await gesture.moveTo(const Offset(220, 240));
    await tester.pump();
    await gesture.up();
    await tester.pumpAndSettle();

    expect(find.text('Captura adicionada à sessão'), findsOneWidget);
  });

  testWidgets('captura cancelada não mostra confirmação', (tester) async {
    tester.platformDispatcher.localesTestValue = const [Locale('pt')];
    addTearDown(tester.platformDispatcher.clearLocalesTestValue);

    final service = FakeScreenCaptureService(
      screenWidth: 400,
      screenHeight: 300,
    );
    final desktop = FakeDesktopIntegration();

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final container = ProviderContainer(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
        screenCaptureServiceProvider.overrideWithValue(service),
        captureWindowControllerProvider.overrideWithValue(FakeCaptureWindow()),
        clipboardServiceProvider.overrideWithValue(RecordingClipboardService()),
        desktopIntegrationProvider.overrideWithValue(desktop),
        imageDecoderProvider.overrideWithValue(fakeDecoder(service)),
        imageCropperProvider.overrideWithValue(fakeCropper),
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

    desktop.onHotkey!.call();
    await tester.pumpAndSettle();

    // Esc cancels the selection; no capture, no confirmation.
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    expect(find.text('Captura adicionada à sessão'), findsNothing);
  });
}
