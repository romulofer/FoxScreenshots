// Drives the real selection overlay without any user input, then grabs the
// root window so the result can be inspected across every monitor.
//
// Run: flutter run -d linux -t tool/overlay_probe.dart
// Output: /tmp/foxscreenshots_live/overlay_probe.png
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:foxscreenshots/core/capture/x11_screen_capture_service.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/screen_mapping.dart';
import 'package:foxscreenshots/features/capture/selection_overlay.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:window_manager/window_manager.dart';

final GlobalKey<NavigatorState> navKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await windowManager.ensureInitialized();
  await windowManager.waitUntilReadyToShow(
    const WindowOptions(size: Size(920, 640), title: 'overlay probe'),
    () async => windowManager.show(),
  );

  runApp(
    MaterialApp(
      navigatorKey: navKey,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const Scaffold(backgroundColor: Colors.indigo),
    ),
  );

  // Park the hub on the last monitor before capturing: an overlay opened from
  // a secondary screen is the case that used to map the frozen frame onto the
  // wrong monitor.
  final displays = await screenRetriever.getAllDisplays();
  final parkOn =
      (displays.last.visiblePosition ?? Offset.zero) + const Offset(180, 120);
  await windowManager.setBounds(Rect.fromLTWH(parkOn.dx, parkOn.dy, 920, 640));
  await Future<void>.delayed(const Duration(seconds: 1));
  await _wmctrl('hub');
  await _probe();
  exit(0);
}

Future<void> _wmctrl(String tag) async {
  final out = await Process.run('wmctrl', ['-lG']);
  for (final line in (out.stdout as String).split('\n')) {
    if (line.toLowerCase().contains('probe')) {
      stdout.writeln('wmctrl[$tag] $line');
    }
  }
}

Future<void> _probe() async {
  const service = X11ScreenCaptureService();
  final window = WindowManagerCaptureWindow();

  await window.hideForCapture();
  final frozen = await service.grabFullVirtualScreen();
  stdout.writeln('grab=${frozen.width}x${frozen.height}');

  final backdrop = await decodeImageFromList(frozen.pngBytes);

  final requested = await window.enterOverlay();
  stdout.writeln('requested=$requested');
  final mapping = ValueNotifier<ScreenMapping>(
    ScreenMapping.fromPlacement(
      requested,
      imageWidth: frozen.width,
      imageHeight: frozen.height,
    ),
  );
  stdout.writeln('mapping(requested)=${mapping.value}');

  navKey.currentState!.push<void>(
    PageRouteBuilder<void>(
      transitionDuration: Duration.zero,
      pageBuilder: (context, _, _) => CaptureSelectionOverlay(
        backdrop: backdrop,
        screenWidth: frozen.width,
        screenHeight: frozen.height,
        mapping: mapping,
        onSelected: (_) {},
        onCancel: () {},
      ),
    ),
  );

  final granted = await window.revealOverlay();
  stdout.writeln('granted=$granted');
  mapping.value = ScreenMapping.fromPlacement(
    granted,
    imageWidth: frozen.width,
    imageHeight: frozen.height,
  );
  stdout.writeln('mapping(granted)=${mapping.value}');

  final view = WidgetsBinding.instance.platformDispatcher.views.first;
  await Future<void>.delayed(const Duration(seconds: 2));
  await _wmctrl('overlay');
  stdout.writeln(
    'view.physicalSize=${view.physicalSize} dpr=${view.devicePixelRatio}',
  );

  await Directory('/tmp/foxscreenshots_live').create(recursive: true);
  final shot = await Process.run('import', [
    '-window',
    'root',
    '/tmp/foxscreenshots_live/overlay_probe.png',
  ]);
  stdout.writeln('import exit=${shot.exitCode} ${shot.stderr}');

  await window.leaveOverlay();
}
