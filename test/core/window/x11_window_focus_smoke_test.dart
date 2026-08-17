@TestOn('linux')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/window/x11_window_focus.dart';

/// Real-X11 smoke test (SPEC §6), same gating as x11_capture_smoke_test.dart:
/// skipped on CI and under Wayland, where there is no X server to dial.
///
/// `flutter test` has no GTK window of its own, so the process never shows up
/// in `_NET_CLIENT_LIST` and [X11WindowFocus.forceFocus] cannot exercise the
/// actual `XSetInputFocus` call without grabbing focus away from whatever the
/// person running the suite is doing on their real desktop. What this proves
/// instead: the FFI plumbing (open the display, walk the client list, time
/// out) runs clean end to end on a live X server — no crash, no hang, no
/// leaked display connection.
void main() {
  final env = Platform.environment;
  final hasX11 =
      (env['DISPLAY']?.isNotEmpty ?? false) &&
      env['XDG_SESSION_TYPE']?.toLowerCase() != 'wayland';

  group('X11WindowFocus', () {
    test(
      'não lança nem trava quando o processo não tem janela própria',
      () async {
        const focuser = X11WindowFocus();

        await expectLater(
          focuser.forceFocus().timeout(const Duration(seconds: 2)),
          completes,
        );
      },
    );
  }, skip: hasX11 ? null : 'needs a headed X11 session');
}
