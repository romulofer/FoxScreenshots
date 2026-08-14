import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/capture_result.dart';
import '../capture/image_decoder.dart';
import 'editor_compositor.dart';
import 'models/annotation.dart';
import 'models/editor_tool.dart';

/// The editable document: the base image plus its annotation layer.
///
/// One immutable snapshot per undo step. Crops create a new base image;
/// everything else shares the same one, so the history costs a list copy, not
/// a bitmap copy.
class EditorDocument {
  const EditorDocument({
    required this.pngBytes,
    required this.image,
    required this.width,
    required this.height,
    required this.annotations,
  });

  final Uint8List pngBytes;

  /// Decoded base. Null only in the moment between opening the editor and the
  /// engine finishing the decode.
  final ui.Image? image;

  final int width;
  final int height;
  final List<Annotation> annotations;

  EditorDocument copyWith({
    Uint8List? pngBytes,
    ui.Image? image,
    int? width,
    int? height,
    List<Annotation>? annotations,
  }) {
    return EditorDocument(
      pngBytes: pngBytes ?? this.pngBytes,
      image: image ?? this.image,
      width: width ?? this.width,
      height: height ?? this.height,
      annotations: annotations ?? this.annotations,
    );
  }
}

/// Everything the editor UI paints from.
class EditorState {
  const EditorState({
    required this.document,
    required this.tool,
    required this.color,
    required this.strokeWidth,
    required this.draft,
    required this.cropDraft,
    required this.canUndo,
    required this.canRedo,
    required this.isBusy,
    required this.isDirty,
  });

  final EditorDocument document;
  final EditorTool tool;
  final Color color;
  final double strokeWidth;

  /// The annotation being dragged right now, painted on top of the committed
  /// ones and not yet part of the undo history.
  final Annotation? draft;

  /// The rectangle being dragged with the crop tool.
  final Rect? cropDraft;

  final bool canUndo;
  final bool canRedo;

  /// A crop or an export is rasterizing; the canvas ignores input meanwhile.
  final bool isBusy;

  List<Annotation> get annotations => document.annotations;

  /// Committed annotations plus the in-progress one, in paint order.
  List<Annotation> get visibleAnnotations => [...document.annotations, ?draft];

  /// Whether the document differs from the last version handed back to the
  /// session. Drives both the Apply button and the discard confirmation.
  final bool isDirty;

  EditorState copyWith({
    EditorDocument? document,
    EditorTool? tool,
    Color? color,
    double? strokeWidth,
    Annotation? draft,
    bool clearDraft = false,
    Rect? cropDraft,
    bool clearCropDraft = false,
    bool? canUndo,
    bool? canRedo,
    bool? isBusy,
    bool? isDirty,
  }) {
    return EditorState(
      document: document ?? this.document,
      tool: tool ?? this.tool,
      color: color ?? this.color,
      strokeWidth: strokeWidth ?? this.strokeWidth,
      draft: clearDraft ? null : (draft ?? this.draft),
      cropDraft: clearCropDraft ? null : (cropDraft ?? this.cropDraft),
      canUndo: canUndo ?? this.canUndo,
      canRedo: canRedo ?? this.canRedo,
      isBusy: isBusy ?? this.isBusy,
      isDirty: isDirty ?? this.isDirty,
    );
  }
}

