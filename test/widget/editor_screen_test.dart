import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/editor/editor_compositor.dart';
import 'package:foxscreenshots/features/editor/editor_controller.dart';
import 'package:foxscreenshots/features/editor/editor_screen.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:foxscreenshots/features/editor/widgets/editor_canvas.dart';
import 'package:foxscreenshots/features/home/session_controller.dart';

import '../helpers/test_images.dart';

void main() {
  final capture = fakeCapture(width: 200, height: 160);

  /// PNG bytes the fake compositor hands back, so the test can tell a flattened
  /// export apart from the untouched capture.
  final flattenedBytes = Uint8List.fromList(const [7, 7, 7, 7]);

  ProviderContainer makeContainer() {
    final container = ProviderContainer(
      overrides: [
        imageDecoderProvider.overrideWithValue(
          (bytes) async => solidImage(width: 200, height: 160),
        ),
        // Rasterizing for real needs `runAsync`; the editor's own tests cover
        // the compositor, so here it only has to be observable.
        imageFlattenerProvider.overrideWithValue(
          ({
            required ui.Image base,
            required List<Annotation> annotations,
            required TextDirection textDirection,
          }) async => FlattenedImage(image: base, pngBytes: flattenedBytes),
        ),
      ],
    );
    addTearDown(container.dispose);
    container.read(sessionControllerProvider.notifier).add(capture);
    return container;
  }

  /// Pushes the editor over a hub-like route, so popping has somewhere to go.
  Future<void> pumpEditor(
    WidgetTester tester,
    ProviderContainer container,
  ) async {
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => EditorScreen(capture: capture),
                    ),
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }

  EditorState stateOf(ProviderContainer container) =>
      container.read(editorControllerProvider(capture));

  testWidgets('mostra o trilho de ferramentas com dicas localizadas', (
    tester,
  ) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    expect(find.byType(EditorCanvas), findsOneWidget);
    for (final tooltip in [
      'Crop',
      'Arrow',
      'Rectangle',
      'Ellipse',
      'Highlight',
      'Blur',
      'Pixelate',
      'Pen',
      'Text',
      'Numbered marker',
    ]) {
      expect(find.byTooltip(tooltip), findsOneWidget, reason: tooltip);
    }
  });

  testWidgets('arrastar na área de desenho cria uma anotação', (tester) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    await tester.drag(find.byType(EditorCanvas), const Offset(60, 40));
    await tester.pumpAndSettle();

    expect(stateOf(container).annotations, hasLength(1));
    expect(stateOf(container).annotations.single, isA<ArrowAnnotation>());
  });

  testWidgets('o desfazer fica desabilitado até algo ser desenhado', (
    tester,
  ) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    IconButton undoButton() => tester.widget<IconButton>(
      find.ancestor(
        of: find.byTooltip('Undo'),
        matching: find.byType(IconButton),
      ),
    );
    expect(undoButton().onPressed, isNull);

    await tester.drag(find.byType(EditorCanvas), const Offset(60, 40));
    await tester.pumpAndSettle();
    expect(undoButton().onPressed, isNotNull);

    await tester.tap(find.byTooltip('Undo'));
    await tester.pumpAndSettle();
    expect(stateOf(container).annotations, isEmpty);
  });

  testWidgets('a ferramenta de texto pede o conteúdo antes de colocá-lo', (
    tester,
  ) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    await tester.tap(find.byTooltip('Text'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(EditorCanvas));
    await tester.pumpAndSettle();

    expect(find.text('Add text'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'look here');
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();

    final annotation = stateOf(container).annotations.single;
    expect(annotation, isA<TextAnnotation>());
    expect((annotation as TextAnnotation).text, 'look here');
  });

  testWidgets('cancelar o diálogo de texto não coloca nada', (tester) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    await tester.tap(find.byTooltip('Text'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(EditorCanvas));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(stateOf(container).annotations, isEmpty);
  });

  testWidgets(
    'a ferramenta de marcador numerado coloca as bolinhas por toque',
    (tester) async {
      final container = makeContainer();
      await pumpEditor(tester, container);

      // Last tool on the rail: off-screen in a short window until scrolled to.
      await tester.ensureVisible(find.byTooltip('Numbered marker'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Numbered marker'));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(EditorCanvas));
      await tester.pumpAndSettle();

      expect(stateOf(container).annotations.single, isA<StepAnnotation>());
    },
  );

  testWidgets('aplicar grava a imagem achatada de volta na sessão', (
    tester,
  ) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    await tester.drag(find.byType(EditorCanvas), const Offset(60, 40));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Apply to capture'));
    await tester.pumpAndSettle();

    final stored = container.read(sessionControllerProvider).single;
    expect(stored.id, capture.id, reason: 'a entrada da galeria é substituída');
    expect(stored.pngBytes, flattenedBytes);
    expect(find.text('Changes applied'), findsOneWidget);
    expect(
      find.byType(EditorScreen),
      findsNothing,
      reason: 'aplicar fecha o editor',
    );
  });

  testWidgets('sair com edições não salvas pergunta antes de descartar', (
    tester,
  ) async {
    final container = makeContainer();
    await pumpEditor(tester, container);

    await tester.drag(find.byType(EditorCanvas), const Offset(60, 40));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.text('Discard changes?'), findsOneWidget);

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.byType(EditorScreen), findsOneWidget);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Discard'));
    await tester.pumpAndSettle();
    expect(find.byType(EditorScreen), findsNothing);
  });
}
