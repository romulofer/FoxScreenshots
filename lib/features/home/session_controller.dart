import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/capture_result.dart';

/// In-memory list of screenshots taken this session, most-recent first
/// (SPEC §2.5). Not a permanent library — cleared on quit.
class SessionController extends Notifier<List<CaptureResult>> {
  /// Soft cap so a long session cannot grow unbounded in RAM (screenshots of
  /// passwords/2FA stay resident otherwise).
  static const int maxSessionCaptures = 30;

  @override
  List<CaptureResult> build() => const [];

  void add(CaptureResult capture) {
    state = [
      capture,
      ...state,
    ].take(maxSessionCaptures).toList(growable: false);
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