/// Drives the annotation editor (SPEC §2.2): tool selection, the drag being
/// drawn, undo/redo, crop, and flattening for export.
///
/// Scoped to one capture and auto-disposed with the editor route, so a
/// screenshot's decoded pixels do not outlive the window showing them
/// (SPEC §7: screenshots may hold sensitive data).
class EditorController
    extends AutoDisposeFamilyNotifier<EditorState, CaptureResult> {
  /// Cap on undo steps. Each step is cheap, but a crop-heavy session would
  /// otherwise pin every intermediate bitmap in RAM.
  static const int maxHistory = 50;

  /// Default annotation stroke, in image pixels.
  static const double defaultStrokeWidth = 4;

  static const Color defaultColor = Color(0xFFE53935);

  final List<EditorDocument> _past = [];
  final List<EditorDocument> _future = [];

  /// Images this controller decoded or rendered, including ones only reachable
  /// from the undo stack. Disposed together when the editor closes.
  final List<ui.Image> _owned = [];

  /// The document as the session last saw it. Compared by identity to decide
  /// whether leaving the editor would lose work.
  EditorDocument? _savedDocument;

  /// Where the crop drag started. Kept out of the state because only the
  /// resulting rectangle is painted.
  Offset? _cropAnchor;

  int _nextId = 0;

  /// Async work started before the editor closed must not touch [state] (nor
  /// leak the image it just rendered) after disposal.
  bool _disposed = false;

  @override
  EditorState build(CaptureResult arg) {
    ref.onDispose(() {
      _disposed = true;
      _disposeImages();
    });
    // The engine decode is async; the canvas shows a spinner until it lands.
    Future<void>(_loadBase);
    final document = EditorDocument(
      pngBytes: arg.pngBytes,
      image: null,
      width: arg.width,
      height: arg.height,
      annotations: const [],
    );
    _savedDocument = document;
    return EditorState(
      document: document,
      tool: EditorTool.arrow,
      color: defaultColor,
      strokeWidth: defaultStrokeWidth,
      draft: null,
      cropDraft: null,
      canUndo: false,
      canRedo: false,
      isBusy: false,
      isDirty: false,
    );
  }

  Future<void> _loadBase() async {
    final image = await ref.read(imageDecoderProvider)(arg.pngBytes);
    if (_disposed) {
      image.dispose();
      return;
    }
    _owned.add(image);
    final document = state.document.copyWith(
      image: image,
      width: image.width,
      height: image.height,
    );
    // Decoding is not an edit: carry the "saved" marker over to the new
    // snapshot so the editor does not open already dirty.
    if (identical(_savedDocument, state.document)) _savedDocument = document;
    state = state.copyWith(document: document, isDirty: _isDirty(document));
  }

  void selectTool(EditorTool tool) {
    _cropAnchor = null;
    state = state.copyWith(tool: tool, clearDraft: true, clearCropDraft: true);
  }

  void selectColor(Color color) => state = state.copyWith(color: color);

  void setStrokeWidth(double width) =>
      state = state.copyWith(strokeWidth: width);

  /// Starts a drag at [point] (image pixels).
  void startDraft(Offset point) {
    if (state.isBusy) return;
    if (state.tool == EditorTool.crop) {
      _cropAnchor = point;
      state = state.copyWith(cropDraft: Rect.fromPoints(point, point));
      return;
    }
    if (!state.tool.isDragTool) return;
    state = state.copyWith(draft: _newAnnotation(state.tool, point));
  }

  void updateDraft(Offset point) {
    final anchor = _cropAnchor;
    if (anchor != null) {
      // Anchored on where the drag began, not on the rectangle's top-left: a
      // normalized rect moves its own corner once the pointer goes up or left,
      // which would drag the anchor along with it.
      state = state.copyWith(cropDraft: Rect.fromPoints(anchor, point));
      return;
    }
    final draft = state.draft;
    if (draft == null) return;
    state = state.copyWith(draft: draft.dragTo(point));
  }

  /// Ends the drag: commits the annotation, or applies the crop.
  Future<void> endDraft() async {
    final crop = state.cropDraft;
    if (crop != null) {
      _cropAnchor = null;
      state = state.copyWith(clearCropDraft: true);
      if (crop.width >= Annotation.minDragExtent &&
          crop.height >= Annotation.minDragExtent) {
        await applyCrop(crop);
      }
      return;
    }

    final draft = state.draft;
    state = state.copyWith(clearDraft: true);
    if (draft == null || !draft.isMeaningful) return;
    _commit(
      state.document.copyWith(annotations: [...state.annotations, draft]),
    );
  }

  /// Places a caption at [position]. No-op for empty text, so cancelling the
  /// input dialog leaves nothing behind.
  void addText(Offset position, String text) {
    // The caption comes back from a dialog, which the editor may have outlived.
    if (_disposed || text.trim().isEmpty) return;
    _commit(
      state.document.copyWith(
        annotations: [
          ...state.annotations,
          TextAnnotation(
            id: _id(),
            color: state.color,
            strokeWidth: state.strokeWidth,
            position: position,
            text: text,
            fontSize: TextAnnotation.fontSizeFor(state.strokeWidth),
          ),
        ],
      ),
    );
  }

  /// Drops the next badge in the 1, 2, 3… sequence at [center].
  void addStep(Offset center) {
    final next = state.annotations.whereType<StepAnnotation>().length + 1;
    _commit(
      state.document.copyWith(
        annotations: [
          ...state.annotations,
          StepAnnotation(
            id: _id(),
            color: state.color,
            strokeWidth: state.strokeWidth,
            center: center,
            number: next,
          ),
        ],
      ),
    );
  }

  /// Trims the base image to [rect] and moves the annotations with it, so a
  /// crop never silently relocates a mark relative to what it points at.
  Future<void> applyCrop(Rect rect) async {
    final image = state.document.image;
    if (image == null || state.isBusy) return;

    state = state.copyWith(isBusy: true);
    try {
      final cropped = await cropImage(base: image, rect: rect);
      if (_disposed) {
        cropped.image.dispose();
        return;
      }
      _owned.add(cropped.image);

      final shift = -Offset(
        rect.left.floorToDouble(),
        rect.top.floorToDouble(),
      );
      _commit(
        EditorDocument(
          pngBytes: cropped.pngBytes,
          image: cropped.image,
          width: cropped.width,
          height: cropped.height,
          annotations: [
            for (final annotation in state.annotations)
              _translate(annotation, shift),
          ],
        ),
      );
    } finally {
      if (!_disposed) state = state.copyWith(isBusy: false);
    }
  }

  void undo() {
    if (_past.isEmpty) return;
    _future.add(state.document);
    final previous = _past.removeLast();
    state = state.copyWith(
      document: previous,
      clearDraft: true,
      clearCropDraft: true,
      canUndo: _past.isNotEmpty,
      canRedo: true,
      isDirty: _isDirty(previous),
    );
  }

  void redo() {
    if (_future.isEmpty) return;
    _past.add(state.document);
    final next = _future.removeLast();
    state = state.copyWith(
      document: next,
      clearDraft: true,
      clearCropDraft: true,
      canUndo: true,
      canRedo: _future.isNotEmpty,
      isDirty: _isDirty(next),
    );
  }

  /// Bakes the annotations into the image and returns the result, ready for
  /// the clipboard, a file, or the session gallery. Null while the base image
  /// is still decoding.
  Future<FlattenedImage?> flatten({
    TextDirection textDirection = TextDirection.ltr,
  }) async {
    final image = state.document.image;
    if (image == null) return null;
    if (state.annotations.isEmpty) {
      // Nothing to bake: hand back the bytes we already have instead of
      // re-encoding (and re-compressing) an unchanged image.
      return FlattenedImage(image: image, pngBytes: state.document.pngBytes);
    }
    return ref.read(imageFlattenerProvider)(
      base: image,
      annotations: state.annotations,
      textDirection: textDirection,
    );
  }

  /// Pushes [document] as the new present and clears the redo branch.
  void _commit(EditorDocument document) {
    final dropped = [..._future];
    _future.clear();
    _past.add(state.document);
    state = state.copyWith(
      document: document,
      clearDraft: true,
      canUndo: true,
      canRedo: false,
      isDirty: _isDirty(document),
    );
    if (_past.length > maxHistory) dropped.add(_past.removeAt(0));
    for (final document in dropped) {
      _releaseImage(document.image);
    }
  }

  /// Frees a bitmap the moment the last snapshot holding it leaves the history.
  ///
  /// Crops are the only images beyond the first, and a screenshot-sized one is
  /// megabytes: waiting for the editor to close would pin every discarded
  /// redo branch in memory (and keep captured pixels resident, SPEC §7).
  void _releaseImage(ui.Image? image) {
    if (image == null) return;
    final reachable = [state.document, ..._past, ..._future];
    if (reachable.any((document) => identical(document.image, image))) return;
    image.dispose();
    _owned.remove(image);
  }

  /// Records that the current document is what the session now holds, so the
  /// editor can be closed without a "discard changes?" prompt.
  void markApplied() {
    _savedDocument = state.document;
    state = state.copyWith(isDirty: false);
  }

  bool _isDirty(EditorDocument document) =>
      !identical(document, _savedDocument);

  Annotation _newAnnotation(EditorTool tool, Offset point) {
    final id = _id();
    final color = state.color;
    final strokeWidth = state.strokeWidth;
    return switch (tool) {
      EditorTool.arrow => ArrowAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        start: point,
        end: point,
      ),
      EditorTool.rectangle => RectangleAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        start: point,
        end: point,
      ),
      EditorTool.ellipse => EllipseAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        start: point,
        end: point,
      ),
      EditorTool.highlight => HighlightAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        start: point,
        end: point,
      ),
      EditorTool.blur => RedactionAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        start: point,
        end: point,
        style: RedactionStyle.blur,
      ),
      EditorTool.pixelate => RedactionAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        start: point,
        end: point,
        style: RedactionStyle.pixelate,
      ),
      EditorTool.pen => PenAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        points: [point],
      ),
      // Tap tools never start a drag; crop is not an annotation.
      EditorTool.text || EditorTool.step || EditorTool.crop => PenAnnotation(
        id: id,
        color: color,
        strokeWidth: strokeWidth,
        points: [point],
      ),
    };
  }

  String _id() => 'ann-${_nextId++}';

  void _disposeImages() {
    for (final image in _owned) {
      image.dispose();
    }
    _owned.clear();
  }
}

