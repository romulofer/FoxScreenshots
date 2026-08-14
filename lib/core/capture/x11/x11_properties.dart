import 'dart:ffi';

import 'x11_bindings.dart';

/// Reading window properties off the X server (EWMH), shared by the capture
/// backend and the overlay's placement probe.
///
/// Both work from one `malloc`ed scratch block instead of allocating per call.
/// The layout is fixed so the helpers never tread on each other:
///
/// * bytes 0–63   — out-parameters of [readCardinals]
/// * bytes 64–127 — the C string [internAtom] writes
/// * bytes 128+   — free for the caller (an `XEvent` is 192 bytes, and the
///   overlay builds one there)
///
/// Allocate at least [scratchBytes] and hand the same pointer to every call.
const int scratchBytes = 512;

/// First byte a caller may use for its own out-parameters.
const int callerScratchOffset = 128;

/// `Success` — the status `XGetWindowProperty` returns when it read something.
const int _success = 0;

/// `AnyPropertyType` — accept whatever type the property happens to have.
const int _anyPropertyType = 0;

/// Interns [name] and returns its atom, or 0 when the server does not know it
/// (nothing has ever set that property).
int internAtom(
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

/// Reads a 32-bit-format property as a list of numbers, empty when absent.
///
/// X hands `format 32` data back as C `long`s, which are 64 bits wide on the
/// platforms this backend runs on — hence the `Uint64` view.
List<int> readCardinals(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int window,
  int property,
) {
  const noDelete = 0;
  const maxLongs = 1024;
  if (property == 0) return const [];

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

/// Top-level windows of [processId], in the window manager's own client list.
///
/// Empty while the app's only window is hidden: a withdrawn window leaves
/// `_NET_CLIENT_LIST`, so callers must ask again once it is mapped.
List<int> ownWindows(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int root,
  int processId,
) {
  final clientList = internAtom(x11, display, scratch, '_NET_CLIENT_LIST');
  if (clientList == 0) return const [];

  return [
    for (final window in readCardinals(x11, display, scratch, root, clientList))
      if (windowPid(x11, display, scratch, window) == processId) window,
  ];
}

/// Whether the window manager advertises support for every atom in [names]
/// (`_NET_SUPPORTED`), so a request can be skipped instead of sent into the
/// void and silently ignored.
bool supportsAll(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int root,
  List<String> names,
) {
  final supported = internAtom(x11, display, scratch, '_NET_SUPPORTED');
  final atoms = readCardinals(x11, display, scratch, root, supported).toSet();
  if (atoms.isEmpty) return false;

  for (final name in names) {
    final atom = internAtom(x11, display, scratch, name);
    if (atom == 0 || !atoms.contains(atom)) return false;
  }
  return true;
}

/// Process id that owns [window] (`_NET_WM_PID`), or `null` when it says
/// nothing — remote and toolkit-less clients often do not set it.
int? windowPid(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int window,
) {
  final atom = internAtom(x11, display, scratch, '_NET_WM_PID');
  final values = readCardinals(x11, display, scratch, window, atom);
  return values.isEmpty ? null : values.first;
}
