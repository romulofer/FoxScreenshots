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

  group('anotações de forma', () {
    test('normalizam os cantos, para qualquer lado que o arrasto tenha ido', () {
      final downRight = rectangle(const Offset(10, 20), const Offset(40, 60));
      final upLeft = rectangle(const Offset(40, 60), const Offset(10, 20));

      expect(downRight.rect, const Rect.fromLTRB(10, 20, 40, 60));
      expect(upLeft.rect, downRight.rect);
    });

    test('dragTo move o canto final e mantém o inicial', () {
      final dragged = rectangle(
        const Offset(10, 10),
        const Offset(20, 20),
      ).dragTo(const Offset(50, 70));

      expect(dragged.start, const Offset(10, 10));
      expect(dragged.end, const Offset(50, 70));
    });

    test('um clique que não andou não vira marca', () {
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
        reason: 'um arrasto largo e achatado ainda é uma forma proposital',
      );
    });
  });

  group('seta', () {
    test('conta o comprimento, não a caixa delimitadora', () {
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

    test('a ponta cresce junto com a espessura', () {
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

    test('os limites cobrem a ponta, que passa da linha', () {
      final horizontal = arrow(const Offset(0, 50), const Offset(80, 50));
      expect(horizontal.bounds.height, greaterThan(0));
    });
  });

  group('caneta', () {
    test('coleta todos os pontos do arrasto', () {
      var pen = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 3,
        points: const [Offset(1, 1)],
      );
      pen = pen.dragTo(const Offset(5, 5)).dragTo(const Offset(9, 2));

      expect(pen.points, const [Offset(1, 1), Offset(5, 5), Offset(9, 2)]);
    });

    test('funde movimentos menores que cerca de um pixel no ponto anterior', () {
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

    test('um ponto só já é uma marca legítima', () {
      final dot = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 3,
        points: const [Offset(4, 4)],
      );
      expect(dot.isMeaningful, isTrue);
    });

    test('os limites envolvem todos os pontos mais a espessura', () {
      final pen = PenAnnotation(
        id: 'p',
        color: color,
        strokeWidth: 4,
        points: const [Offset(10, 10), Offset(30, 5), Offset(20, 40)],
      );
      expect(pen.bounds, const Rect.fromLTRB(6, 1, 34, 44));
    });
  });

  group('texto', () {
    test('texto em branco é descartado', () {
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

    test('o tamanho da fonte acompanha o controle de espessura', () {
      expect(TextAnnotation.fontSizeFor(4), 4 * TextAnnotation.fontScale);
    });

    test('é colocado por um toque, então arrastar não o move', () {
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

  group('tarja', () {
    RedactionAnnotation redaction(RedactionStyle style, double strokeWidth) =>
        RedactionAnnotation(
          id: 'x',
          color: color,
          strokeWidth: strokeWidth,
          start: Offset.zero,
          end: const Offset(50, 20),
          style: style,
        );

    test('a intensidade acompanha a espessura', () {
      expect(
        redaction(RedactionStyle.blur, 4).sigma,
        4 * RedactionAnnotation.strengthScale,
      );
    });

    test('o bloco nunca fica abaixo de 2, senão a pixelagem não faz nada', () {
      expect(redaction(RedactionStyle.pixelate, 0.1).blockSize, 2);
    });

    test('mantém o estilo durante o arrasto', () {
      final dragged = redaction(
        RedactionStyle.pixelate,
        4,
      ).dragTo(const Offset(90, 90));
      expect(dragged.style, RedactionStyle.pixelate);
      expect(dragged.rect.right, 90);
    });
  });

  group('ferramentas do editor', () {
    test('texto e marcador numerado são por toque; o resto é por arrasto', () {
      expect(EditorTool.text.isDragTool, isFalse);
      expect(EditorTool.step.isDragTool, isFalse);
      expect(EditorTool.arrow.isDragTool, isTrue);
      expect(EditorTool.crop.isDragTool, isTrue);
    });

    test('o recorte é a única ferramenta que não adiciona camada', () {
      expect(EditorTool.crop.createsAnnotation, isFalse);
      for (final tool in EditorTool.values.where((t) => t != EditorTool.crop)) {
        expect(tool.createsAnnotation, isTrue, reason: '$tool');
      }
    });
  });
}
