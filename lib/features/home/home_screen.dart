import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../core/l10n/gen/app_localizations.dart';
import '../../core/storage/clipboard_service.dart';
import '../../core/storage/output_service.dart';
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
            Text(
              l10n.sessionGalleryTitle,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Expanded(
              child: session.isEmpty
                  ? Center(child: Text(l10n.sessionEmpty))
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
