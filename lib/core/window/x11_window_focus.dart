import 'dart:ffi';
import 'dart:io';
import 'dart:isolate';

import '../capture/x11/x11_bindings.dart';
import '../capture/x11/x11_properties.dart';
import 'window_focus.dart';

/// EWMH/Xlib implementation: hands the app's own top-level window real X
/// server keyboard focus.
///
/// `window_manager`'s `focus()` only asks the window manager to activate the
/// window (`gtk_window_present`, which under the hood is `gdk_window_focus`).
/// Most window managers apply focus-stealing prevention to that request when
/// it carries no fresh user-interaction timestamp — exactly what a reveal
/// triggered by a global hotkey or a tray click, from a window that was fully
/// hidden (closed to the tray), has. The window is shown, but keyboard input
/// keeps going wherever it was before, so Esc never reaches the overlay's
/// `Focus` widget and cannot cancel the capture.
///
/// `XSetInputFocus` talks straight to the X server instead of going through
/// the window manager's activation policy, so it lands even when the WM
/// would otherwise sit on the request.
class X11WindowFocus implements WindowFocuser {
  const X11WindowFocus();

  @override
  Future<void> forceFocus() async {
    try {
      await Isolate.run(() => _forceFocusSync(pid));
    } on X11Exception {
      // Best effort: window_manager's own focus() call is the fallback.
    }
  }
}

/// `RevertToParent` — where focus should fall back to if this window is
/// unmapped while it holds it.
const int _revertToParent = 2;
const int _currentTime = 0;

/// How long to keep asking for the window before giving up: the window
/// manager adds a freshly mapped window to `_NET_CLIENT_LIST` a moment after
/// `windowManager.show()` returns, not before — the same race
/// `x11_window_geometry.dart`'s placement probe already retries around.
const Duration _timeout = Duration(milliseconds: 500);
const Duration _pollStep = Duration(milliseconds: 20);

void _forceFocusSync(int processId) {
  final x11 = X11Lib.open();
  final display = x11.openDisplay(nullptr);
  if (display == nullptr) {
    throw const X11Exception(
      'Could not connect to the X server (is DISPLAY set?)',
    );
  }

  final scratch = x11.malloc(scratchBytes);
  if (scratch == nullptr) {
    throw const X11Exception('malloc failed for X11 scratch buffer');
  }
  try {
    final root = x11.defaultRootWindow(display);
    final deadline = DateTime.now().add(_timeout);
    while (true) {
      final windows = ownWindows(x11, display, scratch, root, processId);
      if (windows.isNotEmpty) {
        x11.setInputFocus(
          display,
          windows.first,
          _revertToParent,
          _currentTime,
        );
        x11.flush(display);
        return;
      }
      if (!DateTime.now().isBefore(deadline)) return;
      sleep(_pollStep);
    }
  } finally {
    x11.free(scratch);
    x11.closeDisplay(display);
  }
}
