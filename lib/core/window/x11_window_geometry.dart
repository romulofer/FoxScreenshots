import 'dart:ffi' hide Size;
import 'dart:io';
import 'dart:isolate';
import 'dart:ui';

import '../capture/x11/x11_bindings.dart';
import '../capture/x11/x11_properties.dart';
import 'window_geometry.dart';

/// Reads the app's own window position off the X server.
///
/// The window is found by process id in the window manager's client list
/// (`_NET_CLIENT_LIST` + `_NET_WM_PID`, the same route `wmctrl` takes) rather
/// than by keyboard focus: the overlay is measured moments after being mapped,
/// when focus may still be somewhere else.
///
/// The call runs in a background isolate with its own X connection, like every
/// other X11 call in the app.
class X11WindowGeometry implements WindowGeometryProbe {
  const X11WindowGeometry();

  @override
  Future<Offset?> ownWindowOrigin(Size physicalSize) async {
    final width = physicalSize.width.round();
    final height = physicalSize.height.round();
    if (width <= 0 || height <= 0) return null;
    try {
      return await Isolate.run(() => _originSync(pid, width, height));
    } on X11Exception {
      // Never fatal: the caller falls back to the window manager's numbers.
      return null;
    }
  }
}

/// Top-left corner, in root coordinates, of the window owned by [processId]
/// whose client area measures [width]x[height] physical pixels.
///
/// Matching on the size is what makes this safe: it picks out the surface the
/// engine is rendering into even when the process owns several windows, and
/// rejects a reading taken while the window manager is still resizing.
Offset? _originSync(int processId, int width, int height) {
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
    final clientList = internAtom(x11, display, scratch, '_NET_CLIENT_LIST');
    if (clientList == 0) return null;

    for (final window in readCardinals(
      x11,
      display,
      scratch,
      root,
      clientList,
    )) {
      if (windowPid(x11, display, scratch, window) != processId) continue;

      final origin = _clientOrigin(
        x11,
        display,
        scratch,
        root,
        window,
        width,
        height,
      );
      if (origin != null) return origin;
    }
    return null;
  } finally {
    x11.free(scratch);
    x11.closeDisplay(display);
  }
}

/// Root-relative origin of [window]'s client area, or `null` when it is not the
/// [width]x[height] surface being looked for.
Offset? _clientOrigin(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int root,
  int window,
  int width,
  int height,
) {
  final base = scratch + callerScratchOffset;
  final rootOut = base.cast<UnsignedLong>();
  final xOut = (base + 8).cast<Int32>();
  final yOut = (base + 12).cast<Int32>();
  final widthOut = (base + 16).cast<Uint32>();
  final heightOut = (base + 20).cast<Uint32>();
  final borderOut = (base + 24).cast<Uint32>();
  final depthOut = (base + 28).cast<Uint32>();
  final ok = x11.getGeometry(
    display,
    window,
    rootOut,
    xOut,
    yOut,
    widthOut,
    heightOut,
    borderOut,
    depthOut,
  );
  if (ok == 0) return null;
  if (widthOut.value != width || heightOut.value != height) return null;

  // The geometry above is relative to the window's parent — the window
  // manager's frame — so map (0,0) of the window onto the root to get the
  // position the screenshot is indexed by.
  final absX = (base + 32).cast<Int32>();
  final absY = (base + 36).cast<Int32>();
  final child = (base + 40).cast<UnsignedLong>();
  final translated = x11.translateCoordinates(
    display,
    window,
    root,
    0,
    0,
    absX,
    absY,
    child,
  );
  if (translated == 0) return null;

  return Offset(absX.value.toDouble(), absY.value.toDouble());
}
