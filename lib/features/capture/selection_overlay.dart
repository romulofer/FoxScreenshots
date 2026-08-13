import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/l10n/gen/app_localizations.dart';
import '../../core/theme/app_colors.dart';
import '../../models/capture_region.dart';
import 'widgets/selection_painter.dart';

/// Fullscreen frozen-frame overlay with a rubber-band selection (SPEC §2.1).
///
/// [backdrop] is the already-grabbed screenshot of the whole virtual screen, so
/// what the user drags over is a still image: menus and tooltips stay open in
/// the frame. The selection is reported in **backdrop pixels**, derived from
/// the widget size, which keeps it correct on any display scale factor.
class CaptureSelectionOverlay extends StatefulWidget {
  const CaptureSelectionOverlay({
    required this.backdrop,
    required this.onSelected,
    required this.onCancel,
    super.key,
  });

  final ui.Image backdrop;

  /// Called once with the chosen region, in backdrop pixels.
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

  /// Converts a widget-space rectangle into backdrop pixels.
  CaptureRegion _toImageRegion(Offset a, Offset b, Size widgetSize) {
    final scaleX = widget.backdrop.width / widgetSize.width;
    final scaleY = widget.backdrop.height / widgetSize.height;
    final logical = CaptureRegion.fromPoints(a, b);
    return CaptureRegion(
      x: (logical.x * scaleX).round(),
      y: (logical.y * scaleY).round(),
      width: (logical.width * scaleX).round(),
      height: (logical.height * scaleY).round(),
    ).clampedTo(widget.backdrop.width, widget.backdrop.height);
  }

  void _cancel() {
    if (_done) return;
    _done = true;
    widget.onCancel();
  }

  void _finish(Size widgetSize) {
    final start = _start;
    final current = _current;
    if (_done || start == null || current == null) return;

    final dragged = (current - start).distance;
    if (dragged < CaptureSelectionOverlay.minDragExtent) {
      setState(() {
        _start = null;
        _current = null;
      });
      return;
    }

    final region = _toImageRegion(start, current, widgetSize);
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
      child: LayoutBuilder(
        builder: (context, constraints) {
          final widgetSize = constraints.biggest;
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
              onPanEnd: (_) => _finish(widgetSize),
              child: CustomPaint(
                size: widgetSize,
                painter: SelectionPainter(
                  backdrop: widget.backdrop,
                  start: _start,
                  current: _current,
                  pointer: _pointer,
                  accent: colors.brand,
                  hint: l10n.selectionHint,
                  textDirection: Directionality.of(context),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
