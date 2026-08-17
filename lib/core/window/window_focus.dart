import 'dart:io';

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
