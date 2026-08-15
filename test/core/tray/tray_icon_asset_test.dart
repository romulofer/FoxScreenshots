import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/tray/tray_icon_asset.dart';

void main() {
  group('trayIconAsset', () {
    test('uses the .ico on Windows', () {
      expect(trayIconAsset(TargetPlatform.windows), 'assets/icon.ico');
    });

    test('uses the .png everywhere else', () {
      for (final platform in [TargetPlatform.linux, TargetPlatform.macOS]) {
        expect(trayIconAsset(platform), 'assets/icon.png');
      }
    });
  });

  group('bundled icons', () {
    test('the Windows tray icon is a real ICO', () {
      // `LoadImage(IMAGE_ICON)` accepts nothing else, and it fails silently —
      // a PNG renamed to .ico would ship a blank tray icon.
      final header = File(trayIconAsset(TargetPlatform.windows))
          .readAsBytesSync()
          .sublist(0, 6);
      expect(header.sublist(0, 4), [0x00, 0x00, 0x01, 0x00]); // ICONDIR
      expect(header[4] | header[5] << 8, greaterThan(1)); // several sizes
    });

    test('both icons are declared as assets', () {
      final pubspec = File('pubspec.yaml').readAsStringSync();
      for (final platform in TargetPlatform.values) {
        expect(pubspec, contains('- ${trayIconAsset(platform)}'));
      }
    });
  });
}
