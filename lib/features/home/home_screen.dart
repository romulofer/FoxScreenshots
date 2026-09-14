import 'dart:typed_data';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../core/l10n/gen/app_localizations.dart';
import '../../core/storage/clipboard_service.dart';
import '../../core/storage/output_service.dart';
import '../../models/capture_result.dart';
import '../capture/capture_controller.dart';
import '../capture/capture_failure_message.dart';
import '../editor/editor_screen.dart';
import '../settings/settings_screen.dart';
import 'session_controller.dart';
import 'widgets/capture_toolbar.dart';
import 'widgets/dependency_banner.dart';
import 'widgets/thumbnail_tile.dart';

/// Shutter-like hub window (SPEC §2.5): capture toolbar on top, session gallery
/// below. Left-clicking the tray icon opens this window.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionControllerProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.appTitle),
        actions: [
          IconButton(
            tooltip: l10n.actionSaveAll,
            icon: const Icon(Icons.save_alt_outlined),
            onPressed: session.isEmpty
                ? null
                : () => _onSaveAll(context, ref, session),
          ),
          IconButton(
            tooltip: l10n.actionClearSession,
            icon: const Icon(Icons.delete_sweep_outlined),
            onPressed: session.isEmpty ? null : () => _onClear(context, ref),
          ),
          IconButton(
            tooltip: l10n.settingsTitle,
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => const SettingsScreen()),
            ),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const DependencyBanner(),
            CaptureToolbar(onCapture: (mode) => _onCapture(context, ref, mode)),
            const SizedBox(height: 16),
            Row(
              children: [
                Text(
                  l10n.sessionGalleryTitle,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(width: 8),
                if (session.isNotEmpty)
                  Text(
                    l10n.sessionCount(
                      session.length,
                      SessionController.maxSessionCaptures,
                    ),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Expanded(
              child: session.isEmpty
                  ? _EmptyState(message: l10n.sessionEmpty)
                  : GridView.builder(
                      gridDelegate:
                          const SliverGridDelegateWithMaxCrossAxisExtent(
                            maxCrossAxisExtent: 240,
                            childAspectRatio: 4 / 3,
                            crossAxisSpacing: 12,
                            mainAxisSpacing: 12,
                          ),
                      itemCount: session.length,
                      itemBuilder: (context, i) {
                        final capture = session[i];
                        return ThumbnailTile(
                          capture: capture,
                          onEdit: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => EditorScreen(capture: capture),
                            ),
                          ),
                          onCopy: () => _onCopy(context, ref, capture.pngBytes),
                          onSave: () => _onSave(context, ref, capture.pngBytes),
                          onDelete: () => ref
                              .read(sessionControllerProvider.notifier)
                              .remove(capture.id),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _onCapture(
    BuildContext context,
    WidgetRef ref,
    CaptureMode mode,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(captureControllerProvider).capture(mode);
    } on CaptureException catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text(captureFailureMessage(l10n, e))),
      );
    }
  }

  Future<void> _onCopy(
    BuildContext context,
    WidgetRef ref,
    Uint8List pngBytes,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final ok = await ref.read(clipboardServiceProvider).copyPng(pngBytes);
    messenger.showSnackBar(
      SnackBar(
        content: Text(ok ? l10n.copiedToClipboard : l10n.copyToClipboardFailed),
      ),
    );
  }

  /// Drops the whole session after a confirmation. Files already written to
  /// disk are untouched — only the in-memory gallery is cleared.
  Future<void> _onClear(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.clearSessionTitle),
        content: Text(l10n.clearSessionMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.actionCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.actionClear),
          ),
        ],
      ),
    );
    if (confirmed ?? false) {
      ref.read(sessionControllerProvider.notifier).clear();
    }
  }

  /// Writes every session capture into a folder the user picks. Reports how
  /// many landed; a single write failure aborts and shows the generic error.
  Future<void> _onSaveAll(
    BuildContext context,
    WidgetRef ref,
    List<CaptureResult> session,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final dir = await getDirectoryPath();
    if (dir == null) return;
    final output = ref.read(outputServiceProvider);
    try {
      for (final capture in session) {
        await output.savePngToDir(capture.pngBytes, dir);
      }
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.savedCountTo(session.length, dir))),
      );
    } catch (_) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.saveAllFailed)));
    }
  }

  Future<void> _onSave(
    BuildContext context,
    WidgetRef ref,
    Uint8List pngBytes,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final path = await ref
          .read(outputServiceProvider)
          .savePngWithDialog(pngBytes);
      if (path != null) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.savedTo(path))));
      }
    } catch (_) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.saveFailed)));
    }
  }
}

/// Placeholder shown while the session has no captures.
class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.photo_library_outlined,
            size: 64,
            color: theme.colorScheme.onSurfaceVariant,
          ),
          const SizedBox(height: 16),
          Text(
            message,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
