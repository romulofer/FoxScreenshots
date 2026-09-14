import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/features/settings/settings_controller.dart';
import 'package:foxscreenshots/features/settings/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  Future<void> pumpSettings(
    WidgetTester tester, {
    Map<String, Object> prefs = const {},
  }) async {
    SharedPreferences.setMockInitialValues(prefs);
    final store = await SharedPreferences.getInstance();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsServiceProvider.overrideWithValue(SettingsService(store)),
          appVersionProvider.overrideWith((ref) async => '9.9.9'),
        ],
        child: const MaterialApp(
          locale: Locale('pt'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: SettingsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('agrupa as opções em seções', (tester) async {
    await pumpSettings(tester);

    expect(find.text('Aparência'), findsOneWidget);
    expect(find.text('Captura'), findsOneWidget);
    expect(find.text('Saída'), findsOneWidget);
  });

  testWidgets('auto-save começa desligado e sem pasta', (tester) async {
    await pumpSettings(tester);

    expect(find.text('Nenhuma pasta escolhida'), findsOneWidget);
    final toggle = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(toggle.value, isFalse);
  });

  testWidgets('mostra a pasta escolhida e o auto-save ligado', (tester) async {
    await pumpSettings(
      tester,
      prefs: {'output_dir': '/tmp/shots', 'auto_save': true},
    );

    expect(find.text('/tmp/shots'), findsOneWidget);
    final toggle = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(toggle.value, isTrue);
  });

  testWidgets('exibe a versão do app no rodapé', (tester) async {
    await pumpSettings(tester);
    expect(find.text('v.9.9.9'), findsOneWidget);
  });
}
