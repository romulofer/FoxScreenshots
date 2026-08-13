import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/l10n/gen/app_localizations.dart';
import '../../models/capture_result.dart';

/// Non-destructive annotation editor (SPEC §2.2). Scaffold placeholder: shows
/// the base image and a tool rail. Individual tools (arrow, blur, pen, number…)
/// and the compositor land incrementally, each with its own tests.
class EditorScreen extends ConsumerWidget {
  const EditorScreen({required this.capture, super.key});

  final CaptureResult capture;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.editorTitle)),
      body: Center(
        child: InteractiveViewer(child: Image.memory(capture.pngBytes)),
      ),
    );
  }
}
