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
          // Each action takes an equal share of the tile width: four fixed-size
          // icon buttons overflow once the grid packs tiles below ~200px.
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Row(
              children: [
                _TileAction(
                  tooltip: l10n.actionEdit,
                  icon: Icons.edit_outlined,
                  onPressed: onEdit,
                ),
                _TileAction(
                  tooltip: l10n.actionCopy,
                  icon: Icons.copy_outlined,
                  onPressed: onCopy,
                ),
                _TileAction(
                  tooltip: l10n.actionSave,
                  icon: Icons.save_outlined,
                  onPressed: onSave,
                ),
                _TileAction(
                  tooltip: l10n.actionDelete,
                  icon: Icons.delete_outline,
                  color: theme.colorScheme.error,
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

/// One action in the tile footer, sized to an equal share of the row.
class _TileAction extends StatelessWidget {
  const _TileAction({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    this.color,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: IconButton(
        tooltip: tooltip,
        padding: EdgeInsets.zero,
        visualDensity: VisualDensity.compact,
        iconSize: 20,
        constraints: const BoxConstraints(minWidth: 32, minHeight: 36),
        icon: Icon(icon, color: color),
        onPressed: onPressed,
      ),
    );
  }
}
