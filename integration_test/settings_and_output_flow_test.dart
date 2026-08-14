import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/home/widgets/thumbnail_tile.dart';
import 'package:foxscreenshots/features/settings/settings_controller.dart';
import 'package:foxscreenshots/features/settings/settings_screen.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/e2e_app.dart';

/// End-to-end settings and output (SPEC §2.3, §2.6, §2.7): preferences apply
/// live and persist, and a capture reaches the clipboard and the filesystem.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  Future<void> openSettings(WidgetTester tester) async {
    await tester.tap(find.byTooltip('Configurações'));
    await tester.pumpAndSettle();
    expect(find.byType(SettingsScreen), findsOneWidget);
  }

  /// Picks [option] from the dropdown in the settings row titled [rowTitle].
  Future<void> chooseInRow(
    WidgetTester tester,
    String rowTitle,
    String option,
  ) async {
    await tester.tap(
      find.descendant(
        of: find.widgetWithText(ListTile, rowTitle),
        // The rows carry differently-typed dropdowns (ThemeMode, String).
        matching: find.byWidgetPredicate((widget) => widget is DropdownButton),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text(option).last);
    await tester.pumpAndSettle();
  }

  testWidgets('switching the language updates the whole UI live', (
    tester,
  ) async {
    await pumpE2EApp(tester);
    expect(find.text('Instantâneo'), findsOneWidget);

    await openSettings(tester);
    await chooseInRow(tester, 'Idioma', 'Inglês (EUA)');

    // The settings screen itself is already in English.
    expect(find.text('Settings'), findsWidgets);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.text('Instant'), findsOneWidget);
    expect(find.text('Instantâneo'), findsNothing);

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('locale_tag'), 'en');
  });

  testWidgets('switching to the dark theme repaints the hub', (tester) async {
    await pumpE2EApp(tester);
    expect(
      Theme.of(tester.element(find.text('Instantâneo'))).brightness,
      Brightness.light,
    );

    await openSettings(tester);
    await chooseInRow(tester, 'Tema', 'Escuro');
    await tester.tap(find.byTooltip('Voltar'));
    await tester.pumpAndSettle();

    expect(
      Theme.of(tester.element(find.text('Instantâneo'))).brightness,
      Brightness.dark,
    );
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('theme_mode'), 'dark');
  });

  testWidgets('the timer delay chosen in settings drives the timer capture', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    await openSettings(tester);
    // Drag the slider to its minimum, one second.
    await tester.drag(find.byType(Slider), const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(app.container.read(settingsControllerProvider).timerDelaySeconds, 1);

    await tester.tap(find.byTooltip('Voltar'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Temporizador'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(100, 100), const Offset(300, 200));

    expect(app.capture.regionCalls, 0);
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(app.capture.regionCalls, 1);
  });

  testWidgets('rebinding the hotkey re-registers it with the desktop', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);
    expect(app.desktop.attachedHotkey, 'PrintScreen');

    await openSettings(tester);
    await chooseInRow(tester, 'Atalho de captura', 'F9');
    await tester.pumpAndSettle();

    expect(app.desktop.attachedHotkey, 'F9');
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('capture_hotkey'), 'F9');

    // The new binding still triggers a capture.
    await tester.tap(find.byTooltip('Voltar'));
    await tester.pumpAndSettle();
    app.desktop.pressHotkey();
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(40, 40), const Offset(140, 140));
    expect(find.byType(ThumbnailTile), findsOneWidget);
  });

  testWidgets('copy from the gallery writes a PNG to the clipboard', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Copiar'));
    await tester.pumpAndSettle();

    // Two writes: the capture copies itself, then the button copies it again.
    expect(app.clipboard.writes, hasLength(2));
    expect(find.text('Copiado para a área de transferência'), findsOneWidget);
  });

  testWidgets('a clipboard-less session reports the failure', (tester) async {
    final app = await pumpE2EApp(tester);
    app.clipboard.available = false;

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Copiar'));
    await tester.pumpAndSettle();

    expect(
      find.text('Não foi possível copiar para a área de transferência'),
      findsOneWidget,
    );
  });

  testWidgets('save from the gallery writes the file to disk', (tester) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Salvar'));
    await tester.pumpAndSettle();

    expect(app.output.savedPaths, hasLength(1));
    expect(File(app.output.savedPaths.single).existsSync(), isTrue);
    expect(File(app.output.savedPaths.single).lengthSync(), greaterThan(0));
    expect(find.textContaining('Salvo em '), findsOneWidget);
  });

  testWidgets('cancelling the save dialog writes nothing and says nothing', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);
    app.output.cancelled = true;

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Salvar'));
    await tester.pumpAndSettle();

    expect(app.output.savedPaths, isEmpty);
    expect(find.textContaining('Salvo em '), findsNothing);
  });

  testWidgets('left-clicking the tray icon opens the hub window', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    app.desktop.clickTrayIcon();
    await tester.pumpAndSettle();

    expect(app.desktop.calls, contains('showWindow'));
  });
}
