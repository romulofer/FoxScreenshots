// Live smoke against the real X11 backend (not fakes).
// Run: dart run tool/live_capture_smoke.dart
import 'dart:io';

import 'package:foxscreenshots/core/capture/x11_screen_capture_service.dart';
import 'package:foxscreenshots/core/diagnostics/dependency_checker.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:image/image.dart' as img;

Future<void> main() async {
  final outDir = Directory('/tmp/foxscreenshots_live');
  await outDir.create(recursive: true);

  stdout.writeln('=== FoxScreenShots live capture smoke ===');
  stdout.writeln('DISPLAY=${Platform.environment['DISPLAY']}');
  stdout.writeln(
    'XDG_SESSION_TYPE=${Platform.environment['XDG_SESSION_TYPE']}',
  );

  final issues = LinuxDependencyChecker().check();
  final blocking = issues
      .where((issue) => issue.severity == DependencySeverity.blocking)
      .toList();
  stdout.writeln('readyForCapture=${blocking.isEmpty}');
  for (final issue in issues) {
    stdout.writeln(
      '  issue: ${issue.dependency.name} (${issue.severity.name})',
    );
  }
  if (blocking.isNotEmpty) {
    stderr.writeln('FAIL: capture dependencies missing');
    exit(2);
  }

  const service = X11ScreenCaptureService();
  const codec = PngCodec.inline();

  final size = await service.virtualScreenSize();
  stdout.writeln('virtualScreenSize=${size.width}x${size.height}');
  if (size.width < 64 || size.height < 64) {
    stderr.writeln('FAIL: virtual screen too small');
    exit(3);
  }

  final full = await service.grabFullVirtualScreen();
  final fullPath = '${outDir.path}/full.png';
  await File(fullPath).writeAsBytes(full.pngBytes);
  final fullDecoded = img.decodePng(full.pngBytes);
  stdout.writeln(
    'full grab=${full.width}x${full.height} bytes=${full.pngBytes.length} -> $fullPath',
  );
  if (fullDecoded == null ||
      fullDecoded.width != full.width ||
      fullDecoded.height != full.height) {
    stderr.writeln('FAIL: full PNG decode mismatch');
    exit(4);
  }

  final region = CaptureRegion(
    x: 10,
    y: 10,
    width: 200,
    height: 120,
  ).clampedTo(size.width, size.height);
  final part = await service.grabRegion(region);
  final partPath = '${outDir.path}/region.png';
  await File(partPath).writeAsBytes(part.pngBytes);
  stdout.writeln(
    'region grab=${part.width}x${part.height} requested=${region.width}x${region.height} -> $partPath',
  );
  if (part.width != region.width || part.height != region.height) {
    stderr.writeln('FAIL: region size mismatch');
    exit(5);
  }

  final cropped = await codec.crop(
    full.pngBytes,
    CaptureRegion(x: 0, y: 0, width: 64, height: 48),
  );
  if (cropped == null || cropped.width != 64 || cropped.height != 48) {
    stderr.writeln('FAIL: crop failed');
    exit(6);
  }
  final cropPath = '${outDir.path}/crop.png';
  await File(cropPath).writeAsBytes(cropped.pngBytes);
  stdout.writeln('crop ok -> $cropPath');

  final active = await service.activeWindowRegion();
  stdout.writeln('activeWindowRegion=$active');

  stdout.writeln('PASS: live capture path works');
}
