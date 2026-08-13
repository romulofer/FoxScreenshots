import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:tray_manager/tray_manager.dart';

/// Actions the tray context menu can raise (SPEC §1).
enum TrayAction { instant, timer, settings, quit }

/// Manages the system-tray icon and its context menu. Left-click opens the main
/// window; right-click shows the menu. Wraps `tray_manager`.
class TrayService with TrayListener {
  TrayService();

  void Function()? _onOpenWindow;
  void Function(TrayAction action)? _onAction;

  /// Installs the tray icon + menu and wires callbacks. [iconPath] is a bundled
  /// asset path resolved by the plugin per-OS (`.ico` on Windows, `.png` else).
  Future<void> init({
    required String iconPath,
    required String tooltip,
    required Map<TrayAction, String> labels,
    required void Function() onOpenWindow,
    required void Function(TrayAction action) onAction,
  }) async {
    _onOpenWindow = onOpenWindow;
    _onAction = onAction;
    trayManager.addListener(this);
    await trayManager.setIcon(iconPath);
    // The Linux (AppIndicator) backend implements neither tooltips nor
    // programmatic menu popups; both are no-ops there rather than errors.
    await _ignoreUnsupported(() => trayManager.setToolTip(tooltip));
    await trayManager.setContextMenu(_buildMenu(labels));
  }

  /// Runs [call], swallowing the `MissingPluginException` raised by tray
  /// features a platform does not implement.
  Future<void> _ignoreUnsupported(Future<void> Function() call) async {
    try {
      await call();
    } on MissingPluginException {
      // Optional tray feature; the icon and menu still work.
    }
  }

  Menu _buildMenu(Map<TrayAction, String> labels) {
    return Menu(
      items: [
        MenuItem(
          key: TrayAction.instant.name,
          label: labels[TrayAction.instant],
        ),
        MenuItem(key: TrayAction.timer.name, label: labels[TrayAction.timer]),
        MenuItem.separator(),
        MenuItem(
          key: TrayAction.settings.name,
          label: labels[TrayAction.settings],
        ),
        MenuItem(key: TrayAction.quit.name, label: labels[TrayAction.quit]),
      ],
    );
  }

  Future<void> dispose() async {
    trayManager.removeListener(this);
    await trayManager.destroy();
  }

  @override
  void onTrayIconMouseDown() => _onOpenWindow?.call();

  @override
  void onTrayIconRightMouseDown() {
    _ignoreUnsupported(trayManager.popUpContextMenu);
  }

  @override
  void onTrayMenuItemClick(MenuItem menuItem) {
    final action = TrayAction.values
        .where((a) => a.name == menuItem.key)
        .cast<TrayAction?>()
        .firstWhere((a) => a != null, orElse: () => null);
    if (action != null) _onAction?.call(action);
  }
}

final trayServiceProvider = Provider<TrayService>((ref) {
  final service = TrayService();
  ref.onDispose(service.dispose);
  return service;
});
