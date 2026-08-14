import 'dart:ui' as ui;

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/core/storage/clipboard_service.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/capture_controller.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/home/session_controller.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../helpers/fake_capture_service.dart';
import '../../helpers/fake_capture_window.dart';
import '../../helpers/fake_clipboard.dart';

void main() {
  late FakeScreenCaptureService service;
  late FakeCaptureWindow window;
  late RecordingClipboardService clipboard;
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
        clipboardServiceProvider.overrideWithValue(clipboard),
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
    clipboard = RecordingClipboardService();
  });

  group('captura instantânea', () {
    testWidgets('recorta o quadro congelado na região arrastada', (tester) async {
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

    testWidgets('cai na área de transferência na hora', (tester) async {
      await pumpApp(tester);
      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(20, 40), const Offset(220, 240));
      final result = await pending;

      // Hit the hotkey, then paste: the capture is copied without a second
      // trip through the gallery.
      expect(clipboard.writes.single, same(result!.pngBytes));
    });

    testWidgets('uma sessão sem área de transferência ainda guarda a captura', (
      tester,
    ) async {
      clipboard.available = false;
      await pumpApp(tester);
      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(20, 40), const Offset(220, 240));

      expect(await pending, isNotNull);
      expect(container.read(sessionControllerProvider), hasLength(1));
    });

    testWidgets('adiciona a captura à sessão', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureInstant();
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(10, 10), const Offset(110, 90));
      await pending;

      expect(container.read(sessionControllerProvider), hasLength(1));
    });

    testWidgets('esconde a janela principal e sempre a restaura', (tester) async {
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

    testWidgets('Esc cancela sem registrar captura', (tester) async {
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

    testWidgets('um clique solto não é uma seleção', (tester) async {
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

    testWidgets('ignora uma segunda captura enquanto uma está em andamento', (
      tester,
    ) async {
      await pumpApp(tester);
      final controller = container.read(captureControllerProvider);

      final first = controller.captureInstant();
      await tester.pumpAndSettle();
      final second = await controller.captureInstant();
      expect(second, isNull);

      await dragSelection(tester, const Offset(10, 10), const Offset(110, 90));
      expect(await first, isNotNull);
    });
  });

  group('captura com temporizador', () {
    testWidgets('seleciona antes e captura de novo depois do atraso', (tester) async {
      await pumpApp(tester);

      final pending = container
          .read(captureControllerProvider)
          .captureWithTimer(delay: const Duration(milliseconds: 20));
      await tester.pumpAndSettle();
      await dragSelection(tester, const Offset(20, 40), const Offset(220, 240));
      // Advance the fake clock past the timer delay.
      await tester.pump(const Duration(milliseconds: 30));
      final result = await pending;

      expect(result!.width, 100);
      // Framing happens over a frozen snapshot (a see-through window needs a
      // compositor), but the shot itself is grabbed live after the delay.
      expect(service.fullScreenCalls, 1);
      expect(service.regionCalls, 1);
      expect(
        service.lastRegion,
        const CaptureRegion(x: 10, y: 20, width: 100, height: 100),
      );
    });

    testWidgets('mapeia a seleção pelos limites de janela concedidos', (
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
  });

  group('captura de tela cheia', () {
    testWidgets('registra a tela virtual inteira, sem sobreposição', (
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

  group('captura de janela ativa', () {
    testWidgets('pega a geometria da janela em foco', (tester) async {
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

    testWidgets('acusa falha quando nada está em foco', (tester) async {
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

  group('falha do backend', () {
    testWidgets('propaga a falha e restaura a janela', (
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
