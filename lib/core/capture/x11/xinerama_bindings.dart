// Minimal `dart:ffi` bindings for the one Xinerama call the overlay needs:
// the monitor layout, in the order the window manager numbers it.
//
// `libXinerama.so.1` is a direct dependency of GTK 3, so it is already loaded
// in every session this app can run in — no new install requirement (SPEC §7).

import 'dart:ffi';

import 'x11_bindings.dart';

/// One monitor as Xinerama reports it. Field order mirrors
/// `X11/extensions/Xinerama.h` exactly.
final class XineramaScreenInfo extends Struct {
  @Int32()
  external int screenNumber;
  @Int16()
  external int xOrg;
  @Int16()
  external int yOrg;
  @Int16()
  external int width;
  @Int16()
  external int height;
}

typedef _XineramaQueryScreensNative = Pointer<XineramaScreenInfo> Function(
  Pointer<Void> display,
  Pointer<Int32> count,
);
typedef XineramaQueryScreensDart = Pointer<XineramaScreenInfo> Function(
  Pointer<Void> display,
  Pointer<Int32> count,
);
/// A loaded `libXinerama.so.1` plus the bound symbols.
class XineramaLib {
  XineramaLib._(DynamicLibrary lib)
    : queryScreens = lib
          .lookup<NativeFunction<_XineramaQueryScreensNative>>(
            'XineramaQueryScreens',
          )
          .asFunction<XineramaQueryScreensDart>();

  /// Opens the library, or throws [X11Exception] when it is not installed.
  factory XineramaLib.open() {
    try {
      return XineramaLib._(DynamicLibrary.open('libXinerama.so.1'));
    } on Object catch (e) {
      throw X11Exception('libXinerama.so.1 could not be loaded: $e');
    }
  }

  final XineramaQueryScreensDart queryScreens;
}
