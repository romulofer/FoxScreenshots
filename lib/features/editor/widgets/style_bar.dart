import 'package:flutter/material.dart';

import '../../../core/l10n/gen/app_localizations.dart';
import '../models/annotation_palette.dart';

/// Ink color and stroke width for the next annotation (SPEC §2.2: "color +
/// stroke-width picker").
class StyleBar extends StatelessWidget {
  const StyleBar({
    required this.color,
    required this.strokeWidth,
    required this.onColorSelected,
    required this.onStrokeWidthChanged,
    super.key,
  });

  final Color color;
  final double strokeWidth;
  final ValueChanged<Color> onColorSelected;
  final ValueChanged<double> onStrokeWidthChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;

    return Row(
      children: [
        Text(l10n.editorColor, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(width: 8),
        for (final swatch in AnnotationPalette.colors)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 3),
            child: _Swatch(
              color: swatch,
              selected: swatch == color,
              onSelected: () => onColorSelected(swatch),
            ),
          ),
        const SizedBox(width: 20),
        Text(
          l10n.editorThickness,
          style: Theme.of(context).textTheme.labelLarge,
        ),
        Expanded(
          child: Slider(
            value: strokeWidth.clamp(
              AnnotationPalette.minStrokeWidth,
              AnnotationPalette.maxStrokeWidth,
            ),
            min: AnnotationPalette.minStrokeWidth,
            max: AnnotationPalette.maxStrokeWidth,
            divisions:
                (AnnotationPalette.maxStrokeWidth -
                        AnnotationPalette.minStrokeWidth)
                    .round(),
            label: '${strokeWidth.round()}',
            onChanged: onStrokeWidthChanged,
          ),
        ),
        SizedBox(
          width: 32,
          child: Text(
            '${strokeWidth.round()}',
            textAlign: TextAlign.end,
            style: Theme.of(context).textTheme.labelLarge
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
        ),
      ],
    );
  }
}

class _Swatch extends StatelessWidget {
  const _Swatch({
    required this.color,
    required this.selected,
    required this.onSelected,
  });

  final Color color;
  final bool selected;
  final VoidCallback onSelected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      selected: selected,
      button: true,
      // The color has no name to announce; the value is the swatch itself.
      label:
          '#${(color.toARGB32() & 0xFFFFFF).toRadixString(16).padLeft(6, '0')}',
      child: InkWell(
        onTap: onSelected,
        customBorder: const CircleBorder(),
        child: Container(
          width: 24,
          height: 24,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: Border.all(
              // A white swatch would vanish on a light surface without this.
              color: selected ? scheme.primary : scheme.outlineVariant,
              width: selected ? 3 : 1,
            ),
          ),
        ),
      ),
    );
  }
}
