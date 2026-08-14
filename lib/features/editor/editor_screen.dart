import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/l10n/gen/app_localizations.dart';
import '../../core/storage/clipboard_service.dart';
import '../../core/storage/output_service.dart';
import '../../models/capture_result.dart';
import '../home/session_controller.dart';
import 'editor_controller.dart';
import 'models/editor_tool.dart';
import 'widgets/editor_canvas.dart';
import 'widgets/style_bar.dart';
import 'widgets/tool_rail.dart';

/// Non-destructive annotation editor (SPEC §2.2).
///
/// The annotation layer only becomes pixels when the user copies, saves, or
/// applies — until then every mark can be undone or restyled.
class EditorScreen extends ConsumerWidget {
  const EditorScreen({required this.capture, super.key});

  final CaptureResult capture;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final provider = editorControllerProvider(capture);
    final state = ref.watch(provider);
    final controller = ref.read(provider.notifier);
    final image = state.document.image;

    return PopScope(
      // Edits live only in this route; leaving with unsaved marks needs a
      // deliberate confirmation.
      canPop: !state.isDirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        if (await _confirmDiscard(context)) {
          if (context.mounted) Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.editorTitle),
          actions: [
            IconButton(
              tooltip: l10n.undo,
              icon: const Icon(Icons.undo),
              onPressed: state.canUndo ? controller.undo : null,
            ),
            IconButton(
              tooltip: l10n.redo,
              icon: const Icon(Icons.redo),
              onPressed: state.canRedo ? controller.redo : null,
            ),
            const SizedBox(width: 8),
            IconButton(
              tooltip: l10n.actionCopy,
              icon: const Icon(Icons.copy_outlined),
              onPressed: image == null
                  ? null
                  : () => _onCopy(context, ref, controller),
            ),
            IconButton(
              tooltip: l10n.actionSave,
              icon: const Icon(Icons.save_outlined),
              onPressed: image == null
                  ? null
                  : () => _onSave(context, ref, controller),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: FilledButton.icon(
                icon: const Icon(Icons.check),
                label: Text(l10n.editorApply),
                onPressed: image == null || !state.isDirty
                    ? null
                    : () => _onApply(context, ref, controller),
              ),
            ),
          ],
        ),
        body: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            children: [
              Expanded(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    ToolRail(
                      selected: state.tool,
                      onSelected: controller.selectTool,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: image == null
                          ? const Center(child: CircularProgressIndicator())
                          : EditorCanvas(
                              image: image,
                              annotations: state.visibleAnnotations,
                              cropDraft: state.cropDraft,
                              tool: state.tool,
                              onDragStart: controller.startDraft,
                              onDragUpdate: controller.updateDraft,
                              onDragEnd: controller.endDraft,
                              onTap: (point) => _onTap(
                                context,
                                controller,
                                state.tool,
                                point,
                              ),
                            ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              StyleBar(
                color: state.color,
                strokeWidth: state.strokeWidth,
                onColorSelected: controller.selectColor,
                onStrokeWidthChanged: controller.setStrokeWidth,
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Tap-to-place tools. Drag tools ignore taps: a click with the arrow tool
  /// should not drop a zero-length arrow.
  Future<void> _onTap(
    BuildContext context,
    EditorController controller,
    EditorTool tool,
    Offset point,
  ) async {
    switch (tool) {
      case EditorTool.step:
        controller.addStep(point);
      case EditorTool.text:
        final text = await _promptForText(context);
        if (text != null) controller.addText(point, text);
      default:
        break;
    }
  }

  Future<String?> _promptForText(BuildContext context) {
    return showDialog<String>(
      context: context,
      builder: (context) => const _TextPromptDialog(),
    );
  }

  Future<bool> _confirmDiscard(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final discard = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.editorDiscardTitle),
        content: Text(l10n.editorDiscardMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.actionCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.actionDiscard),
          ),
        ],
      ),
    );
    return discard ?? false;
  }

  Future<void> _onCopy(
    BuildContext context,
    WidgetRef ref,
    EditorController controller,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final bytes = await _flatten(context, controller);
    if (bytes == null) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.editorExportFailed)));
      return;
    }
    final ok = await ref.read(clipboardServiceProvider).copyPng(bytes);
    messenger.showSnackBar(
      SnackBar(
        content: Text(ok ? l10n.copiedToClipboard : l10n.copyToClipboardFailed),
      ),
    );
  }

  Future<void> _onSave(
    BuildContext context,
    WidgetRef ref,
    EditorController controller,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final bytes = await _flatten(context, controller);
    if (bytes == null) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.editorExportFailed)));
      return;
    }
    try {
      final path = await ref
          .read(outputServiceProvider)
          .savePngWithDialog(bytes);
      if (path != null) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.savedTo(path))));
      }
    } catch (_) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.saveFailed)));
    }
  }

  /// Bakes the edits back into the session entry, so the gallery thumbnail and
  /// every later action see the annotated image.
  Future<void> _onApply(
    BuildContext context,
    WidgetRef ref,
    EditorController controller,
  ) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);

    final flattened = await controller.flatten(
      textDirection: Directionality.of(context),
    );
    if (flattened == null) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.editorExportFailed)));
      return;
    }

    ref
        .read(sessionControllerProvider.notifier)
        .replace(
          CaptureResult(
            id: capture.id,
            pngBytes: flattened.pngBytes,
            width: flattened.width,
            height: flattened.height,
            takenAt: capture.takenAt,
          ),
        );
    // Marked before popping: the route guard must not ask to discard edits the
    // session has just taken.
    controller.markApplied();
    messenger.showSnackBar(SnackBar(content: Text(l10n.editorApplied)));
    navigator.pop();
  }

  Future<Uint8List?> _flatten(
    BuildContext context,
    EditorController controller,
  ) async {
    final flattened = await controller.flatten(
      textDirection: Directionality.of(context),
    );
    return flattened?.pngBytes;
  }
}

/// Asks for the caption of a text annotation.
///
/// Stateful so the field's controller lives exactly as long as the dialog:
/// disposing it when the future completes would kill it mid-exit-animation,
/// while the dialog is still being rebuilt.
class _TextPromptDialog extends StatefulWidget {
  const _TextPromptDialog();

  @override
  State<_TextPromptDialog> createState() => _TextPromptDialogState();
}

class _TextPromptDialogState extends State<_TextPromptDialog> {
  final TextEditingController _field = TextEditingController();

  @override
  void dispose() {
    _field.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(l10n.editorTextTitle),
      content: TextField(
        controller: _field,
        autofocus: true,
        decoration: InputDecoration(hintText: l10n.editorTextHint),
        onSubmitted: (value) => Navigator.of(context).pop(value),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.actionCancel),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(_field.text),
          child: Text(l10n.actionConfirm),
        ),
      ],
    );
  }
}