/// Moves an annotation by [shift]; used when a crop re-origins the image.
Annotation _translate(Annotation annotation, Offset shift) {
  return switch (annotation) {
    ArrowAnnotation() => ArrowAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      start: annotation.start + shift,
      end: annotation.end + shift,
    ),
    RectangleAnnotation() => RectangleAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      start: annotation.start + shift,
      end: annotation.end + shift,
    ),
    EllipseAnnotation() => EllipseAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      start: annotation.start + shift,
      end: annotation.end + shift,
    ),
    HighlightAnnotation() => HighlightAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      start: annotation.start + shift,
      end: annotation.end + shift,
    ),
    RedactionAnnotation() => RedactionAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      start: annotation.start + shift,
      end: annotation.end + shift,
      style: annotation.style,
    ),
    PenAnnotation() => PenAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      points: [for (final p in annotation.points) p + shift],
    ),
    TextAnnotation() => TextAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      position: annotation.position + shift,
      text: annotation.text,
      fontSize: annotation.fontSize,
    ),
    StepAnnotation() => StepAnnotation(
      id: annotation.id,
      color: annotation.color,
      strokeWidth: annotation.strokeWidth,
      center: annotation.center + shift,
      number: annotation.number,
    ),
  };
}

final editorControllerProvider = NotifierProvider.autoDispose
    .family<EditorController, EditorState, CaptureResult>(EditorController.new);
