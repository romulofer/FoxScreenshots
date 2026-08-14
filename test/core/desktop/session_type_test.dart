import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/desktop/session_type.dart';

void main() {
  // The function short-circuits to `other` off Linux; these cases only mean
  // anything where a display server of either kind can exist.
  final onLinux = Platform.isLinux;

  test('XDG_SESSION_TYPE manda', () {
    expect(
      currentDesktopSession(environment: const {'XDG_SESSION_TYPE': 'wayland'}),
      onLinux ? DesktopSession.wayland : DesktopSession.other,
    );
    expect(
      currentDesktopSession(environment: const {'XDG_SESSION_TYPE': 'x11'}),
      onLinux ? DesktopSession.x11 : DesktopSession.other,
    );
  });

  test('WAYLAND_DISPLAY sozinha já denuncia a sessão', () {
    expect(
      currentDesktopSession(
        environment: const {'WAYLAND_DISPLAY': 'wayland-0'},
      ),
      onLinux ? DesktopSession.wayland : DesktopSession.other,
    );
  });

  test('o Wayland vence mesmo com DISPLAY do XWayland presente', () {
    // A Flutter app under XWayland sees both; treating it as X11 would grab a
    // root window that is not the real desktop.
    expect(
      currentDesktopSession(
        environment: const {'XDG_SESSION_TYPE': 'wayland', 'DISPLAY': ':0'},
      ),
      onLinux ? DesktopSession.wayland : DesktopSession.other,
    );
  });

  test('sem nenhuma pista, nenhuma sessão gráfica', () {
    expect(currentDesktopSession(environment: const {}), DesktopSession.other);
  });
}
