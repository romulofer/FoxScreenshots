import 'package:flutter/foundation.dart';

/// Bundled tray icon to hand to `tray_manager` on [platform].
///
/// The plugin passes the path straight to the OS, so the format has to match
/// what that OS can read: the Windows backend calls `LoadImage(IMAGE_ICON)`,
/// which only accepts `.ico` and silently yields a blank icon for a PNG — and a
/// blank tray icon leaves no way back to a window that closes to the tray
/// (SPEC §1). The AppIndicator and macOS backends load the PNG.
String trayIconAsset([TargetPlatform? platform]) {
  return (platform ?? defaultTargetPlatform) == TargetPlatform.windows
      ? 'assets/icon.ico'
      : 'assets/icon.png';
}
