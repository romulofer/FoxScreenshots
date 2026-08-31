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
            child: Stack(
              children: [
                // Committed annotations rarely change relative to how often
                // this widget rebuilds (once per drag, not once per pointer
                // move), so they sit behind a RepaintBoundary: shouldRepaint
                // returning false lets the engine reuse last frame's
                // rasterized layer instead of re-walking every annotation on
                // every pointer-move event of an unrelated drag.
                RepaintBoundary(
                  child: CustomPaint(
                    size: constraints.biggest,
                    painter: _BaseLayerPainter(
                      image: image,
                      annotations: annotations,
                      fit: fit,
                      textDirection: Directionality.of(context),
                    ),
                  ),
                ),
                // Only what actually changes on every pointer-move: the
                // in-progress drag and the crop rectangle.
                CustomPaint(
                  size: constraints.biggest,
                  painter: _DraftLayerPainter(
                    base: image,
                    draft: draft,
                    cropDraft: cropDraft,
                    fit: fit,
                    textDirection: Directionality.of(context),
                    cropAccent: Theme.of(context).colorScheme.primary,
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// The capture plus every committed annotation — everything a drag does not
/// touch.
class _BaseLayerPainter extends CustomPainter {
  const _BaseLayerPainter({
    required this.image,
    required this.annotations,
    required this.fit,
    required this.textDirection,
  });

  final ui.Image image;
  final List<Annotation> annotations;
  final CanvasFit fit;
  final TextDirection textDirection;

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

    AnnotationPainter(
      base: image,
      textDirection: textDirection,
    ).paintAll(canvas, annotations);

    canvas.restore();
  }

  @override
  bool shouldRepaint(_BaseLayerPainter old) =>
      old.image != image || old.annotations != annotations || old.fit != fit;
}

/// The annotation being dragged right now, plus the crop rectangle — redrawn
/// on every pointer-move event a drag produces.
class _DraftLayerPainter extends CustomPainter {
  const _DraftLayerPainter({
    required this.base,
    required this.draft,
    required this.cropDraft,
    required this.fit,
    required this.textDirection,
    required this.cropAccent,
  });

  final ui.Image base;
  final Annotation? draft;
  final Rect? cropDraft;
  final CanvasFit fit;
  final TextDirection textDirection;
  final Color cropAccent;

  static const Color _cropDim = Color(0x99000000);

  @override
  void paint(Canvas canvas, Size size) {
    canvas
      ..save()
      ..translate(fit.offset.dx, fit.offset.dy)
      ..scale(fit.scale);

    final draft = this.draft;
    if (draft != null) {
      AnnotationPainter(
        base: base,
        textDirection: textDirection,
      ).paint(canvas, draft);
    }

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
  bool shouldRepaint(_DraftLayerPainter old) =>
      old.base != base ||
      old.draft != draft ||
      old.cropDraft != cropDraft ||
      old.fit != fit ||
      old.cropAccent != cropAccent;
}
