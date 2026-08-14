import 'dart:io';
import 'dart:typed_data';

import 'package:file_selector/file_selector.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;

/// Saves screenshots to disk as PNG (SPEC §2.3). Local filesystem only.
class OutputService {
  const OutputService();

  static const List<int> _pngMagic = [
    0x89,
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
  ];

  /// Prompts for a location and writes [pngBytes]. Returns the saved path, or
  /// `null` if the user cancelled. [initialDirectory] remembers the last folder.
  Future<String?> savePngWithDialog(
    Uint8List pngBytes, {
    String? initialDirectory,
    String? suggestedName,
  }) async {
    _assertPng(pngBytes);
    final location = await getSaveLocation(
      initialDirectory: initialDirectory,
      suggestedName: suggestedName ?? _defaultName(),
      acceptedTypeGroups: const [
        XTypeGroup(label: 'PNG', extensions: ['png']),
      ],
    );
    if (location == null) return null;
    return _writeBytes(location.path, pngBytes);
  }

  /// Writes [pngBytes] straight to [directory] with a timestamped name (used by
  /// the auto-save option). Creates [directory] if missing.
  ///
  /// Only the app-chosen file name is written under the resolved directory —
  /// callers cannot inject path segments into the file name.
  Future<String> savePngToDir(Uint8List pngBytes, String directory) async {
    _assertPng(pngBytes);
    final dir = Directory(directory);
    await dir.create(recursive: true);
    final root = await dir.resolveSymbolicLinks();
    return _writeBytes(p.join(root, _defaultName()), pngBytes);
  }

  void _assertPng(Uint8List bytes) {
    if (bytes.length < _pngMagic.length) {
      throw ArgumentError.value(bytes.length, 'pngBytes', 'too short for PNG');
    }
    for (var i = 0; i < _pngMagic.length; i++) {
      if (bytes[i] != _pngMagic[i]) {
        throw ArgumentError.value(bytes, 'pngBytes', 'not a PNG');
      }
    }
  }

  Future<String> _writeBytes(String path, Uint8List bytes) async {
    final file = File(path);
    await file.writeAsBytes(bytes, flush: true);
    return file.path;
  }

  String _defaultName() {
    final now = DateTime.now();
    String two(int n) => n.toString().padLeft(2, '0');
    return 'foxshot_${now.year}${two(now.month)}${two(now.day)}'
        '_${two(now.hour)}${two(now.minute)}${two(now.second)}.png';
  }
}

final outputServiceProvider = Provider<OutputService>((ref) {
  return const OutputService();
});
