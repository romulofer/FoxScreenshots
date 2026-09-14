import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/storage/output_service.dart';
import 'package:path/path.dart' as p;

/// Minimal valid 1×1 PNG (magic + IHDR/IDAT/IEND) so `savePngToDir` accepts it.
final _png = Uint8List.fromList([
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, //
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
  0x89,
]);

void main() {
  const service = OutputService();
  late Directory dir;

  setUp(() => dir = Directory.systemTemp.createTempSync('foxshots_test'));
  tearDown(() => dir.deleteSync(recursive: true));

  test('savePngToDir grava o PNG dentro da pasta', () async {
    final path = await service.savePngToDir(_png, dir.path);
    expect(p.isWithin(dir.path, path), isTrue);
    expect(File(path).readAsBytesSync(), _png);
  });

  test('savePngToDir não sobrescreve nomes iguais no mesmo segundo', () async {
    final first = await service.savePngToDir(_png, dir.path);
    final second = await service.savePngToDir(_png, dir.path);
    final third = await service.savePngToDir(_png, dir.path);

    expect({first, second, third}.length, 3);
    expect(dir.listSync().whereType<File>().length, 3);
  });

  test('savePngToDir rejeita bytes que não são PNG', () {
    expect(
      () => service.savePngToDir(Uint8List.fromList([1, 2, 3]), dir.path),
      throwsA(isA<ArgumentError>()),
    );
  });
}
