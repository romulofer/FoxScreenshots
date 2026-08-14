/// The tools on the editor rail (SPEC §2.2).
enum EditorTool {
  /// Trims the base image itself — the one destructive tool.
  crop,
  arrow,
  rectangle,
  ellipse,
  highlight,
  blur,
  pixelate,
  pen,
  text,
  step;

  /// Whether the tool is driven by dragging out a shape, as opposed to a single
  /// tap that places something ([text], [step]).
  bool get isDragTool => switch (this) {
    EditorTool.text || EditorTool.step => false,
    _ => true,
  };

  /// Whether the tool adds an annotation layer. [crop] does not: it rewrites
  /// the base image.
  bool get createsAnnotation => this != EditorTool.crop;
}
