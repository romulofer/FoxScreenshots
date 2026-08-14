import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:image/image.dart' as img;

void main() {
  const codec = PngCodec();

  Uint8List rgbaGradient(int width, int height) {
    final bytes = Uint8List(width * height * 4);
    for (var i = 0; i < width * height; i++) {
      bytes[i * 4] = i % 256;
      bytes[i * 4 + 1] = 64;
      bytes[i * 4 + 2] = 128;
      bytes[i * 4 + 3] = 255;
    }
    return bytes;
  }

  group('encodeRgba', () {
    test('produz um PNG que decodifica de volta nos mesmos pixels', () async {
      final rgba = rgbaGradient(4, 2);

      final png = await codec.encodeRgba(rgba, width: 4, height: 2);

      final decoded = img.decodePng(png);
      expect(decoded, isNotNull);
      expect(decoded!.width, 4);
      expect(decoded.height, 2);
      final first = decoded.getPixel(0, 0);
      expect([first.r, first.g, first.b, first.a], [0, 64, 128, 255]);
      final second = decoded.getPixel(1, 0);
      expect(second.r, 1);
    });
  });

  group('pngSize', () {
    test('lê as dimensões do cabeçalho, sem decodificar', () async {
      final png = await codec.encodeRgba(
        rgbaGradient(7, 3),
        width: 7,
        height: 3,
      );

      expect(pngSize(png), (width: 7, height: 3));
    });

    test('recusa bytes que não são PNG', () {
      expect(
        () => pngSize(Uint8List.fromList(List.filled(64, 0x42))),
        throwsFormatException,
      );
    });

    test('recusa um arquivo curto demais para ter cabeçalho', () {
      expect(
        () => pngSize(Uint8List.fromList(const [0x89, 0x50, 0x4E, 0x47])),
        throwsFormatException,
      );
    });
  });

  group('crop', () {
    late Uint8List source;

    setUp(() async {
      source = await codec.encodeRgba(
        rgbaGradient(10, 10),
        width: 10,
        height: 10,
      );
    });

    test('devolve o retângulo pedido', () async {
      final cropped = await codec.crop(
        source,
        const CaptureRegion(x: 2, y: 3, width: 4, height: 5),
      );

      expect(cropped, isNotNull);
      expect(cropped!.width, 4);
      expect(cropped.height, 5);
      expect(img.decodePng(cropped.pngBytes)?.width, 4);
    });

    test('limita uma região que transborda a imagem', () async {
      final cropped = await codec.crop(
        source,
        const CaptureRegion(x: 8, y: 8, width: 50, height: 50),
      );

      expect(cropped!.width, 2);
      expect(cropped.height, 2);
    });

    test('devolve null quando a região erra a imagem por completo', () async {
      final cropped = await codec.crop(
        source,
        const CaptureRegion(x: 20, y: 20, width: 5, height: 5),
      );

      expect(cropped, isNull);
    });

    test('recorta a partir do deslocamento certo', () async {
      // Row 0 of the gradient runs 0,1,2… in the red channel, so a crop at
      // x = 3 must start at red = 3.
      final cropped = await codec.crop(
        source,
        const CaptureRegion(x: 3, y: 0, width: 2, height: 1),
      );

      final decoded = img.decodePng(cropped!.pngBytes)!;
      expect(decoded.getPixel(0, 0).r, 3);
    });

    test('rejeita bytes que não são um PNG', () async {
      expect(
        () => codec.crop(
          Uint8List.fromList([1, 2, 3, 4]),
          const CaptureRegion(x: 0, y: 0, width: 1, height: 1),
        ),
        throwsA(isA<FormatException>()),
      );
    });
  });
}
