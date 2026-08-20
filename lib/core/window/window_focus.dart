import 'dart:io';

import 'package:window_manager/window_manager.dart';

import '../desktop/session_type.dart';
import 'x11_window_focus.dart';

/// Forces the app's own window to take real OS keyboard focus.
///
/// Needed on top of `window_manager`'s own `focus()`: see [X11WindowFocus] for
/// why that alone does not reliably win the overlay keyboard focus on X11.
abstract interface class WindowFocuser {
  Future<void> forceFocus();
}

/// Used where there is nothing extra to do (Windows, macOS, Wayland, tests):
/// `window_manager`'s own `focus()` call is trusted as-is there.
class NoWindowFocuser implements WindowFocuser {
  const NoWindowFocuser();

  @override
  Future<void> forceFocus() async {}
}

/// Picks the implementation for the current session. Mirrors
/// `defaultOverlayStacking`.
WindowFocuser defaultWindowFocuser({Map<String, String>? environment}) {
  if (!Platform.isLinux) return const NoWindowFocuser();
  return currentDesktopSession(environment: environment) == DesktopSession.x11
      ? const X11WindowFocus()
      // Wayland gives clients no way to ask for focus directly; window_manager's
      // own focus() is all there is.
      : const NoWindowFocuser();
}

/// How long to keep retrying [WindowManager.focus] before giving up.
const Duration _focusTimeout = Duration(milliseconds: 500);
const Duration _focusPoll = Duration(milliseconds: 30);

/// Shows and focuses the app's window for real, on top of whatever
/// `window_manager` alone would do.
///
/// A bare `windowManager.focus()` does not reliably win real keyboard focus
/// for a window that was fully hidden and is being shown again with no fresh
/// user-interaction timestamp — a capture finishing, or a tray click, are
/// exactly that (see [X11WindowFocus] for the X11-specific reason). This
/// retries [WindowManager.focus] until [WindowManager.isFocused] confirms it
/// landed or the deadline runs out, then asks [focuser] for real OS-level
/// focus on top of that.
Future<void> ensureWindowFocus(WindowFocuser focuser) async {
  final deadline = DateTime.now().add(_focusTimeout);
  while (true) {
    await windowManager.focus();
    if (await windowManager.isFocused()) break;
    if (!DateTime.now().isBefore(deadline)) break;
    await Future<void>.delayed(_focusPoll);
  }
  await focuser.forceFocus();
}
