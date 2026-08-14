import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Navigator of the single app window.
///
/// Captures can start from the tray or a global hotkey, with no `BuildContext`
/// in hand, so the flows push the selection overlay through this key
/// (SPEC §1, §4: logic keeps no BuildContext coupling).
final navigatorKeyProvider = Provider<GlobalKey<NavigatorState>>((ref) {
  return GlobalKey<NavigatorState>(debugLabel: 'foxscreenshots');
});

/// Messenger of the single app window.
///
/// Installed above the navigator so a confirmation raised on a pushed route
/// (the editor saving, say) survives the pop back to the hub, and so captures
/// started from the tray or the hotkey have somewhere to report failures.
final scaffoldMessengerKeyProvider =
    Provider<GlobalKey<ScaffoldMessengerState>>(
      (ref) => GlobalKey<ScaffoldMessengerState>(debugLabel: 'foxscreenshots'),
    );
