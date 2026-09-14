import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/features/capture/image_decoder.dart';
import 'package:foxscreenshots/features/editor/editor_controller.dart';
import 'package:foxscreenshots/features/editor/editor_screen.dart';
import 'package:foxscreenshots/models/capture_result.dart';

import '../helpers/fake_capture_service.dart';

/// A blank 1×1 [ui.Image], so the editor's base-image decode resolves without a
/// real engine round trip.
ImageDecoder _stubDecoder() {
  return (_) async {
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder);
    return recorder.endRecording().toImageSync(1, 1);
  };
}

void main() {
  Future<ProviderContainer> pumpEditor(
    WidgetTester tester,
    CaptureResult capture,
  ) async {
    final container = ProviderContainer(
      overrides: [imageDecoderProvider.overrideWithValue(_stubDecoder())],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          locale: const Locale('pt'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: EditorScreen(capture: capture),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return container;
  }

  CaptureResult capture() => CaptureResult(
    id: 'shot-1',
    pngBytes: solidPng(4, 4),
    width: 4,
    height: 4,
    takenAt: DateTime(2026, 1, 1),
  );

  testWidgets('título ganha o marcador • quando há edição não salva', (
    tester,
  ) async {
    final cap = capture();
    final container = await pumpEditor(tester, cap);

    expect(find.text('Editor'), findsOneWidget);
    expect(find.text('• Editor'), findsNothing);

    container
        .read(editorControllerProvider(cap).notifier)
        .addStep(const Offset(2, 2));
    await tester.pumpAndSettle();

    expect(find.text('• Editor'), findsOneWidget);
  });

  testWidgets('Ctrl+Z desfaz a última edição', (tester) async {
    final cap = capture();
    final container = await pumpEditor(tester, cap);
    final controller = container.read(editorControllerProvider(cap).notifier);

    controller.addStep(const Offset(2, 2));
    await tester.pumpAndSettle();
    expect(container.read(editorControllerProvider(cap)).canUndo, isTrue);

    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.keyZ);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    final state = container.read(editorControllerProvider(cap));
    expect(state.canUndo, isFalse);
    expect(state.isDirty, isFalse);
    expect(find.text('• Editor'), findsNothing);
  });

  testWidgets('Ctrl+Y refaz o que foi desfeito', (tester) async {
    final cap = capture();
    final container = await pumpEditor(tester, cap);
    final controller = container.read(editorControllerProvider(cap).notifier);

    controller.addStep(const Offset(2, 2));
    await tester.pumpAndSettle();
    controller.undo();
    await tester.pumpAndSettle();
    expect(container.read(editorControllerProvider(cap)).canRedo, isTrue);

    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.keyY);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await tester.pumpAndSettle();

    final state = container.read(editorControllerProvider(cap));
    expect(state.canRedo, isFalse);
    expect(state.canUndo, isTrue);
  });
}
