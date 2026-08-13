import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Navigator of the single app window.
///
/// Captures can start from the tray or a global hotkey, with no `BuildContext`
/// in hand, so the flows push the selection overlay through this key
/// (SPEC §1, §4: logic keeps no BuildContext coupling).
final navigatorKeyProvider = Provider<GlobalKey<NavigatorState>>((ref) {
  return GlobalKey<NavigatorState>(debugLabel: 'foxscreenshots');
});
