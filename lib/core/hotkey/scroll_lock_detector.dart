import 'dart:async';
import 'dart:ffi';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../capture/x11/x11_bindings.dart';
import '../desktop/session_type.dart';

/// Reports whether Scroll Lock is on.
///
/// It matters because X11 only fires a global hotkey grab when the lock-key
/// state matches the grab, and `libkeybinder` does not account for Scroll Lock:
/// with it on, every global hotkey silently stops working.
abstract interface class ScrollLockDetector {
  Future<bool> isActive();
}

/// Never active; used where the problem cannot happen (Wayland, Windows, macOS)
/// and in tests.
class NoScrollLockDetector implements ScrollLockDetector {
  const NoScrollLockDetector();

  @override
  Future<bool> isActive() async => false;
}

/// Bit of the Xkb indicator state that carries the Scroll Lock LED (indicator
/// 3 of the core keyboard: Caps Lock, Num Lock, Scroll Lock) — the same bit
/// `xset q` reports as "Scroll Lock".
const int scrollLockIndicatorMask = 0x4;

/// `XkbUseCoreKbd`: ask about the core keyboard device.
const int _xkbUseCoreKbd = 0x0100;

/// Whether an `XkbGetIndicatorState` mask has Scroll Lock lit.
bool isScrollLockLit(int indicatorMask) =>
    indicatorMask & scrollLockIndicatorMask != 0;

typedef _XkbGetIndicatorStateNative = Int32 Function(
  Pointer<Void>,
  Uint32,
  Pointer<Uint32>,
);
typedef _XkbGetIndicatorStateDart = int Function(
  Pointer<Void>,
  int,
  Pointer<Uint32>,
);

/// Reads the keyboard LED state straight from the X server through `libX11`
/// (no extra process or dependency).
///
/// Runs in a short-lived isolate, like the display probe in the dependency
/// checker: Xlib calls block on the server, and the display connection cannot
/// be shared across isolates. Any failure reads as "not active".
class X11ScrollLockDetector implements ScrollLockDetector {
  const X11ScrollLockDetector();

  @override
  Future<bool> isActive() async {
    try {
      return await Isolate.run(_readScrollLock);
    } on Object {
      return false;
    }
  }
}

bool _readScrollLock() {
  final x11 = X11Lib.open();
  final display = x11.openDisplay(nullptr);
  if (display == nullptr) return false;
  final state = x11.malloc(4).cast<Uint32>();
  try {
    final getState = DynamicLibrary.open('libX11.so.6')
        .lookup<NativeFunction<_XkbGetIndicatorStateNative>>(
          'XkbGetIndicatorState',
        )
        .asFunction<_XkbGetIndicatorStateDart>();
    state.value = 0;
    // Returns Success (0) when it filled `state`.
    if (getState(display, _xkbUseCoreKbd, state) != 0) return false;
    return isScrollLockLit(state.value);
  } finally {
    x11.free(state.cast<Uint8>());
    x11.closeDisplay(display);
  }
}

final scrollLockDetectorProvider = Provider<ScrollLockDetector>((ref) {
  if (Platform.isLinux && currentDesktopSession() == DesktopSession.x11) {
    return const X11ScrollLockDetector();
  }
  return const NoScrollLockDetector();
});

/// How often the lock state is re-read while something is watching it.
const Duration scrollLockPollInterval = Duration(seconds: 2);

/// Live Scroll Lock state. Auto-disposed, so it only polls while the banner
/// (or anything else) is on screen.
///
/// Polls with a cancellable [Timer] rather than a `delayed` loop: a loop's
/// pending delay outlives the provider, which leaves a live timer behind once
/// the widget tree is gone.
final scrollLockActiveProvider = StreamProvider.autoDispose<bool>((ref) {
  final detector = ref.watch(scrollLockDetectorProvider);
  final controller = StreamController<bool>();
  Timer? timer;
  var disposed = false;
  bool? last;

  Future<void> poll() async {
    final active = await detector.isActive();
    if (disposed) return;
    if (active != last) {
      last = active;
      controller.add(active);
    }
    timer = Timer(scrollLockPollInterval, poll);
  }

  ref.onDispose(() {
    disposed = true;
    timer?.cancel();
    controller.close();
  });
  poll();
  return controller.stream;
});
