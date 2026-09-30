import 'package:flutter/foundation.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/tray/tray_service.dart';

/// A [DesktopIntegration] that keeps the callbacks it is handed instead of
/// wiring a real tray/hotkey, so a test can fire them by hand — the only way to
/// exercise the tray/hotkey capture paths without a desktop embedder.
class FakeDesktopIntegration implements DesktopIntegration {
  VoidCallback? onHotkey;
  VoidCallback? onTimerHotkey;
  VoidCallback? onRepeatHotkey;
  void Function(TrayAction action)? onTrayAction;

  @override
  Future<void> attach({
    required String iconPath,
    required String tooltip,
    required Map<TrayAction, String> labels,
    required VoidCallback onOpenWindow,
    required void Function(TrayAction action) onTrayAction,
    required VoidCallback onHotkey,
    required VoidCallback onTimerHotkey,
    required VoidCallback onRepeatHotkey,
    String hotkey = 'PrintScreen',
    String timerHotkey = 'F7',
    String repeatHotkey = 'F6',
  }) async {
    this.onHotkey = onHotkey;
    this.onTimerHotkey = onTimerHotkey;
    this.onRepeatHotkey = onRepeatHotkey;
    this.onTrayAction = onTrayAction;
  }

  @override
  Future<void> hideWindow() async {}

  @override
  Future<void> showWindow() async {}

  @override
  Future<void> quit() async {}
}
