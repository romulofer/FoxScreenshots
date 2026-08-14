import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/tray/tray_service.dart';
import 'package:foxscreenshots/features/capture/selection_overlay.dart';
import 'package:foxscreenshots/features/home/session_controller.dart';
import 'package:foxscreenshots/features/home/widgets/thumbnail_tile.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:integration_test/integration_test.dart';

import 'helpers/e2e_app.dart';

/// End-to-end capture flows (SPEC §2.1, §2.5): the user drives the real UI from
/// the hub window, the tray, and the global hotkey.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('captura instantânea: da barra até a miniatura na galeria', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    // Nothing captured yet.
    expect(
      find.text('Nenhuma captura ainda. Use a barra acima para começar.'),
      findsOneWidget,
    );

    await tester.tap(find.text('Instantâneo'));
    await tester.pumpAndSettle();

    // The screen is frozen behind a fullscreen overlay.
    expect(find.byType(CaptureSelectionOverlay), findsOneWidget);
    expect(app.window.calls, contains('hideForCapture'));

    // 800×600 window over a 400×300 desktop: overlay pixels are half-scale.
    await dragRect(tester, const Offset(40, 60), const Offset(240, 260));

    expect(find.byType(CaptureSelectionOverlay), findsNothing);
    expect(find.byType(ThumbnailTile), findsOneWidget);
    expect(app.window.calls.last, 'restore');

    final shot = app.container.read(sessionControllerProvider).single;
    expect(shot.width, 100);
    expect(shot.height, 100);
    expect(
      app.clipboard.writes.single,
      same(shot.pngBytes),
      reason: 'a captura vai para a área de transferência sem mais nenhum clique',
    );
  });

  testWidgets('captura instantânea: Esc deixa a galeria vazia', (tester) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Instantâneo'));
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    expect(find.byType(CaptureSelectionOverlay), findsNothing);
    expect(app.container.read(sessionControllerProvider), isEmpty);
    expect(find.byType(ThumbnailTile), findsNothing);
  });

  testWidgets('captura com temporizador: região primeiro, foto depois do atraso', (
    tester,
  ) async {
    final app = await pumpE2EApp(
      tester,
      preferences: {'timer_delay_seconds': 2},
    );

    await tester.tap(find.text('Temporizador'));
    await tester.pumpAndSettle();

    await dragRect(tester, const Offset(100, 100), const Offset(300, 200));

    // Framed, but not shot yet: the delay is what lets the user open a menu.
    expect(app.capture.regionCalls, 0);

    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(app.capture.regionCalls, 1);
    expect(
      app.capture.lastRegion,
      const CaptureRegion(x: 50, y: 50, width: 100, height: 50),
    );
    expect(find.byType(ThumbnailTile), findsOneWidget);
  });

  testWidgets('captura de tela cheia pega a área de trabalho inteira, sem sobreposição', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();

    expect(find.byType(CaptureSelectionOverlay), findsNothing);
    final shot = app.container.read(sessionControllerProvider).single;
    expect(shot.width, 400);
    expect(shot.height, 300);
  });

  testWidgets('captura de janela ativa acusa falha quando nada está em foco', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Janela ativa'));
    await tester.pumpAndSettle();

    expect(find.text('Nenhuma janela ativa para capturar.'), findsOneWidget);
    expect(app.container.read(sessionControllerProvider), isEmpty);
  });

  testWidgets('o atalho global captura sem passar pela janela principal', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);
    expect(app.desktop.attachedHotkey, 'PrintScreen');

    app.desktop.pressHotkey();
    await tester.pumpAndSettle();

    expect(find.byType(CaptureSelectionOverlay), findsOneWidget);
    await dragRect(tester, const Offset(40, 40), const Offset(140, 140));

    expect(find.byType(ThumbnailTile), findsOneWidget);
  });

  testWidgets('o menu da bandeja espelha as ações de captura', (tester) async {
    final app = await pumpE2EApp(tester);

    expect(app.desktop.labels[TrayAction.instant], 'Instantâneo');
    expect(app.desktop.labels[TrayAction.quit], 'Sair');

    app.desktop.chooseTrayAction(TrayAction.instant);
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(40, 40), const Offset(140, 140));

    expect(find.byType(ThumbnailTile), findsOneWidget);

    app.desktop.chooseTrayAction(TrayAction.settings);
    await tester.pumpAndSettle();
    expect(find.text('Configurações'), findsWidgets);
  });

  testWidgets('sair pela bandeja desmonta a integração com o sistema', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    app.desktop.chooseTrayAction(TrayAction.quit);
    await tester.pumpAndSettle();

    expect(app.desktop.calls, contains('quit'));
  });

  testWidgets('uma falha do backend aparece como mensagem localizada', (
    tester,
  ) async {
    final app = await pumpE2EApp(
      tester,
      failure: CaptureFailure.waylandUnsupported,
    );

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'A captura ainda não funciona no Wayland. Entre em uma sessão X11.',
      ),
      findsOneWidget,
    );
    expect(app.container.read(sessionControllerProvider), isEmpty);
  });

  testWidgets('as capturas se acumulam, da mais nova para a mais velha, e podem ser excluídas', (
    tester,
  ) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    expect(find.byType(ThumbnailTile), findsNWidgets(2));

    final newest = app.container.read(sessionControllerProvider).first;
    await tester.tap(find.byTooltip('Excluir').first);
    await tester.pumpAndSettle();

    final left = app.container.read(sessionControllerProvider);
    expect(left, hasLength(1));
    expect(left.single.id, isNot(newest.id));
  });
}
