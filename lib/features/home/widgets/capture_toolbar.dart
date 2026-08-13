import 'package:flutter/material.dart';

import '../../../core/capture/screen_capture_service.dart';
import '../../../core/l10n/gen/app_localizations.dart';

/// Top capture bar of the hub window (SPEC §2.5): Instant, Timer, Full screen,
/// Active window. Emits the chosen [CaptureMode].
class CaptureToolbar extends StatelessWidget {
  const CaptureToolbar({required this.onCapture, super.key});

  final ValueChanged<CaptureMode> onCapture;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        _CaptureButton(
          icon: Icons.crop_free,
          label: l10n.captureInstant,
          onPressed: () => onCapture(CaptureMode.instant),
        ),
        _CaptureButton(
          icon: Icons.timer_outlined,
          label: l10n.captureTimer,
          onPressed: () => onCapture(CaptureMode.timer),
        ),
        _CaptureButton(
          icon: Icons.fullscreen,
          label: l10n.captureFullScreen,
          onPressed: () => onCapture(CaptureMode.fullScreen),
        ),
        _CaptureButton(
          icon: Icons.web_asset,
          label: l10n.captureActiveWindow,
          onPressed: () => onCapture(CaptureMode.activeWindow),
        ),
      ],
    );
  }
}

class _CaptureButton extends StatelessWidget {
  const _CaptureButton({
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return FilledButton.tonalIcon(
      onPressed: onPressed,
      icon: Icon(icon),
      label: Text(label),
    );
  }
}
