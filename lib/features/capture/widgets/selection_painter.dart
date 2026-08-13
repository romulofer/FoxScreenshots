import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// Draws the whole capture overlay: frozen backdrop, dimmed surroundings, the
/// rubber band with its live size badge, and a pixel magnifier (SPEC §2.1).
///
/// One painter instead of stacked widgets: everything here repaints on every
/// pointer move, and a single canvas pass keeps the drag smooth on a 4K frame.
class SelectionPainter extends CustomPainter {
  const SelectionPainter({
    required this.backdrop,
    required this.start,
    required this.current,
    required this.pointer,
    required this.accent,
    required this.hint,
    required this.textDirection,
  });

  final ui.Image backdrop;
  final Offset? start;
  final Offset? current;
  final Offset? pointer;
  final Color accent;
  final String hint;
  final TextDirection textDirection;

  static const double _magnifierSize = 132;
  static const double _magnifierZoom = 6;
  static const Color _dim = Color(0x8C000000);

  @override
  void paint(Canvas canvas, Size size) {
    final canvasRect = Offset.zero & size;
    canvas.drawImageRect(
      backdrop,
      Rect.fromLTWH(
        0,
        0,
        backdrop.width.toDouble(),
        backdrop.height.toDouble(),
      ),
      canvasRect,
      Paint()..filterQuality = FilterQuality.high,
    );

    final selection = (start != null && current != null)
        ? Rect.fromPoints(start!, current!)
        : null;

    _paintDim(canvas, canvasRect, selection);
    if (selection == null) {
      _paintCrosshair(canvas, canvasRect);
      _paintHint(canvas, canvasRect);
    } else {
      _paintSelection(canvas, selection);
      _paintSizeBadge(canvas, canvasRect, selection, size);
    }
    _paintMagnifier(canvas, canvasRect, size);
  }

  /// Darkens everything outside the selection so the chosen region pops.
  void _paintDim(Canvas canvas, Rect canvasRect, Rect? selection) {
    final paint = Paint()..color = _dim;
    if (selection == null) {
      canvas.drawRect(canvasRect, paint);
      return;
    }
    canvas.drawPath(
      Path.combine(
        PathOperation.difference,
        Path()..addRect(canvasRect),
        Path()..addRect(selection),
      ),
      paint,
    );
  }

