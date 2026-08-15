import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:window_manager/window_manager.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../core/desktop/desktop_integration.dart';
import '../../core/l10n/gen/app_localizations.dart';
import '../../core/navigation/app_navigator.dart';
import '../../core/tray/tray_service.dart';
import '../capture/capture_controller.dart';
import '../capture/capture_failure_message.dart';
import '../home/home_screen.dart';
import '../menu/tray_menu.dart';
import '../settings/settings_controller.dart';
import '../settings/settings_screen.dart';

/// Hosts the hub window and owns the desktop-level wiring (SPEC §1): tray icon,
/// global capture hotkey, and closing the window to the tray rather than
/// quitting.
///
/// Captures triggered from the tray or the hotkey have no `BuildContext` of
/// their own, so they run through the same [CaptureController] as the toolbar
/// and report failures on the app-wide messenger (see
/// [scaffoldMessengerKeyProvider]), which outlives any single route.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  /// Bundled tray icon; `tray_manager` resolves it from the asset bundle.
  static const String trayIconPath = 'assets/icon.png';

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> with WindowListener {
  Locale? _wiredFor;
  String? _wiredHotkey;

  @override
  void initState() {
    super.initState();
    windowManager.addListener(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Re-attach on the first build and after every locale change, so the tray
    // menu is never left in the previous language (SPEC §2.6).
    final locale = Localizations.localeOf(context);
    final hotkey = ref.read(settingsControllerProvider).hotkey;
    if (_wiredFor == locale && _wiredHotkey == hotkey) return;
    _wiredFor = locale;
    _wiredHotkey = hotkey;
    _attach(AppLocalizations.of(context), hotkey);
  }

  @override
  void dispose() {
    windowManager.removeListener(this);
    super.dispose();
  }

  Future<void> _attach(AppLocalizations l10n, String hotkey) {
    return ref
        .read(desktopIntegrationProvider)
        .attach(
          iconPath: AppShell.trayIconPath,
          tooltip: l10n.appTitle,
          labels: trayMenuLabels(l10n),
          onOpenWindow: _openWindow,
          onTrayAction: _onTrayAction,
          onHotkey: () => _capture(CaptureMode.instant),
          hotkey: hotkey,
        );
  }

  @override
  void onWindowClose() {
    // Keep running in the tray; Quit is explicit.
    ref.read(desktopIntegrationProvider).hideWindow();
  }

  Future<void> _openWindow() =>
      ref.read(desktopIntegrationProvider).showWindow();

  void _onTrayAction(TrayAction action) {
    switch (action) {
      case TrayAction.show:
        _openWindow();
      case TrayAction.instant:
        _capture(CaptureMode.instant);
      case TrayAction.timer:
        _capture(CaptureMode.timer);
      case TrayAction.settings:
        _openSettings();
      case TrayAction.quit:
        ref.read(desktopIntegrationProvider).quit();
    }
  }

  Future<void> _openSettings() async {
    await _openWindow();
    if (!mounted) return;
    await Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => const SettingsScreen()));
  }

  Future<void> _capture(CaptureMode mode) async {
    final l10n = AppLocalizations.of(context);
    try {
      await ref.read(captureControllerProvider).capture(mode);
    } on CaptureException catch (e) {
      ref
          .read(scaffoldMessengerKeyProvider)
          .currentState
          ?.showSnackBar(
            SnackBar(content: Text(captureFailureMessage(l10n, e))),
          );
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(settingsControllerProvider.select((s) => s.hotkey), (
      previous,
      next,
    ) {
      if (previous == next || _wiredHotkey == next) return;
      _wiredHotkey = next;
      _attach(AppLocalizations.of(context), next);
    });
    return const HomeScreen();
  }
}
