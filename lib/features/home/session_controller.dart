import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/capture_result.dart';

/// In-memory list of screenshots taken this session, most-recent first
/// (SPEC §2.5). Not a permanent library — cleared on quit.
class SessionController extends Notifier<List<CaptureResult>> {
  @override
  List<CaptureResult> build() => const [];

  void add(CaptureResult capture) {
    state = [capture, ...state];
  }

  void remove(String id) {
    state = state.where((c) => c.id != id).toList(growable: false);
  }

  void replace(CaptureResult capture) {
    state = [
      for (final c in state)
        if (c.id == capture.id) capture else c,
    ];
  }

  void clear() => state = const [];
}

final sessionControllerProvider =
    NotifierProvider<SessionController, List<CaptureResult>>(
      SessionController.new,
    );
