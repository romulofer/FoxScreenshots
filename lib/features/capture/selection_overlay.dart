import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/l10n/gen/app_localizations.dart';
import '../../core/theme/app_colors.dart';
import '../../models/capture_region.dart';
import 'screen_mapping.dart';
import 'widgets/selection_painter.dart';

/// Fullscreen overlay with a rubber-band selection (SPEC §2.1).
///
/// Two shapes, one widget:
///
/// * **Frozen** (instant mode) — [backdrop] holds a screenshot of the whole
///   virtual screen, so what the user drags over is a still image and open
///   menus or tooltips stay in the frame.
/// * **Live** (timer mode) — [backdrop] is `null` and the window is
///   see-through, so the user frames a region on the moving desktop; the shot
///   is taken later, after the delay.
///
/// [mapping] says which slice of the screen sits under the overlay window, so
/// a frozen backdrop lines up pixel-for-pixel with the real desktop; it updates
/// once the window manager has placed the window. Selections are reported in
/// screen pixels.
class CaptureSelectionOverlay extends StatefulWidget {
  const CaptureSelectionOverlay({
    required this.backdrop,
    required this.screenWidth,
    required this.screenHeight,
    required this.mapping,
    required this.onSelected,
    required this.onCancel,
    super.key,
  });

  /// Frozen frame to draw, or `null` to select over the live desktop.
  final ui.Image? backdrop;

  /// Virtual screen size in physical pixels — the bounds a selection is
  /// clipped to. Not read from [backdrop] because live mode has none.
  final int screenWidth;
  final int screenHeight;
  final ValueListenable<ScreenMapping> mapping;

  /// Called once with the chosen region, in screenshot pixels.
  final ValueChanged<CaptureRegion> onSelected;
  final VoidCallback onCancel;

  /// Drags shorter than this (in logical pixels) are treated as a stray click
  /// rather than a selection.
  static const double minDragExtent = 4;

  @override
  State<CaptureSelectionOverlay> createState() =>
      _CaptureSelectionOverlayState();
}

class _CaptureSelectionOverlayState extends State<CaptureSelectionOverlay> {
  final FocusNode _focusNode = FocusNode();
  Offset? _start;
  Offset? _current;
  Offset? _pointer;
  bool _done = false;

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  void _cancel() {
    if (_done) return;
    _done = true;
    widget.onCancel();
  }

  void _finish(ScreenMapping mapping) {
    final start = _start;
    final current = _current;
    if (_done || start == null || current == null) return;

    if ((current - start).distance < CaptureSelectionOverlay.minDragExtent) {
      setState(() {
        _start = null;
        _current = null;
      });
      return;
    }

    final region = mapping.toRegion(
      start,
      current,
      imageWidth: widget.screenWidth,
      imageHeight: widget.screenHeight,
    );
    if (region.isEmpty) return;
    _done = true;
    widget.onSelected(region);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colors = Theme.of(context).extension<FoxColors>() ?? FoxColors.dark;

    return Focus(
      focusNode: _focusNode,
      autofocus: true,
      onKeyEvent: (_, event) {
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.escape) {
          _cancel();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: ValueListenableBuilder<ScreenMapping>(
        valueListenable: widget.mapping,
        builder: (context, mapping, _) {
          return MouseRegion(
            cursor: SystemMouseCursors.precise,
            onHover: (e) => setState(() => _pointer = e.localPosition),
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onSecondaryTap: _cancel,
              onPanStart: (d) => setState(() {
                _start = d.localPosition;
                _current = d.localPosition;
                _pointer = d.localPosition;
              }),
              onPanUpdate: (d) => setState(() {
                _current = d.localPosition;
                _pointer = d.localPosition;
              }),
              onPanEnd: (_) => _finish(mapping),
              child: CustomPaint(
                painter: SelectionPainter(
                  backdrop: widget.backdrop,
                  mapping: mapping,
                  start: _start,
                  current: _current,
                  pointer: _pointer,
                  accent: colors.brand,
                  hint: l10n.selectionHint,
                  textDirection: Directionality.of(context),
                ),
                child: const SizedBox.expand(),
              ),
            ),
          );
        },
      ),
    );
  }
}
