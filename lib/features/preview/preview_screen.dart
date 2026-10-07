import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/l10n/gen/app_localizations.dart';
import '../../models/capture_result.dart';

/// Full-size, read-only look at a session capture, without opening the editor.
/// Scroll/pinch zooms, drag pans, Esc or the close button dismisses.
class PreviewScreen extends StatelessWidget {
  const PreviewScreen({required this.capture, super.key});

  final CaptureResult capture;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.escape): () =>
            Navigator.of(context).maybePop(),
      },
      child: Focus(
        autofocus: true,
        child: Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(
            backgroundColor: Colors.black,
            foregroundColor: Colors.white,
            title: Text(
              '${l10n.previewTitle} · ${capture.width}×${capture.height}',
            ),
            leading: IconButton(
              tooltip: l10n.actionClose,
              icon: const Icon(Icons.close),
              onPressed: () => Navigator.of(context).maybePop(),
            ),
          ),
          body: Tooltip(
            message: l10n.previewHint,
            child: InteractiveViewer(
              maxScale: 8,
              child: Center(
                child: Image.memory(
                  capture.pngBytes,
                  fit: BoxFit.contain,
                  width: double.infinity,
                  height: double.infinity,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
