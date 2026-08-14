import 'dart:typed_data';

import 'package:foxscreenshots/core/storage/clipboard_service.dart';

/// Records clipboard writes instead of touching the system clipboard, which
/// needs a real desktop portal.
class RecordingClipboardService implements ClipboardService {
  final List<Uint8List> writes = <Uint8List>[];

  /// Set to false to act like a session with no writable clipboard.
  bool available = true;

  @override
  Future<bool> copyPng(Uint8List pngBytes) async {
    if (!available) return false;
    writes.add(pngBytes);
    return true;
  }
}
