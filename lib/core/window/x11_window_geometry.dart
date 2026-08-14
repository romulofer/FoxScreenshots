import 'dart:ffi' hide Size;
import 'dart:io';
import 'dart:isolate';
import 'dart:ui';

import '../capture/x11/x11_bindings.dart';
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

/// `Success` — the status `XGetWindowProperty` returns when it read something.
const int _success = 0;

/// `AnyPropertyType` — accept whatever type the property happens to have.
const int _anyPropertyType = 0;

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

  // One scratch block for every out-parameter below; each slot is 8-byte
  // aligned so an X `Window`/`Atom` (unsigned long) always lands aligned.
  final scratch = x11.malloc(128);
  if (scratch == nullptr) {
    throw const X11Exception('malloc failed for X11 scratch buffer');
  }
  try {
    final root = x11.defaultRootWindow(display);
    final clientList = _atom(x11, display, scratch, '_NET_CLIENT_LIST');
    final pidAtom = _atom(x11, display, scratch, '_NET_WM_PID');
    if (clientList == 0 || pidAtom == 0) return null;

    for (final window in _cardinals(x11, display, scratch, root, clientList)) {
      final pids = _cardinals(x11, display, scratch, window, pidAtom);
      if (pids.isEmpty || pids.first != processId) continue;

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

/// Interns [name], writing it into [scratch] as a NUL-terminated C string.
int _atom(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  String name,
) {
  const onlyIfExists = 1;
  final buffer = (scratch + 64).cast<Uint8>();
  final bytes = name.codeUnits;
  for (var i = 0; i < bytes.length; i++) {
    buffer[i] = bytes[i];
  }
  buffer[bytes.length] = 0;
  return x11.internAtom(display, buffer.cast<Char>(), onlyIfExists);
}

/// Reads a 32-bit-format property as a list of numbers.
///
/// X hands `format 32` data back as C `long`s, which are 64 bits wide on the
/// platforms this backend runs on — hence the `Uint64` view.
List<int> _cardinals(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int window,
  int property,
) {
  const noDelete = 0;
  const maxLongs = 1024;

  final actualType = scratch.cast<UnsignedLong>();
  final actualFormat = (scratch + 8).cast<Int32>();
  final itemCount = (scratch + 16).cast<UnsignedLong>();
  final bytesAfter = (scratch + 24).cast<UnsignedLong>();
  final data = (scratch + 32).cast<Pointer<Uint8>>();
  data.value = nullptr;

  final status = x11.getWindowProperty(
    display,
    window,
    property,
    0,
    maxLongs,
    noDelete,
    _anyPropertyType,
    actualType,
    actualFormat,
    itemCount,
    bytesAfter,
    data,
  );
  final buffer = data.value;
  if (status != _success || buffer == nullptr) return const [];
  try {
    if (actualFormat.value != 32) return const [];
    return List<int>.from(buffer.cast<Uint64>().asTypedList(itemCount.value));
  } finally {
    x11.freeData(buffer);
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
  final rootOut = (scratch + 40).cast<UnsignedLong>();
  final xOut = (scratch + 48).cast<Int32>();
  final yOut = (scratch + 52).cast<Int32>();
  final widthOut = (scratch + 56).cast<Uint32>();
  final heightOut = (scratch + 60).cast<Uint32>();
  final borderOut = (scratch + 80).cast<Uint32>();
  final depthOut = (scratch + 84).cast<Uint32>();
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
  final absX = (scratch + 88).cast<Int32>();
  final absY = (scratch + 92).cast<Int32>();
  final child = (scratch + 96).cast<UnsignedLong>();
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
