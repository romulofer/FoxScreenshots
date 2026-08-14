import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/editor/editor_screen.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:foxscreenshots/features/editor/widgets/editor_canvas.dart';
import 'package:foxscreenshots/features/home/session_controller.dart';
import 'package:foxscreenshots/features/home/widgets/thumbnail_tile.dart';
import 'package:integration_test/integration_test.dart';

import 'helpers/e2e_app.dart';

/// End-to-end editing (SPEC §2.2, §2.3): capture, annotate, then copy, save, or
/// push the result back into the session gallery.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  /// Captures the full screen and opens the editor on the resulting thumbnail.
  Future<E2EApp> captureAndEdit(WidgetTester tester) async {
    final app = await pumpE2EApp(tester);

    await tester.tap(find.text('Tela cheia'));
    await tester.pumpAndSettle();
    expect(find.byType(ThumbnailTile), findsOneWidget);

    await tester.tap(find.byTooltip('Editar'));
    await tester.pumpAndSettle();
    expect(find.byType(EditorScreen), findsOneWidget);

    return app;
  }

  testWidgets('anotar, aplicar, e a galeria mostra a imagem editada', (
    tester,
  ) async {
    final app = await captureAndEdit(tester);
    final original = app.container.read(sessionControllerProvider).single;

    await tester.tap(find.byTooltip('Retângulo'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(300, 250), const Offset(500, 400));

    await tester.tap(find.text('Aplicar na captura'));
    await tester.pumpAndSettle();

    // Back on the hub, with the flattened image in place of the original.
    expect(find.byType(EditorScreen), findsNothing);
    expect(find.text('Alterações aplicadas'), findsOneWidget);

    final edited = app.container.read(sessionControllerProvider).single;
    expect(
      edited.id,
      original.id,
      reason: 'a entrada da galeria é substituída',
    );
    expect(edited.pngBytes, flattenedMarkerBytes);
  });

  testWidgets('desfazer e refazer percorrem as marcas', (tester) async {
    await captureAndEdit(tester);

    await tester.tap(find.byTooltip('Seta'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(300, 250), const Offset(450, 350));
    await dragRect(tester, const Offset(320, 300), const Offset(480, 380));

    final canvas = tester.widget<EditorCanvas>(find.byType(EditorCanvas));
    expect(canvas.annotations, hasLength(2));

    await tester.tap(find.byTooltip('Desfazer'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<EditorCanvas>(find.byType(EditorCanvas)).annotations,
      hasLength(1),
    );

    await tester.tap(find.byTooltip('Refazer'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<EditorCanvas>(find.byType(EditorCanvas)).annotations,
      hasLength(2),
    );
  });

  testWidgets('o texto é digitado em um diálogo antes de ser colocado', (
    tester,
  ) async {
    await captureAndEdit(tester);

    await tester.tap(find.byTooltip('Texto'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(EditorCanvas));
    await tester.pumpAndSettle();

    expect(find.text('Adicionar texto'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'olhe aqui');
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();

    final annotations = tester
        .widget<EditorCanvas>(find.byType(EditorCanvas))
        .annotations;
    expect(annotations.single, isA<TextAnnotation>());
    expect((annotations.single as TextAnnotation).text, 'olhe aqui');
  });

  testWidgets('as ferramentas de tarja cobrem uma região da captura', (
    tester,
  ) async {
    await captureAndEdit(tester);

    await tester.tap(find.byTooltip('Pixelar'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(300, 250), const Offset(420, 330));

    final annotations = tester
        .widget<EditorCanvas>(find.byType(EditorCanvas))
        .annotations;
    expect(annotations.single, isA<RedactionAnnotation>());
    expect(
      (annotations.single as RedactionAnnotation).style,
      RedactionStyle.pixelate,
    );
  });

  testWidgets('copiar do editor põe o PNG achatado na área de transferência', (
    tester,
  ) async {
    final app = await captureAndEdit(tester);

    await tester.tap(find.byTooltip('Retângulo'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(300, 250), const Offset(500, 400));

    await tester.tap(find.byTooltip('Copiar'));
    await tester.pumpAndSettle();

    // The capture copied itself when it was taken; this is the edited version.
    expect(app.clipboard.writes.last, flattenedMarkerBytes);
    expect(find.text('Copiado para a área de transferência'), findsOneWidget);
  });

  testWidgets('salvar do editor grava um PNG e informa o caminho', (
    tester,
  ) async {
    final app = await captureAndEdit(tester);

    await tester.tap(find.byTooltip('Retângulo'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(300, 250), const Offset(500, 400));

    await tester.tap(find.byTooltip('Salvar'));
    await tester.pumpAndSettle();

    expect(app.output.savedPaths, hasLength(1));
    expect(find.textContaining('Salvo em '), findsOneWidget);
  });

  testWidgets('sair com marcas não salvas pergunta antes', (tester) async {
    final app = await captureAndEdit(tester);
    final original = app.container.read(sessionControllerProvider).single;

    await tester.tap(find.byTooltip('Retângulo'));
    await tester.pumpAndSettle();
    await dragRect(tester, const Offset(300, 250), const Offset(500, 400));

    await tester.tap(find.byTooltip('Voltar'));
    await tester.pumpAndSettle();
    expect(find.text('Descartar alterações?'), findsOneWidget);

    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(find.byType(EditorScreen), findsOneWidget);

    await tester.tap(find.byTooltip('Voltar'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Descartar'));
    await tester.pumpAndSettle();

    expect(find.byType(EditorScreen), findsNothing);
    expect(
      app.container.read(sessionControllerProvider).single.pngBytes,
      original.pngBytes,
      reason: 'uma edição descartada não pode chegar à galeria',
    );
  });

  testWidgets('uma captura intocada sai do editor sem perguntar nada', (
    tester,
  ) async {
    await captureAndEdit(tester);

    await tester.tap(find.byTooltip('Voltar'));
    await tester.pumpAndSettle();

    expect(find.text('Descartar alterações?'), findsNothing);
    expect(find.byType(EditorScreen), findsNothing);
  });
}
