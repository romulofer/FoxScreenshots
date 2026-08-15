import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/editor/editor_compositor.dart';
import 'package:foxscreenshots/features/editor/editor_controller.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:foxscreenshots/features/editor/models/editor_tool.dart';

import '../../helpers/test_images.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final capture = fakeCapture(width: 100, height: 80);

  /// Container with a decoder that hands back a real (but synthetic) image, so
  /// no engine decode is needed under the test clock.
  ProviderContainer makeContainer({int width = 100, int height = 80}) {
    final container = ProviderContainer(
      overrides: [
        imageDecoderProvider.overrideWithValue(
          (bytes) async => solidImage(width: width, height: height),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  /// Reads the notifier and keeps the auto-disposed provider alive for the test.
  EditorController controllerOf(ProviderContainer container) {
    final provider = editorControllerProvider(capture);
    container.listen(provider, (_, _) {});
    return container.read(provider.notifier);
  }

  EditorState stateOf(ProviderContainer container) =>
      container.read(editorControllerProvider(capture));

  /// One complete drag with the currently selected tool.
  Future<void> drag(EditorController controller, Offset from, Offset to) async {
    controller
      ..startDraft(from)
      ..updateDraft(to);
    await controller.endDraft();
  }

  test('decodifica a captura e informa o tamanho da imagem', () async {
    final container = makeContainer(width: 120, height: 90);
    controllerOf(container);

    expect(stateOf(container).document.image, isNull);
    await pumpEventQueue();

    final state = stateOf(container);
    expect(state.document.image, isNotNull);
    expect(state.document.width, 120);
    expect(state.document.height, 90);
    expect(state.isDirty, isFalse, reason: 'decodificar não é editar');
  });

  test('um arrasto confirma uma anotação e libera o desfazer', () async {
    final container = makeContainer();
    final controller = controllerOf(container)
      ..selectTool(EditorTool.rectangle);
    await pumpEventQueue();

    await drag(controller, const Offset(10, 10), const Offset(60, 50));

    final state = stateOf(container);
    expect(state.annotations, hasLength(1));
    expect(state.annotations.single, isA<RectangleAnnotation>());
    expect(
      state.draft,
      isNull,
      reason: 'o rascunho é confirmado, não guardado',
    );
    expect(state.canUndo, isTrue);
    expect(state.canRedo, isFalse);
    expect(state.isDirty, isTrue);
  });

  test('o rascunho em andamento aparece antes de ser confirmado', () async {
    final container = makeContainer();
    final controller = controllerOf(container)..selectTool(EditorTool.arrow);
    await pumpEventQueue();

    controller
      ..startDraft(const Offset(5, 5))
      ..updateDraft(const Offset(70, 40));

    final state = stateOf(container);
    expect(state.annotations, isEmpty);
    expect(state.visibleAnnotations, hasLength(1));
    expect(
      state.canUndo,
      isFalse,
      reason: 'um arrasto em andamento ainda não é histórico',
    );
  });

  test('um clique solto não deixa nada para trás', () async {
    final container = makeContainer();
    final controller = controllerOf(container)..selectTool(EditorTool.ellipse);
    await pumpEventQueue();

    await drag(controller, const Offset(10, 10), const Offset(11, 11));

    expect(stateOf(container).annotations, isEmpty);
    expect(stateOf(container).canUndo, isFalse);
  });

  test('as ferramentas de toque ignoram arrastos', () async {
    final container = makeContainer();
    final controller = controllerOf(container)..selectTool(EditorTool.step);
    await pumpEventQueue();

    await drag(controller, const Offset(10, 10), const Offset(60, 60));

    expect(stateOf(container).annotations, isEmpty);
  });

  test('trocar de ferramenta descarta a forma pela metade', () async {
    final container = makeContainer();
    final controller = controllerOf(container)..selectTool(EditorTool.pen);
    await pumpEventQueue();

    controller
      ..startDraft(const Offset(10, 10))
      ..updateDraft(const Offset(20, 20))
      ..selectTool(EditorTool.arrow);

    expect(stateOf(container).draft, isNull);
    expect(stateOf(container).annotations, isEmpty);
  });

  test('desfazer e refazer percorrem o histórico', () async {
    final container = makeContainer();
    final controller = controllerOf(container)
      ..selectTool(EditorTool.rectangle);
    await pumpEventQueue();

    await drag(controller, const Offset(0, 0), const Offset(30, 30));
    await drag(controller, const Offset(40, 40), const Offset(70, 70));
    expect(stateOf(container).annotations, hasLength(2));

    controller.undo();
    expect(stateOf(container).annotations, hasLength(1));
    expect(stateOf(container).canRedo, isTrue);

    controller.undo();
    expect(stateOf(container).annotations, isEmpty);
    expect(stateOf(container).canUndo, isFalse);
    expect(stateOf(container).isDirty, isFalse, reason: 'de volta ao original');

    controller.redo();
    expect(stateOf(container).annotations, hasLength(1));

    controller.redo();
    expect(stateOf(container).annotations, hasLength(2));
    expect(stateOf(container).canRedo, isFalse);
  });

  test('uma edição nova depois de desfazer limpa o ramo do refazer', () async {
    final container = makeContainer();
    final controller = controllerOf(container)
      ..selectTool(EditorTool.rectangle);
    await pumpEventQueue();

    await drag(controller, const Offset(0, 0), const Offset(30, 30));
    controller.undo();
    expect(stateOf(container).canRedo, isTrue);

    await drag(controller, const Offset(5, 5), const Offset(40, 40));
    expect(stateOf(container).canRedo, isFalse);
    expect(stateOf(container).annotations, hasLength(1));
  });

  test(
    'os marcadores numerados seguem a ordem em que foram colocados',
    () async {
      final container = makeContainer();
      final controller = controllerOf(container)..selectTool(EditorTool.step);
      await pumpEventQueue();

      controller
        ..addStep(const Offset(10, 10))
        ..addStep(const Offset(30, 30))
        ..addStep(const Offset(50, 50));

      expect(
        stateOf(container).annotations
            .cast<StepAnnotation>()
            .map((a) => a.number),
        [1, 2, 3],
      );
    },
  );

  test('desfazer um marcador libera o número dele para o próximo', () async {
    final container = makeContainer();
    final controller = controllerOf(container)..selectTool(EditorTool.step);
    await pumpEventQueue();

    controller
      ..addStep(const Offset(10, 10))
      ..addStep(const Offset(30, 30))
      ..undo()
      ..addStep(const Offset(60, 60));

    expect(
      stateOf(container).annotations
          .cast<StepAnnotation>()
          .map((a) => a.number),
      [1, 2],
    );
  });

  test('texto vazio não é colocado', () async {
    final container = makeContainer();
    final controller = controllerOf(container);
    await pumpEventQueue();

    controller.addText(const Offset(10, 10), '   ');
    expect(stateOf(container).annotations, isEmpty);

    controller.addText(const Offset(10, 10), 'hello');
    expect(stateOf(container).annotations, hasLength(1));
    expect(
      (stateOf(container).annotations.single as TextAnnotation).fontSize,
      TextAnnotation.fontSizeFor(EditorController.defaultStrokeWidth),
    );
  });

  test('anotações novas assumem a cor e a espessura selecionadas', () async {
    final container = makeContainer();
    final controller = controllerOf(container)
      ..selectTool(EditorTool.arrow)
      ..selectColor(const Color(0xFF1E88E5))
      ..setStrokeWidth(9);
    await pumpEventQueue();

    await drag(controller, const Offset(5, 5), const Offset(70, 40));

    final annotation = stateOf(container).annotations.single;
    expect(annotation.color, const Color(0xFF1E88E5));
    expect(annotation.strokeWidth, 9);
  });

  test(
    'o achatamento reaproveita os bytes originais quando nada foi desenhado',
    () async {
      final container = makeContainer();
      controllerOf(container);
      await pumpEventQueue();

      final flattened = await container
          .read(editorControllerProvider(capture).notifier)
          .flatten();

      expect(flattened, isNotNull);
      expect(
        identical(flattened!.pngBytes, capture.pngBytes),
        isTrue,
        reason: 'uma captura sem alteração não deve ser recodificada',
      );
    },
  );

  test('o achatamento passa as anotações pelo compositor', () async {
    late List<Annotation> seen;
    final container = ProviderContainer(
      overrides: [
        imageDecoderProvider.overrideWithValue((bytes) async => solidImage()),
        imageFlattenerProvider.overrideWithValue(({
          required ui.Image base,
          required List<Annotation> annotations,
          required TextDirection textDirection,
        }) async {
          seen = annotations;
          return FlattenedImage(
            image: base,
            pngBytes: Uint8List.fromList(const [1, 2, 3]),
          );
        }),
      ],
    );
    addTearDown(container.dispose);

    final controller = controllerOf(container)
      ..selectTool(EditorTool.rectangle);
    await pumpEventQueue();
    await drag(controller, const Offset(0, 0), const Offset(40, 40));

    final flattened = await controller.flatten();

    expect(seen, hasLength(1));
    expect(flattened!.pngBytes, [1, 2, 3]);
  });

  test('markApplied limpa a marca de alterações não salvas', () async {
    final container = makeContainer();
    final controller = controllerOf(container)..selectTool(EditorTool.pen);
    await pumpEventQueue();

    await drag(controller, const Offset(1, 1), const Offset(20, 20));
    expect(stateOf(container).isDirty, isTrue);

    controller.markApplied();
    expect(stateOf(container).isDirty, isFalse);
    expect(
      stateOf(container).canUndo,
      isTrue,
      reason: 'aplicar não joga o histórico fora',
    );
  });

  testWidgets('o recorte corta a imagem base e leva as anotações junto', (
    tester,
  ) async {
    // Decoding and rasterization are real engine work: the whole flow has to
    // run in a live async zone, not against the test clock.
    late ProviderContainer container;
    await tester.runAsync(() async {
      container = makeContainer(width: 100, height: 80);
      final controller = controllerOf(container)
        ..selectTool(EditorTool.rectangle);
      await pumpEventQueue();

      await drag(controller, const Offset(40, 30), const Offset(60, 50));

      controller.selectTool(EditorTool.crop);
      await drag(controller, const Offset(20, 10), const Offset(80, 70));
    });

    final state = stateOf(container);
    expect(state.document.width, 60);
    expect(state.document.height, 60);

    final moved = state.annotations.single as RectangleAnnotation;
    expect(moved.start, const Offset(20, 20));
    expect(moved.end, const Offset(40, 40));
    expect(state.isBusy, isFalse);
  });

  testWidgets(
    'um recorte arrastado para cima e para a esquerda mantém a âncora',
    (tester) async {
      late ProviderContainer container;
      await tester.runAsync(() async {
        container = makeContainer(width: 100, height: 80);
        final controller = controllerOf(container)..selectTool(EditorTool.crop);
        await pumpEventQueue();

        // Drag from the bottom-right corner back towards the origin, passing
        // through points that keep moving the rectangle's own top-left.
        controller
          ..startDraft(const Offset(90, 70))
          ..updateDraft(const Offset(50, 40))
          ..updateDraft(const Offset(30, 20));
        await controller.endDraft();
      });

      final state = stateOf(container);
      expect(state.document.width, 60, reason: '90 - 30, ancorado no início');
      expect(state.document.height, 50);
    },
  );

  testWidgets('um ramo de refazer descartado libera o bitmap que segurava', (
    tester,
  ) async {
    late ProviderContainer container;
    late ui.Image original;
    late ui.Image cropped;

    await tester.runAsync(() async {
      container = makeContainer(width: 100, height: 80);
      final controller = controllerOf(container)..selectTool(EditorTool.crop);
      await pumpEventQueue();
      original = stateOf(container).document.image!;

      await drag(controller, const Offset(10, 10), const Offset(60, 60));
      cropped = stateOf(container).document.image!;

      // Undo parks the cropped image on the redo branch; the next edit throws
      // that branch away, and with it the only reference to those pixels.
      controller
        ..undo()
        ..selectTool(EditorTool.rectangle);
      await drag(controller, const Offset(0, 0), const Offset(30, 30));
    });

    expect(cropped.debugDisposed, isTrue);
    expect(
      original.debugDisposed,
      isFalse,
      reason: 'a imagem que ainda está na tela precisa sobreviver',
    );
  });

  testWidgets('desfazer restaura a imagem que o recorte substituiu', (
    tester,
  ) async {
    late ProviderContainer container;
    late EditorController controller;
    await tester.runAsync(() async {
      container = makeContainer(width: 100, height: 80);
      controller = controllerOf(container)..selectTool(EditorTool.crop);
      await pumpEventQueue();
      await drag(controller, const Offset(10, 10), const Offset(50, 50));
    });
    expect(stateOf(container).document.width, 40);

    controller.undo();

    final state = stateOf(container);
    expect(state.document.width, 100);
    expect(state.document.height, 80);
    expect(state.document.image, isNotNull);
  });
}
