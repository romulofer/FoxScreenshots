import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/diagnostics/dependency_checker.dart';
import '../../../core/l10n/gen/app_localizations.dart';

/// Tells the user which system components are missing, instead of leaving them
/// with a capture button that silently fails.
///
/// Blocking issues (no X11, no display) are styled as errors; degraded ones
/// (no global hotkey, no tray) as a milder notice. Renders nothing when the
/// machine has everything.
class DependencyBanner extends ConsumerStatefulWidget {
  const DependencyBanner({super.key});

  @override
  ConsumerState<DependencyBanner> createState() => _DependencyBannerState();
}

class _DependencyBannerState extends ConsumerState<DependencyBanner> {
  bool _dismissed = false;

  @override
  Widget build(BuildContext context) {
    final issues = ref.watch(dependencyIssuesProvider);
    if (issues.isEmpty || _dismissed) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final blocking = issues.any(
      (i) => i.severity == DependencySeverity.blocking,
    );
    final foreground = blocking
        ? theme.colorScheme.onErrorContainer
        : theme.colorScheme.onSecondaryContainer;

    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      color: blocking
          ? theme.colorScheme.errorContainer
          : theme.colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              blocking ? Icons.error_outline : Icons.info_outline,
              color: foreground,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    blocking
                        ? l10n.dependencyTitleBlocking
                        : l10n.dependencyTitleDegraded,
                    style: theme.textTheme.titleSmall?.copyWith(
                      color: foreground,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 6),
                  for (final issue in issues)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 2),
                      child: Text(
                        '• ${dependencyMessage(l10n, issue.dependency)}',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: foreground,
                        ),
                      ),
                    ),
                ],
              ),
            ),
            TextButton(
              onPressed: () => setState(() => _dismissed = true),
              child: Text(
                l10n.dependencyDismiss,
                style: TextStyle(color: foreground),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Localized explanation (plus the package to install) for one missing
/// component (SPEC §2.6).
String dependencyMessage(AppLocalizations l10n, SystemDependency dependency) {
  return switch (dependency) {
    SystemDependency.x11Library => l10n.dependencyX11Library,
    SystemDependency.xDisplay => l10n.dependencyXDisplay,
    SystemDependency.waylandSession => l10n.dependencyWaylandSession,
    SystemDependency.keybinder => l10n.dependencyKeybinder,
    SystemDependency.appIndicator => l10n.dependencyAppIndicator,
  };
}
