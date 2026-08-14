import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:window_manager/window_manager.dart';

import '../hotkey/hotkey_service.dart';
import '../tray/tray_service.dart';

/// The desktop-only behaviours that make the app live in the tray (SPEC §1):
/// tray icon and menu, the global capture hotkey, and hide-instead-of-close.
///
/// Behind an interface because all three need a real embedder; widget tests
/// override the provider with [NoopDesktopIntegration].
abstract interface class DesktopIntegration {
  /// Installs the tray icon and the global hotkey. Safe to call again to
  /// refresh [labels] after a locale change, or [hotkey] after a rebind.
  Future<void> attach({
    required String iconPath,
    required String tooltip,
    required Map<TrayAction, String> labels,
    required VoidCallback onOpenWindow,
    required void Function(TrayAction action) onTrayAction,
    required VoidCallback onHotkey,
    String hotkey = 'PrintScreen',
  });

  /// Hides the window instead of closing it, keeping the app in the tray.
  Future<void> hideWindow();

  /// Shows and focuses the hub window.
  Future<void> showWindow();

  /// Tears the tray icon and hotkey down, then closes the app for good.
  Future<void> quit();
}

/// Real implementation over `tray_manager`, `hotkey_manager` and
/// `window_manager`.
class WindowManagerDesktopIntegration implements DesktopIntegration {
  WindowManagerDesktopIntegration(this._tray, this._hotkeys);

  final TrayService _tray;
  final HotkeyService _hotkeys;
  bool _attached = false;

  @override
  Future<void> attach({
    required String iconPath,
    required String tooltip,
    required Map<TrayAction, String> labels,
    required VoidCallback onOpenWindow,
    required void Function(TrayAction action) onTrayAction,
    required VoidCallback onHotkey,
    String hotkey = 'PrintScreen',
  }) async {
    await _tray.init(
      iconPath: iconPath,
      tooltip: tooltip,
      labels: labels,
      onOpenWindow: onOpenWindow,
      onAction: onTrayAction,
    );
    if (!_attached) {
      // Closing the window must not end the session: the app keeps running in
      // the tray until Quit is chosen explicitly (SPEC §1).
      await windowManager.setPreventClose(true);
      _attached = true;
    }
    await _hotkeys.registerCapture(onHotkey, binding: hotkey);
  }

  @override
  Future<void> hideWindow() => windowManager.hide();

  @override
  Future<void> showWindow() async {
    await windowManager.show();
    await windowManager.focus();
  }

  @override
  Future<void> quit() async {
    await _hotkeys.reset();
    await _tray.dispose();
    await windowManager.setPreventClose(false);
    await windowManager.destroy();
  }
}

/// Does nothing; used by widget tests, which have no tray or window server.
class NoopDesktopIntegration implements DesktopIntegration {
  const NoopDesktopIntegration();

  @override
  Future<void> attach({
    required String iconPath,
    required String tooltip,
    required Map<TrayAction, String> labels,
    required VoidCallback onOpenWindow,
    required void Function(TrayAction action) onTrayAction,
    required VoidCallback onHotkey,
    String hotkey = 'PrintScreen',
  }) async {}

  @override
  Future<void> hideWindow() async {}

  @override
  Future<void> showWindow() async {}

  @override
  Future<void> quit() async {}
}

final desktopIntegrationProvider = Provider<DesktopIntegration>((ref) {
  return WindowManagerDesktopIntegration(
    ref.watch(trayServiceProvider),
    ref.watch(hotkeyServiceProvider),
  );
});
