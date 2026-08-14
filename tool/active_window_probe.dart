// Reports what the X backend sees as "the active window" after the hub hides,
// with a window on a secondary monitor activated on purpose.
//
// Run: flutter run -d linux -t tool/active_window_probe.dart
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:foxscreenshots/core/capture/x11_screen_capture_service.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:window_manager/window_manager.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await windowManager.ensureInitialized();
  await windowManager.waitUntilReadyToShow(
    const WindowOptions(size: Size(920, 640), title: 'active window probe'),
    () async => windowManager.show(),
  );
  await Future<void>.delayed(const Duration(milliseconds: 600));

  final displays = await screenRetriever.getAllDisplays();
  for (final display in displays) {
    stdout.writeln(
      'display ${display.name} pos=${display.visiblePosition} size=${display.size}',
    );
  }
  final secondary = displays.length > 1 ? displays.last : displays.first;
  final edge = (secondary.visiblePosition ?? Offset.zero).dx;

  // Pick a window that lives on the secondary monitor and make it the active
  // one, the way the user would before pressing "active window".
  final list = await Process.run('wmctrl', ['-lG']);
  String? target;
  for (final line in (list.stdout as String).split('\n')) {
    final parts = line.split(RegExp(r'\s+'));
    if (parts.length < 8) continue;
    final x = int.tryParse(parts[2]) ?? -1;
    if (x >= edge && !line.contains('probe')) {
      target = parts[0];
      stdout.writeln('activating $line');
      break;
    }
  }
  if (target == null) {
    stdout.writeln('no window found on the secondary monitor');
    exit(1);
  }
  await Process.run('wmctrl', ['-i', '-a', target]);
  await Future<void>.delayed(const Duration(milliseconds: 500));

  const service = X11ScreenCaptureService();
  stdout.writeln('before hide: ${await service.activeWindowRegion()}');

  await windowManager.hide();
  await Future<void>.delayed(const Duration(milliseconds: 300));
  stdout.writeln('after hide:  ${await service.activeWindowRegion()}');
  stdout.writeln('again:       ${await service.activeWindowRegion()}');

  final active = await Process.run('xprop', ['-root', '_NET_ACTIVE_WINDOW']);
  stdout.writeln('_NET_ACTIVE_WINDOW=${(active.stdout as String).trim()}');

  exit(0);
}
