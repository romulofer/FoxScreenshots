import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/features/home/session_controller.dart';
import 'package:foxscreenshots/models/capture_result.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../helpers/fake_capture_service.dart';

void main() {
  Future<ProviderContainer> pumpHome(WidgetTester tester) async {
    tester.platformDispatcher.localesTestValue = const [Locale('pt')];
    addTearDown(tester.platformDispatcher.clearLocalesTestValue);

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final container = ProviderContainer(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
        screenCaptureServiceProvider.overrideWithValue(
          FakeScreenCaptureService(),
        ),
        desktopIntegrationProvider.overrideWithValue(
          const NoopDesktopIntegration(),
        ),
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
    return container;
  }

  CaptureResult fakeShot(int n) => CaptureResult(
    id: 'shot-$n',
    pngBytes: solidPng(4, 4),
    width: 4,
    height: 4,
    takenAt: DateTime(2026, 1, 1),
  );

  void seed(ProviderContainer container, int count) {
    final notifier = container.read(sessionControllerProvider.notifier);
    for (var i = 0; i < count; i++) {
      notifier.add(fakeShot(i));
    }
  }

  testWidgets('estado vazio mostra ícone e mensagem', (tester) async {
    await pumpHome(tester);

    expect(find.byIcon(Icons.photo_library_outlined), findsOneWidget);
    expect(
      find.text('Nenhuma captura ainda. Use a barra acima para começar.'),
      findsOneWidget,
    );
  });

  testWidgets('salvar tudo e limpar ficam desabilitados sem capturas', (
    tester,
  ) async {
    await pumpHome(tester);

    final saveAll = tester.widget<IconButton>(
      find.widgetWithIcon(IconButton, Icons.save_alt_outlined),
    );
    final clear = tester.widget<IconButton>(
      find.widgetWithIcon(IconButton, Icons.delete_sweep_outlined),
    );
    expect(saveAll.onPressed, isNull);
    expect(clear.onPressed, isNull);
  });

  testWidgets('contador mostra capturas sobre o teto da sessão', (
    tester,
  ) async {
    final container = await pumpHome(tester);
    seed(container, 3);
    await tester.pumpAndSettle();

    expect(
      find.text('3/${SessionController.maxSessionCaptures}'),
      findsOneWidget,
    );
  });

  testWidgets('limpar sessão pede confirmação e esvazia a galeria', (
    tester,
  ) async {
    final container = await pumpHome(tester);
    seed(container, 2);
    await tester.pumpAndSettle();
    expect(container.read(sessionControllerProvider), hasLength(2));

    await tester.tap(
      find.widgetWithIcon(IconButton, Icons.delete_sweep_outlined),
    );
    await tester.pumpAndSettle();

    // Diálogo aberto; cancelar mantém tudo.
    expect(find.text('Limpar a sessão?'), findsOneWidget);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(container.read(sessionControllerProvider), hasLength(2));

    // Reabrir e confirmar esvazia.
    await tester.tap(
      find.widgetWithIcon(IconButton, Icons.delete_sweep_outlined),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Limpar'));
    await tester.pumpAndSettle();
    expect(container.read(sessionControllerProvider), isEmpty);
  });
}
