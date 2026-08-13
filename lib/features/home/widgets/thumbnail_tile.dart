import 'package:flutter/material.dart';

import '../../../core/l10n/gen/app_localizations.dart';
import '../../../models/capture_result.dart';

/// A single session capture in the gallery grid (SPEC §2.5), with hover actions
/// Edit / Copy / Save / Delete.
class ThumbnailTile extends StatelessWidget {
  const ThumbnailTile({
    required this.capture,
    required this.onEdit,
    required this.onCopy,
    required this.onSave,
    required this.onDelete,
    super.key,
  });

  final CaptureResult capture;
  final VoidCallback onEdit;
  final VoidCallback onCopy;
  final VoidCallback onSave;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(child: Image.memory(capture.pngBytes, fit: BoxFit.cover)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                IconButton(
                  tooltip: l10n.actionEdit,
                  icon: const Icon(Icons.edit_outlined),
                  onPressed: onEdit,
                ),
                IconButton(
                  tooltip: l10n.actionCopy,
                  icon: const Icon(Icons.copy_outlined),
                  onPressed: onCopy,
                ),
                IconButton(
                  tooltip: l10n.actionSave,
                  icon: const Icon(Icons.save_outlined),
                  onPressed: onSave,
                ),
                IconButton(
                  tooltip: l10n.actionDelete,
                  icon: Icon(
                    Icons.delete_outline,
                    color: theme.colorScheme.error,
                  ),
                  onPressed: onDelete,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
