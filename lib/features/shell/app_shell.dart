import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:window_manager/window_manager.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../core/desktop/desktop_integration.dart';
import '../../core/l10n/gen/app_localizations.dart';
import '../../core/navigation/app_navigator.dart';
import '../../core/tray/tray_icon_asset.dart';
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

  /// Bundled tray icon; `tray_manager` resolves it from the asset bundle. The
  /// format is per-OS — see [trayIconAsset].
  static String get trayIconPath => trayIconAsset();

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> with WindowListener {
  Locale? _wiredFor;
  String? _wiredHotkey;
  String? _wiredTimerHotkey;
  String? _wiredRepeatHotkey;

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
    final settings = ref.read(settingsControllerProvider);
    final hotkey = settings.hotkey;
    final timerHotkey = settings.timerHotkey;
    final repeatHotkey = settings.repeatHotkey;
    if (_wiredFor == locale &&
        _wiredHotkey == hotkey &&
        _wiredTimerHotkey == timerHotkey &&
        _wiredRepeatHotkey == repeatHotkey) {
      return;
    }
    _wiredFor = locale;
    _wiredHotkey = hotkey;
    _wiredTimerHotkey = timerHotkey;
    _wiredRepeatHotkey = repeatHotkey;
    _attach(AppLocalizations.of(context), hotkey, timerHotkey, repeatHotkey);
  }

  @override
  void dispose() {
    windowManager.removeListener(this);
    super.dispose();
  }

  Future<void> _attach(
    AppLocalizations l10n,
    String hotkey,
    String timerHotkey,
    String repeatHotkey,
  ) {
    return ref
        .read(desktopIntegrationProvider)
        .attach(
          iconPath: AppShell.trayIconPath,
          tooltip: l10n.appTitle,
          labels: trayMenuLabels(l10n),
          onOpenWindow: _openWindow,
          onTrayAction: _onTrayAction,
          onHotkey: () => _capture(CaptureMode.instant),
          onTimerHotkey: () => _capture(CaptureMode.timer),
          onRepeatHotkey: () => _capture(CaptureMode.repeat),
          hotkey: hotkey,
          timerHotkey: timerHotkey,
          repeatHotkey: repeatHotkey,
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
      case TrayAction.repeat:
        _capture(CaptureMode.repeat);
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
    final messenger = ref.read(scaffoldMessengerKeyProvider).currentState;
    try {
      final result = await ref.read(captureControllerProvider).capture(mode);
      // Tray/hotkey captures fire with the hub hidden, so — unlike the toolbar,
      // where the new thumbnail is the confirmation — there is nothing on screen
      // to say it worked. A restored-window snackbar fills that gap. Null means
      // the user cancelled the selection; stay quiet then.
      if (result != null) {
        messenger?.showSnackBar(SnackBar(content: Text(l10n.captureTaken)));
      }
    } on CaptureException catch (e) {
      messenger?.showSnackBar(
        SnackBar(content: Text(captureFailureMessage(l10n, e))),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(
      settingsControllerProvider.select(
        (s) => (s.hotkey, s.timerHotkey, s.repeatHotkey),
      ),
      (previous, next) {
        final (hotkey, timerHotkey, repeatHotkey) = next;
        if (_wiredHotkey == hotkey &&
            _wiredTimerHotkey == timerHotkey &&
            _wiredRepeatHotkey == repeatHotkey) {
          return;
        }
        _wiredHotkey = hotkey;
        _wiredTimerHotkey = timerHotkey;
        _wiredRepeatHotkey = repeatHotkey;
        _attach(
          AppLocalizations.of(context),
          hotkey,
          timerHotkey,
          repeatHotkey,
        );
      },
    );
    return const HomeScreen();
  }
}
