import 'dart:ffi';
import 'dart:io';
import 'dart:isolate';

import '../capture/x11/x11_bindings.dart';
import '../capture/x11/x11_properties.dart';
import '../capture/x11/xinerama_bindings.dart';
import 'overlay_stacking.dart';

/// EWMH implementation: fullscreen the app's window and tell the window manager
/// which monitors it should stretch across.
///
/// Both are client messages to the root window, the way every toolkit does it —
/// setting the properties directly would be ignored on a mapped window.
class X11OverlayStacking implements OverlayStacking {
  const X11OverlayStacking();

  @override
  Future<bool> spanAllMonitors() async {
    try {
      return await Isolate.run(() => _setFullScreenSync(pid, add: true));
    } on X11Exception {
      // Best effort: the caller sizes the window by hand instead.
      return false;
    }
  }

  @override
  Future<void> clear() async {
    try {
      await Isolate.run(() => _setFullScreenSync(pid, add: false));
    } on X11Exception {
      // Nothing to undo if the request never went out.
    }
  }
}

/// `_NET_WM_STATE_REMOVE` / `_NET_WM_STATE_ADD`.
const int _stateRemove = 0;
const int _stateAdd = 1;

/// Source indication: "normal application", per EWMH.
const int _sourceApplication = 1;

/// `ClientMessage`, and the masks that route the message to the window manager
/// instead of to the window itself.
const int _clientMessage = 33;
const int _substructureNotifyMask = 1 << 19;
const int _substructureRedirectMask = 1 << 20;

/// Adds or removes fullscreen for the calling process's window, spanning every
/// monitor. Returns whether the request was sent.
bool _setFullScreenSync(int processId, {required bool add}) {
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
    if (!supportsAll(x11, display, scratch, root, const [
      '_NET_WM_STATE',
      '_NET_WM_STATE_FULLSCREEN',
      '_NET_WM_FULLSCREEN_MONITORS',
    ])) {
      return false;
    }

    final windows = ownWindows(x11, display, scratch, root, processId);
    if (windows.isEmpty) return false;
    final window = windows.first;

    final state = internAtom(x11, display, scratch, '_NET_WM_STATE');
    final fullScreen = internAtom(
      x11,
      display,
      scratch,
      '_NET_WM_STATE_FULLSCREEN',
    );
    _sendClientMessage(x11, display, scratch, root, window, state, [
      add ? _stateAdd : _stateRemove,
      fullScreen,
      0,
      _sourceApplication,
      0,
    ]);

    if (add) {
      // Which monitors to stretch over, as edge indices: top, bottom, left,
      // right. Sent after the state change, since a window manager only has
      // somewhere to apply it once the window is fullscreen.
      final edges = _monitorEdges(x11, display);
      if (edges == null) return false;
      final monitors = internAtom(
        x11,
        display,
        scratch,
        '_NET_WM_FULLSCREEN_MONITORS',
      );
      _sendClientMessage(x11, display, scratch, root, window, monitors, [
        edges.top,
        edges.bottom,
        edges.left,
        edges.right,
        _sourceApplication,
      ]);
    }

    x11.flush(display);
    if (!add) {
      // Leaving fullscreen is not instant, and the window manager restores the
      // pre-fullscreen geometry while it happens. Whoever tears the overlay
      // down next would have its own `setBounds` overwritten by that, so wait
      // for the state to be really gone.
      _awaitStateGone(x11, display, scratch, window, fullScreen);
    }
    return true;
  } finally {
    x11.free(scratch);
    x11.closeDisplay(display);
  }
}

/// Blocks until [atom] is off the window's `_NET_WM_STATE`, or ~500 ms pass.
///
/// Runs on a background isolate, so the sleep costs the UI nothing.
void _awaitStateGone(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int window,
  int atom,
) {
  const step = Duration(milliseconds: 20);
  final state = internAtom(x11, display, scratch, '_NET_WM_STATE');
  if (state == 0) return;

  final deadline = DateTime.now().add(const Duration(milliseconds: 500));
  while (DateTime.now().isBefore(deadline)) {
    final current = readCardinals(x11, display, scratch, window, state);
    if (!current.contains(atom)) return;
    sleep(step);
  }
}

/// Builds an `XClientMessageEvent` in [scratch] and posts it to the window
/// manager.
///
/// Field order and padding mirror `X11/Xlib.h` on LP64: `type` and the
/// `send_event` flag are 32-bit but each sits in its own 64-bit slot, and the
/// payload is five `long`s.
void _sendClientMessage(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int root,
  int window,
  int messageType,
  List<int> data,
) {
  // An XEvent is a union of every event struct — 192 bytes — and Xlib reads all
  // of it, so the whole block is zeroed first.
  const eventBytes = 192;
  final event = scratch + callerScratchOffset;
  for (var i = 0; i < eventBytes; i++) {
    event[i] = 0;
  }

  event.cast<Int32>().value = _clientMessage;
  (event + 8).cast<UnsignedLong>().value = 0; // serial
  (event + 16).cast<Int32>().value = 1; // send_event
  (event + 24).cast<Pointer<Void>>().value = display;
  (event + 32).cast<UnsignedLong>().value = window;
  (event + 40).cast<UnsignedLong>().value = messageType;
  (event + 48).cast<Int32>().value = 32; // format: five 32-bit values
  final payload = (event + 56).cast<Long>();
  for (var i = 0; i < data.length && i < 5; i++) {
    payload[i] = data[i];
  }

  const propagate = 0;
  x11.sendEvent(
    display,
    root,
    propagate,
    _substructureNotifyMask | _substructureRedirectMask,
    event,
  );
}

/// Indices of the monitors at each edge of the desktop, or `null` when the
/// layout cannot be read.
({int top, int bottom, int left, int right})? _monitorEdges(
  X11Lib x11,
  Pointer<Void> display,
) {
  final XineramaLib xinerama;
  try {
    xinerama = XineramaLib.open();
  } on X11Exception {
    return null;
  }

  final count = x11.malloc(8).cast<Int32>();
  if (count == nullptr) return null;
  try {
    count.value = 0;
    final screens = xinerama.queryScreens(display, count);
    if (screens == nullptr || count.value <= 0) return null;
    try {
      var top = 0;
      var bottom = 0;
      var left = 0;
      var right = 0;
      for (var i = 1; i < count.value; i++) {
        final screen = screens[i];
        if (screen.yOrg < screens[top].yOrg) top = i;
        if (screen.yOrg + screen.height >
            screens[bottom].yOrg + screens[bottom].height) {
          bottom = i;
        }
        if (screen.xOrg < screens[left].xOrg) left = i;
        if (screen.xOrg + screen.width >
            screens[right].xOrg + screens[right].width) {
          right = i;
        }
      }
      return (top: top, bottom: bottom, left: left, right: right);
    } finally {
      x11.freeData(screens.cast<Uint8>());
    }
  } finally {
    x11.free(count.cast<Uint8>());
  }
}
