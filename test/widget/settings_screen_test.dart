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
    await tester.dragUntilVisible(
      find.text('v.9.9.9'),
      find.byType(ListView),
      const Offset(0, -100),
    );
    expect(find.text('v.9.9.9'), findsOneWidget);
  });

  testWidgets('mostra o valor do atraso ao lado do slider', (tester) async {
    await pumpSettings(tester, prefs: {'timer_delay_seconds': 7});
    expect(find.text('7'), findsOneWidget);
  });

  testWidgets('seção Captura exibe atalho do temporizador', (tester) async {
    await pumpSettings(tester);
    expect(find.text('Atalho do temporizador'), findsOneWidget);
  });

  testWidgets('seção Captura exibe atalho de repetir último', (tester) async {
    await pumpSettings(tester);
    expect(find.text('Atalho de repetir último'), findsOneWidget);
  });

  testWidgets('atalho do temporizador começa em F7', (tester) async {
    await pumpSettings(tester);
    final dropdowns = tester
        .widgetList<DropdownButton<String>>(find.byType(DropdownButton<String>))
        .toList();
    // Order: language(0), capture hotkey(1), timer hotkey(2), repeat hotkey(3).
    expect(dropdowns[2].value, 'F7');
  });

  testWidgets('atalho de repetir último começa em F6', (tester) async {
    await pumpSettings(tester);
    final dropdowns = tester
        .widgetList<DropdownButton<String>>(find.byType(DropdownButton<String>))
        .toList();
    expect(dropdowns[3].value, 'F6');
  });
}
