import 'package:flutter/material.dart';

import '../../../core/l10n/gen/app_localizations.dart';
import '../models/editor_tool.dart';

/// Vertical rail of editor tools (SPEC §2.2).
class ToolRail extends StatelessWidget {
  const ToolRail({required this.selected, required this.onSelected, super.key});

  final EditorTool selected;
  final ValueChanged<EditorTool> onSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;

    return Material(
      color: scheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(12),
      // Scrollable: the full set of tools is taller than a short window, and a
      // clipped rail would hide the last tool with no way to reach it.
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final tool in EditorTool.values)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: IconButton(
                  tooltip: toolLabel(l10n, tool),
                  isSelected: tool == selected,
                  onPressed: () => onSelected(tool),
                  icon: Icon(toolIcon(tool)),
                  style: IconButton.styleFrom(
                    backgroundColor: tool == selected
                        ? scheme.primaryContainer
                        : Colors.transparent,
                    foregroundColor: tool == selected
                        ? scheme.onPrimaryContainer
                        : scheme.onSurfaceVariant,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

IconData toolIcon(EditorTool tool) => switch (tool) {
  EditorTool.crop => Icons.crop,
  EditorTool.arrow => Icons.north_east,
  EditorTool.rectangle => Icons.rectangle_outlined,
  EditorTool.ellipse => Icons.circle_outlined,
  EditorTool.highlight => Icons.border_color_outlined,
  EditorTool.blur => Icons.blur_on,
  EditorTool.pixelate => Icons.grid_on,
  EditorTool.pen => Icons.gesture,
  EditorTool.text => Icons.text_fields,
  EditorTool.step => Icons.looks_one_outlined,
};

String toolLabel(AppLocalizations l10n, EditorTool tool) => switch (tool) {
  EditorTool.crop => l10n.toolCrop,
  EditorTool.arrow => l10n.toolArrow,
  EditorTool.rectangle => l10n.toolRectangle,
  EditorTool.ellipse => l10n.toolEllipse,
  EditorTool.highlight => l10n.toolHighlight,
  EditorTool.blur => l10n.toolBlur,
  EditorTool.pixelate => l10n.toolPixelate,
  EditorTool.pen => l10n.toolPen,
  EditorTool.text => l10n.toolText,
  EditorTool.step => l10n.toolNumber,
};
