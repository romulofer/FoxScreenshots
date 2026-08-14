import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../helpers/fake_capture_service.dart';

void main() {
  testWidgets('a tela inicial cai para pt-BR quando o idioma do sistema não é suportado', (
    tester,
  ) async {
    // French is neither pt nor en, so the app must fall back to pt-BR
    // (SPEC §2.6).
    tester.platformDispatcher.localesTestValue = const [Locale('fr')];
    addTearDown(tester.platformDispatcher.clearLocalesTestValue);

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
          screenCaptureServiceProvider.overrideWithValue(
            FakeScreenCaptureService(),
          ),
          // No tray or global hotkey in a widget test.
          desktopIntegrationProvider.overrideWithValue(
            const NoopDesktopIntegration(),
          ),
        ],
        child: const FoxScreenShotsApp(),
      ),
    );
    await tester.pumpAndSettle();

    // pt-BR is the fallback locale (SPEC §2.6).
    expect(
      find.text('Nenhuma captura ainda. Use a barra acima para começar.'),
      findsOneWidget,
    );
    expect(find.text('Instantâneo'), findsOneWidget);
  });
}
