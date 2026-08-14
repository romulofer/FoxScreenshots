import '../../core/capture/screen_capture_service.dart';
import '../../core/l10n/gen/app_localizations.dart';

/// Maps a [CaptureException] to a localized message (SPEC §2.6).
///
/// Backends raise a [CaptureFailure] code rather than English prose, so the
/// user-facing wording lives in the ARB files like every other string.
String captureFailureMessage(AppLocalizations l10n, CaptureException error) {
  return switch (error.failure) {
    CaptureFailure.waylandUnsupported => l10n.captureFailedWayland,
    CaptureFailure.portalDenied => l10n.captureFailedPortalDenied,
    CaptureFailure.portalUnavailable => l10n.captureFailedPortalUnavailable,
    CaptureFailure.platformUnsupported => l10n.captureFailedPlatform,
    CaptureFailure.displayUnavailable => l10n.captureFailedDisplay,
    CaptureFailure.noActiveWindow => l10n.captureFailedNoActiveWindow,
    CaptureFailure.windowNotReady => l10n.captureFailedWindowNotReady,
  };
}
