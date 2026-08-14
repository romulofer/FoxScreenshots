import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:foxscreenshots/features/editor/models/editor_tool.dart';

void main() {
  const color = Color(0xFFE53935);

  ArrowAnnotation arrow(Offset start, Offset end) => ArrowAnnotation(
    id: 'a',
    color: color,
    strokeWidth: 4,
    start: start,
    end: end,
  );

  RectangleAnnotation rectangle(Offset start, Offset end) =>
      RectangleAnnotation(
        id: 'r',
        color: color,
        strokeWidth: 4,
        start: start,
        end: end,
      );

  group('shape annotations', () {
    test('normalize the dragged corners whichever way the drag went', () {
      final downRight = rectangle(const Offset(10, 20), const Offset(40, 60));
      final upLeft = rectangle(const Offset(40, 60), const Offset(10, 20));

      expect(downRight.rect, const Rect.fromLTRB(10, 20, 40, 60));
      expect(upLeft.rect, downRight.rect);
    });

    test('dragTo moves the end corner and keeps the start', () {
      final dragged = rectangle(
        const Offset(10, 10),
        const Offset(20, 20),
      ).dragTo(const Offset(50, 70));

      expect(dragged.start, const Offset(10, 10));
      expect(dragged.end, const Offset(50, 70));
    });

    test('a click that never moved is not meaningful', () {
      expect(
        rectangle(const Offset(10, 10), const Offset(10, 10)).isMeaningful,
        isFalse,
      );
      expect(
        rectangle(const Offset(10, 10), const Offset(12, 11)).isMeaningful,
        isFalse,
      );
      expect(
        rectangle(const Offset(10, 10), const Offset(30, 10)).isMeaningful,
        isTrue,
        reason: 'a wide but flat drag is still a deliberate shape',
      );
    });
  });

  group('arrow', () {
    test('counts its length, not its bounding box', () {
      // A horizontal arrow has zero height; measuring the box would discard it.
      expect(
        arrow(const Offset(0, 50), const Offset(80, 50)).isMeaningful,
        isTrue,
      );
      expect(
        arrow(const Offset(0, 50), const Offset(2, 51)).isMeaningful,
        isFalse,
      );
    });

    test('head grows with the stroke width', () {
      final thin = ArrowAnnotation(
        id: 'a',
        color: color,
        strokeWidth: 2,
        start: Offset.zero,
        end: const Offset(50, 0),
      );
      final thick = ArrowAnnotation(
        id: 'a',
        color: color,
        strokeWidth: 8,
        start: Offset.zero,
        end: const Offset(50, 0),
      );

      expect(thick.headLength, greaterThan(thin.headLength));
    });

    test('bounds cover the head, which sticks out past the line', () {
      final horizontal = arrow(const Offset(0, 50), const Offset(80, 50));
      expect(horizontal.bounds.height, greaterThan(0));
    });
  });

  group('pen', () {
    test('collects every point of the drag', () {
      var pen = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 3,
        points: const [Offset(1, 1)],
      );
      pen = pen.dragTo(const Offset(5, 5)).dragTo(const Offset(9, 2));

      expect(pen.points, const [Offset(1, 1), Offset(5, 5), Offset(9, 2)]);
    });

    test('folds moves shorter than a pixel-ish into the previous point', () {
      const start = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 3,
        points: [Offset(10, 10)],
      );

      // A pointer that hardly moves must not grow the polyline.
      final jitter = start
          .dragTo(const Offset(10.4, 10.2))
          .dragTo(const Offset(10.8, 10.1));
      expect(jitter.points, hasLength(1));

      // A real move is still recorded.
      expect(start.dragTo(const Offset(14, 10)).points, hasLength(2));
    });

    test('a single dot is a legitimate mark', () {
      final dot = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 3,
        points: const [Offset(4, 4)],
      );
      expect(dot.isMeaningful, isTrue);
    });

    test('bounds wrap every point plus the stroke', () {
      final pen = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 4,
        points: const [Offset(10, 10), Offset(30, 5), Offset(20, 40)],
      );
      expect(pen.bounds, const Rect.fromLTRB(6, 1, 34, 44));
    });
  });

  group('text', () {
    test('blank text is dropped', () {
      TextAnnotation withText(String text) => TextAnnotation(
        id: 't',
        color: color,
        strokeWidth: 4,
        position: Offset.zero,
        text: text,
        fontSize: 24,
      );

      expect(withText('   ').isMeaningful, isFalse);
      expect(withText('hello').isMeaningful, isTrue);
    });

    test('font size follows the stroke slider', () {
      expect(TextAnnotation.fontSizeFor(4), 4 * TextAnnotation.fontScale);
    });

    test('is placed by a tap, so dragging does not move it', () {
      final text = TextAnnotation(
        id: 't',
        color: color,
        strokeWidth: 4,
        position: const Offset(5, 5),
        text: 'hi',
        fontSize: 24,
      );
      expect(identical(text.dragTo(const Offset(90, 90)), text), isTrue);
    });
  });

  group('redaction', () {
    RedactionAnnotation redaction(RedactionStyle style, double strokeWidth) =>
        RedactionAnnotation(
          id: 'x',
          color: color,
          strokeWidth: strokeWidth,
          start: Offset.zero,
          end: const Offset(50, 20),
          style: style,
        );

    test('strength follows the stroke width', () {
      expect(
        redaction(RedactionStyle.blur, 4).sigma,
        4 * RedactionAnnotation.strengthScale,
      );
    });

    test('block size never drops below 2, or pixelation is a no-op', () {
      expect(redaction(RedactionStyle.pixelate, 0.1).blockSize, 2);
    });

    test('keeps its style through a drag', () {
      final dragged = redaction(
        RedactionStyle.pixelate,
        4,
      ).dragTo(const Offset(90, 90));
      expect(dragged.style, RedactionStyle.pixelate);
      expect(dragged.rect.right, 90);
    });
  });

  group('editor tools', () {
    test('text and step place on tap; the rest drag', () {
      expect(EditorTool.text.isDragTool, isFalse);
      expect(EditorTool.step.isDragTool, isFalse);
      expect(EditorTool.arrow.isDragTool, isTrue);
      expect(EditorTool.crop.isDragTool, isTrue);
    });

    test('crop is the only tool that does not add a layer', () {
      expect(EditorTool.crop.createsAnnotation, isFalse);
      for (final tool in EditorTool.values.where((t) => t != EditorTool.crop)) {
        expect(tool.createsAnnotation, isTrue, reason: '$tool');
      }
    });
  });
}
