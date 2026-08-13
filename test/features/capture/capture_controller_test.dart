import 'dart:ui' as ui;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/capture_controller.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/home/session_controller.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../helpers/fake_capture_service.dart';
import '../../helpers/fake_capture_window.dart';

void main() {
  late FakeScreenCaptureService service;
  late FakeCaptureWindow window;
  late ProviderContainer container;

  /// Boots the real app widget tree so the flows can push the overlay through
  /// the app navigator, exactly as they do at runtime.
  Future<void> pumpApp(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();

    container = ProviderContainer(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
        screenCaptureServiceProvider.overrideWithValue(service),
        captureWindowControllerProvider.overrideWithValue(window),
        desktopIntegrationProvider.overrideWithValue(
          const NoopDesktopIntegration(),
        ),
        // Both hop off the fake clock in production; keep tests synchronous.
        pngCodecProvider.overrideWithValue(const PngCodec.inline()),
        imageDecoderProvider.overrideWithValue(_fakeDecoder(service)),
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
  }

  /// Drags a rectangle on the fullscreen overlay, in logical pixels.
  Future<void> dragSelection(
    WidgetTester tester,
    Offset from,
    Offset to,
  ) async {
    final gesture = await tester.startGesture(from);
    await tester.pump();
    await gesture.moveTo(to);
    await tester.pump();
    await gesture.up();
    await tester.pumpAndSettle();
  }

  setUp(() {
    service = FakeScreenCaptureService(screenWidth: 400, screenHeight: 300);
    window = FakeCaptureWindow();
  });

  group('instant capture', () {
    testWidgets('crops the frozen frame to the dragged region', (tester) async {
      await pumpApp(tester);
      // The test window is 800x600 and the fake screen is 400x300, so overlay
      // coordinates map to image pixels at half scale.
      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();

      await dragSelection(tester, const Offset(20, 40), const Offset(220, 240));
      final result = await pending;

      expect(result, isNotNull);
      expect(result!.width, 100);
      expect(result.height, 100);
      // The freeze is cropped locally: no second grab hits the backend.
      expect(service.fullScreenCalls, 1);
      expect(service.regionCalls, 0);
    });

    testWidgets('adds the capture to the session', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(10, 10), const Offset(110, 90));
      await pending;

      expect(container.read(sessionControllerProvider), hasLength(1));
    });

    testWidgets('hides the hub window and always restores it', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(10, 10), const Offset(110, 90));
      await pending;

      expect(window.calls, [
        'hideForCapture',
        'enterOverlay',
        'revealOverlay',
        'leaveOverlay',
        'restore',
      ]);
    });

    testWidgets('Esc cancels without recording a capture', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      final result = await pending;

      expect(result, isNull);
      expect(container.read(sessionControllerProvider), isEmpty);
      expect(window.calls.last, 'restore');
    });

    testWidgets('maps the selection through the granted window bounds', (
      tester,
    ) async {
      // The window manager clamped the overlay to the right-hand half of a
      // 1600x600 virtual screen; a drag there must land on the right half of
      // the screenshot, not at its origin.
      window = FakeCaptureWindow(
        virtualScreen: const Rect.fromLTWH(0, 0, 1600, 600),
        granted: const Rect.fromLTWH(800, 0, 800, 600),
      );
      service = FakeScreenCaptureService(screenWidth: 1600, screenHeight: 600);
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureWithTimer(delay: Duration.zero);
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(10, 20), const Offset(110, 120));
      await pending;

      expect(
        service.lastRegion,
        const CaptureRegion(x: 810, y: 20, width: 100, height: 100),
      );
    });

    testWidgets('a stray click is not a selection', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(50, 50), const Offset(51, 51));
      // Still waiting for a real drag.
      expect(container.read(sessionControllerProvider), isEmpty);

      await dragSelection(tester, const Offset(10, 10), const Offset(110, 90));
      expect(await pending, isNotNull);
    });
  });

  group('timer capture', () {
    testWidgets('re-grabs the selected region after the delay', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureWithTimer(delay: const Duration(milliseconds: 20));
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(20, 40), const Offset(220, 240));
      final result = await pending;

      expect(result!.width, 100);
      // Timer mode captures the *live* screen, not the freeze it selected on.
      expect(service.regionCalls, 1);
      expect(
        service.lastRegion,
        const CaptureRegion(x: 10, y: 20, width: 100, height: 100),
      );
    });
  });

  group('full screen capture', () {
    testWidgets('records the whole virtual screen with no overlay', (
      tester,
    ) async {
      await pumpApp(tester);

      final result = await container
          .read(captureControllerProvider)
          .captureFullScreen();
      await tester.pumpAndSettle();

      expect(result!.width, 400);
      expect(result.height, 300);
      expect(window.calls, ['hideForCapture', 'restore']);
    });
  });

  group('active window capture', () {
    testWidgets('grabs the focused window geometry', (tester) async {
      service = FakeScreenCaptureService(
        activeWindow: const CaptureRegion(x: 5, y: 6, width: 120, height: 80),
      );
      await pumpApp(tester);

      final result = await container
          .read(captureControllerProvider)
          .captureActiveWindow();

      expect(result!.width, 120);
      expect(result.height, 80);
      expect(service.lastRegion?.x, 5);
    });

    testWidgets('reports a failure when nothing is focused', (tester) async {
      await pumpApp(tester);

      await expectLater(
        container.read(captureControllerProvider).captureActiveWindow(),
        throwsA(
          isA<CaptureException>().having(
            (e) => e.failure,
            'failure',
            CaptureFailure.noActiveWindow,
          ),
        ),
      );
      expect(window.calls.last, 'restore');
    });
  });

  group('backend failure', () {
    testWidgets('propagates the failure and restores the window', (
      tester,
    ) async {
      service = FakeScreenCaptureService(
        failure: CaptureFailure.displayUnavailable,
      );
      await pumpApp(tester);

      await expectLater(
        container.read(captureControllerProvider).captureFullScreen(),
        throwsA(isA<CaptureException>()),
      );
      expect(window.calls, ['hideForCapture', 'restore']);
    });
  });
}

/// Builds a blank [ui.Image] the size of the fake screen, synchronously.
ImageDecoder _fakeDecoder(FakeScreenCaptureService service) {
  return (Uint8List _) async {
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder).drawRect(
      ui.Rect.fromLTWH(
        0,
        0,
        service.screenWidth.toDouble(),
        service.screenHeight.toDouble(),
      ),
      ui.Paint()..color = const ui.Color(0xFF336699),
    );
    return recorder.endRecording().toImageSync(
      service.screenWidth,
      service.screenHeight,
    );
  };
}
