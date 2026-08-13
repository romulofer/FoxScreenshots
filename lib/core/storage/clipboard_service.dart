import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:super_clipboard/super_clipboard.dart';

/// Writes a captured image to the system clipboard as PNG (SPEC §2.3).
class ClipboardService {
  const ClipboardService();

  /// Returns `false` if the platform exposes no writable clipboard.
  Future<bool> copyPng(Uint8List pngBytes) async {
    final clipboard = SystemClipboard.instance;
    if (clipboard == null) return false;

    final item = DataWriterItem();
    item.add(Formats.png(pngBytes));
    await clipboard.write([item]);
    return true;
  }
}

final clipboardServiceProvider = Provider<ClipboardService>((ref) {
  return const ClipboardService();
});
