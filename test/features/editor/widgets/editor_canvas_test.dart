import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:foxscreenshots/features/editor/models/editor_tool.dart';
import 'package:foxscreenshots/features/editor/widgets/editor_canvas.dart';

import '../../../helpers/test_images.dart';

void main() {
  testWidgets(
    'as anotações comprometidas e o rascunho ficam em camadas separadas, '
    'a comprometida atrás de um RepaintBoundary',
    (tester) async {
      final image = solidImage(width: 100, height: 80);
      addTearDown(image.dispose);

      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          home: EditorCanvas(
            image: image,
            annotations: const [
              RectangleAnnotation(
                id: 'r',
                color: Color(0xFFE53935),
                strokeWidth: 4,
                start: Offset(10, 10),
                end: Offset(40, 40),
              ),
            ],
            draft: null,
            cropDraft: null,
            tool: EditorTool.arrow,
            onDragStart: (_) {},
            onDragUpdate: (_) {},
            onDragEnd: () {},
            onTap: (_) {},
          ),
        ),
      );

      // Base (committed annotations) and draft layer, painted separately so a
      // drag frame only re-executes the small draft painter.
      expect(find.byType(CustomPaint), findsNWidgets(2));
      final boundaries = find.ancestor(
        of: find.byType(CustomPaint).first,
        matching: find.byType(RepaintBoundary),
      );
      expect(
        boundaries,
        findsWidgets,
        reason: 'a camada comprometida precisa poder ser cacheada à parte',
      );
    },
  );

  testWidgets('um rascunho em andamento não derruba a árvore de widgets', (
    tester,
  ) async {
    final image = solidImage(width: 100, height: 80);
    addTearDown(image.dispose);

    Future<void> pump(Annotation? draft) => tester.pumpWidget(
      MaterialApp(
        debugShowCheckedModeBanner: false,
        home: EditorCanvas(
          image: image,
          annotations: const [],
          draft: draft,
          cropDraft: null,
          tool: EditorTool.rectangle,
          onDragStart: (_) {},
          onDragUpdate: (_) {},
          onDragEnd: () {},
          onTap: (_) {},
        ),
      ),
    );

    await pump(null);
    await pump(
      const RectangleAnnotation(
        id: 'draft',
        color: Color(0xFFE53935),
        strokeWidth: 4,
        start: Offset(5, 5),
        end: Offset(30, 25),
      ),
    );

    expect(find.byType(CustomPaint), findsNWidgets(2));
  });
}
