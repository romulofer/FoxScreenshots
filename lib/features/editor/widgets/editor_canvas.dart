import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../models/annotation.dart';
import '../models/editor_tool.dart';
import '../painters/annotation_painter.dart';
import 'canvas_fit.dart';

/// The editing surface: the capture, its annotations, and the pointer handling
/// that draws new ones.
///
/// Gestures are translated to image pixels through [CanvasFit] before they
/// reach the controller, so the controller never has to know how big the window
/// is.
class EditorCanvas extends StatelessWidget {
  const EditorCanvas({
    required this.image,
    required this.annotations,
    required this.draft,
    required this.cropDraft,
    required this.tool,
    required this.onDragStart,
    required this.onDragUpdate,
    required this.onDragEnd,
    required this.onTap,
    super.key,
  });

  final ui.Image image;

  /// Committed annotations, in paint order.
  final List<Annotation> annotations;

  /// The annotation being dragged right now, painted on top of
  /// [annotations]. Kept separate so [_EditorCanvasPainter.shouldRepaint] can
  /// tell "a drag moved" from "the committed list changed" instead of
  /// comparing a freshly concatenated list every frame.
  final Annotation? draft;
  final Rect? cropDraft;
  final EditorTool tool;
  final ValueChanged<Offset> onDragStart;
  final ValueChanged<Offset> onDragUpdate;
  final VoidCallback onDragEnd;

  /// Tap in image pixels — used by the tools that place instead of drag.
  final ValueChanged<Offset> onTap;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final fit = CanvasFit.contain(
          imageSize: Size(image.width.toDouble(), image.height.toDouble()),
          viewport: constraints.biggest,
        );
        return MouseRegion(
          cursor: tool.isDragTool
              ? SystemMouseCursors.precise
              : SystemMouseCursors.click,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTapUp: (details) => onTap(fit.toImage(details.localPosition)),
            onPanStart: (details) =>
                onDragStart(fit.toImage(details.localPosition)),
            onPanUpdate: (details) =>
                onDragUpdate(fit.toImage(details.localPosition)),
            onPanEnd: (_) => onDragEnd(),
            onPanCancel: onDragEnd,
            child: CustomPaint(
              size: constraints.biggest,
              painter: _EditorCanvasPainter(
                image: image,
                annotations: annotations,
                draft: draft,
                cropDraft: cropDraft,
                fit: fit,
                textDirection: Directionality.of(context),
                cropAccent: Theme.of(context).colorScheme.primary,
              ),
            ),
          ),
        );
      },
    );
  }
}

class _EditorCanvasPainter extends CustomPainter {
  const _EditorCanvasPainter({
    required this.image,
    required this.annotations,
    required this.draft,
    required this.cropDraft,
    required this.fit,
    required this.textDirection,
    required this.cropAccent,
  });

  final ui.Image image;
  final List<Annotation> annotations;
  final Annotation? draft;
  final Rect? cropDraft;
  final CanvasFit fit;
  final TextDirection textDirection;
  final Color cropAccent;

  static const Color _cropDim = Color(0x99000000);

  @override
  void paint(Canvas canvas, Size size) {
    // Everything below draws in image pixels: one transform here means the
    // preview and the exported PNG run the exact same painter code.
    canvas
      ..save()
      ..translate(fit.offset.dx, fit.offset.dy)
      ..scale(fit.scale)
      ..drawImage(
        image,
        Offset.zero,
        Paint()..filterQuality = FilterQuality.medium,
      );

    final annotationPainter = AnnotationPainter(
      base: image,
      textDirection: textDirection,
    )..paintAll(canvas, annotations);
    final draft = this.draft;
    if (draft != null) annotationPainter.paint(canvas, draft);

    final crop = cropDraft;
    if (crop != null) _paintCropDraft(canvas, crop);

    canvas.restore();
  }

  /// Dim everything the crop is about to discard, so the user judges the result
  /// rather than the rectangle.
  void _paintCropDraft(Canvas canvas, Rect crop) {
    final imageRect = Rect.fromLTWH(
      0,
      0,
      fit.imageSize.width,
      fit.imageSize.height,
    );
    canvas
      ..drawPath(
        Path.combine(
          PathOperation.difference,
          Path()..addRect(imageRect),
          Path()..addRect(crop),
        ),
        Paint()..color = _cropDim,
      )
      ..drawRect(
        crop,
        Paint()
          ..style = PaintingStyle.stroke
          // Constant on screen regardless of zoom: the canvas is scaled, so
          // divide the stroke back out.
          ..strokeWidth = 1.5 / fit.scale
          ..color = cropAccent,
      );
  }

  @override
  bool shouldRepaint(_EditorCanvasPainter old) =>
      old.image != image ||
      old.annotations != annotations ||
      old.draft != draft ||
      old.cropDraft != cropDraft ||
      old.fit != fit ||
      old.cropAccent != cropAccent;
}
