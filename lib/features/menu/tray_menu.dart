import '../../core/l10n/gen/app_localizations.dart';
import '../../core/tray/tray_service.dart';

/// Localized labels for the tray context menu (SPEC §2.4: the tray menu mirrors
/// the core actions). Rebuilt whenever the locale changes.
Map<TrayAction, String> trayMenuLabels(AppLocalizations l10n) {
  return {
    TrayAction.show: l10n.showWindow,
    TrayAction.instant: l10n.captureInstant,
    TrayAction.timer: l10n.captureTimer,
    TrayAction.settings: l10n.settingsTitle,
    TrayAction.quit: l10n.quit,
  };
}
