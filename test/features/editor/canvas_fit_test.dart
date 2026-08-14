import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/editor/widgets/canvas_fit.dart';

void main() {
  test('centraliza uma imagem larga com tarjas nas bordas', () {
    final fit = CanvasFit.contain(
      imageSize: const Size(800, 400),
      viewport: const Size(400, 400),
    );

    expect(fit.scale, 0.5);
    expect(fit.offset, const Offset(0, 100));
    expect(fit.destination, const Rect.fromLTWH(0, 100, 400, 200));
  });

  test('nunca amplia além de 1:1', () {
    final fit = CanvasFit.contain(
      imageSize: const Size(100, 50),
      viewport: const Size(1000, 1000),
    );

    expect(
      fit.scale,
      1.0,
      reason: 'ampliar uma captura pequena só deixa ela borrada',
    );
  });

  test('traduz um ponto do widget de volta para pixels da imagem', () {
    final fit = CanvasFit.contain(
      imageSize: const Size(800, 400),
      viewport: const Size(400, 400),
    );

    // Center of the painted area is the center of the image.
    expect(fit.toImage(const Offset(200, 200)), const Offset(400, 200));
    expect(fit.toImage(const Offset(0, 100)), Offset.zero);
  });

  test('limita pontos arrastados para fora da imagem', () {
    final fit = CanvasFit.contain(
      imageSize: const Size(800, 400),
      viewport: const Size(400, 400),
    );

    // Above the letterbox and past the right edge.
    expect(fit.toImage(const Offset(-50, 0)), Offset.zero);
    expect(fit.toImage(const Offset(900, 900)), const Offset(800, 400));
  });

  test('sobrevive a um viewport de tamanho zero no primeiro layout', () {
    final fit = CanvasFit.contain(
      imageSize: const Size(800, 400),
      viewport: Size.zero,
    );

    expect(fit.scale, 1.0);
    expect(fit.toImage(const Offset(10, 10)), const Offset(10, 10));
  });
}
