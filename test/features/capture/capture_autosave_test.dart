import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/storage/clipboard_service.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/capture_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../helpers/fake_capture_service.dart';
import '../../helpers/fake_capture_window.dart';
import '../../helpers/fake_clipboard.dart';

/// Auto-save (SPEC §2.3) writes every new capture to the output folder as it is
/// taken. Full-screen capture is the simplest flow that reaches `_record`
/// without an overlay/navigator, so it exercises the wiring on its own.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory tmp;

  Future<ProviderContainer> containerWith(
    Map<String, Object> prefsValues,
  ) async {
    SharedPreferences.setMockInitialValues(prefsValues);
    final prefs = await SharedPreferences.getInstance();
    final container = ProviderContainer(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
        screenCaptureServiceProvider.overrideWithValue(
          FakeScreenCaptureService(),
        ),
        captureWindowControllerProvider.overrideWithValue(FakeCaptureWindow()),
        clipboardServiceProvider.overrideWithValue(RecordingClipboardService()),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  List<File> pngsIn(Directory dir) => dir.listSync().whereType<File>().toList();

  setUp(() => tmp = Directory.systemTemp.createTempSync('foxshots_autosave'));
  tearDown(() => tmp.deleteSync(recursive: true));

  test('grava a captura na pasta quando ligado', () async {
    final container = await containerWith({
      'auto_save': true,
      'output_dir': tmp.path,
    });

    final result = await container
        .read(captureControllerProvider)
        .captureFullScreen();

    expect(result, isNotNull);
    final files = pngsIn(tmp);
    expect(files, hasLength(1));
    expect(files.single.path, endsWith('.png'));
  });

  test('não grava nada quando desligado', () async {
    final container = await containerWith({
      'auto_save': false,
      'output_dir': tmp.path,
    });

    await container.read(captureControllerProvider).captureFullScreen();

    expect(pngsIn(tmp), isEmpty);
  });

  test('ligado sem pasta não grava nem quebra a captura', () async {
    final container = await containerWith({'auto_save': true});

    final result = await container
        .read(captureControllerProvider)
        .captureFullScreen();

    // A captura ainda entra na sessão; só o auto-save é pulado.
    expect(result, isNotNull);
    expect(pngsIn(tmp), isEmpty);
  });

  test(
    'duas capturas seguidas geram dois arquivos, sem sobrescrever',
    () async {
      final container = await containerWith({
        'auto_save': true,
        'output_dir': tmp.path,
      });
      final controller = container.read(captureControllerProvider);

      await controller.captureFullScreen();
      await controller.captureFullScreen();

      expect(pngsIn(tmp), hasLength(2));
    },
  );
}