  void _paintCrosshair(Canvas canvas, Rect canvasRect) {
    final at = pointer;
    if (at == null) return;
    final paint = Paint()
      ..color = accent.withValues(alpha: 0.7)
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(canvasRect.left, at.dy),
      Offset(canvasRect.right, at.dy),
      paint,
    );
    canvas.drawLine(
      Offset(at.dx, canvasRect.top),
      Offset(at.dx, canvasRect.bottom),
      paint,
    );
  }

  void _paintSelection(Canvas canvas, Rect selection) {
    canvas.drawRect(
      selection,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..color = accent,
    );

    // Corner ticks, so the exact edge stays visible against busy wallpaper.
    const tick = 14.0;
    final corner = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..color = accent;
    for (final c in [
      (selection.topLeft, const Offset(tick, 0), const Offset(0, tick)),
      (selection.topRight, const Offset(-tick, 0), const Offset(0, tick)),
      (selection.bottomLeft, const Offset(tick, 0), const Offset(0, -tick)),
      (selection.bottomRight, const Offset(-tick, 0), const Offset(0, -tick)),
    ]) {
      canvas.drawLine(c.$1, c.$1 + c.$2, corner);
      canvas.drawLine(c.$1, c.$1 + c.$3, corner);
    }
  }

  /// Live dimensions, in backdrop pixels (what the saved file will measure).
  void _paintSizeBadge(
    Canvas canvas,
    Rect canvasRect,
    Rect selection,
    Size size,
  ) {
    final scaleX = backdrop.width / size.width;
    final scaleY = backdrop.height / size.height;
    final label =
        '${(selection.width * scaleX).round()} × '
        '${(selection.height * scaleY).round()} px';

    final painter = _text(label, 13, FontWeight.w600);
    const padding = EdgeInsets.symmetric(horizontal: 8, vertical: 4);
    final badgeSize = Size(
      painter.width + padding.horizontal,
      painter.height + padding.vertical,
    );

    // Above the selection, flipping inside when there is no room.
    var origin = Offset(selection.left, selection.top - badgeSize.height - 6);
    if (origin.dy < canvasRect.top) {
      origin = Offset(selection.left, selection.bottom + 6);
    }
    final badge = origin & badgeSize;

    canvas.drawRRect(
      RRect.fromRectAndRadius(badge, const Radius.circular(6)),
      Paint()..color = accent,
    );
    painter.paint(canvas, origin + Offset(padding.left, padding.top));
  }

  void _paintHint(Canvas canvas, Rect canvasRect) {
    final painter = _text(hint, 15, FontWeight.w500);
    const padding = EdgeInsets.symmetric(horizontal: 14, vertical: 10);
    final box = Rect.fromCenter(
      center: Offset(canvasRect.center.dx, canvasRect.top + 64),
      width: painter.width + padding.horizontal,
      height: painter.height + padding.vertical,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(box, const Radius.circular(10)),
      Paint()..color = const Color(0xCC000000),
    );
    painter.paint(canvas, box.topLeft + Offset(padding.left, padding.top));
  }

  /// Zoomed view of the pixels under the cursor, for pixel-precise edges.
  void _paintMagnifier(Canvas canvas, Rect canvasRect, Size size) {
    final at = pointer;
    if (at == null) return;

    // Keep the loupe clear of the cursor, flipping near the screen edges.
    var origin = at + const Offset(24, 24);
    if (origin.dx + _magnifierSize > canvasRect.right) {
      origin = Offset(at.dx - _magnifierSize - 24, origin.dy);
    }
    if (origin.dy + _magnifierSize > canvasRect.bottom) {
      origin = Offset(origin.dx, at.dy - _magnifierSize - 24);
    }
    final box = origin & const Size(_magnifierSize, _magnifierSize);
    final rrect = RRect.fromRectAndRadius(box, const Radius.circular(10));

    final scaleX = backdrop.width / size.width;
    final scaleY = backdrop.height / size.height;
    final srcExtent = Size(
      _magnifierSize / _magnifierZoom * scaleX,
      _magnifierSize / _magnifierZoom * scaleY,
    );
    final src = Rect.fromCenter(
      center: Offset(at.dx * scaleX, at.dy * scaleY),
      width: srcExtent.width,
      height: srcExtent.height,
    );

    canvas
      ..save()
      ..clipRRect(rrect)
      ..drawImageRect(
        backdrop,
        src,
        box,
        Paint()..filterQuality = FilterQuality.none,
      )
      ..restore();

    final center = box.center;
    final crosshair = Paint()
      ..color = accent
      ..strokeWidth = 1;
    canvas
      ..drawLine(
        Offset(box.left, center.dy),
        Offset(box.right, center.dy),
        crosshair,
      )
      ..drawLine(
        Offset(center.dx, box.top),
        Offset(center.dx, box.bottom),
        crosshair,
      )
      ..drawRRect(
        rrect,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2
          ..color = accent,
      );
  }

  TextPainter _text(String value, double size, FontWeight weight) {
    return TextPainter(
      text: TextSpan(
        text: value,
        style: TextStyle(
          color: Colors.white,
          fontSize: size,
          fontWeight: weight,
        ),
      ),
      textDirection: textDirection,
    )..layout();
  }

  @override
  bool shouldRepaint(SelectionPainter old) =>
      old.start != start ||
      old.current != current ||
      old.pointer != pointer ||
      old.backdrop != backdrop ||
      old.accent != accent ||
      old.hint != hint;
}
