// Minimal `dart:ffi` bindings for the Xlib calls the capture backend needs.
//
// Only the handful of symbols used to read the root window's pixels are bound;
// there is no code generation and no extra native dependency — `libX11.so.6`
// ships with every X11 session (SPEC §7: no bundled binaries).

import 'dart:ffi';

/// `ZPixmap` — the `XGetImage` format that returns packed pixel data.
const int zPixmap = 2;

/// `AllPlanes` is `~0UL`; in Dart's signed 64-bit ints that is `-1`, which the
/// FFI marshals back to an all-ones `unsigned long`.
const int allPlanes = -1;

/// Layout of the `f` member of `XImage` (Xlib's per-image function table).
///
/// Only [destroyImage] is called; the rest are here to keep the struct offsets
/// correct.
final class XImageFuncs extends Struct {
  external Pointer<Void> createImage;
  external Pointer<NativeFunction<Int32 Function(Pointer<XImage>)>>
  destroyImage;
  external Pointer<Void> getPixel;
  external Pointer<Void> putPixel;
  external Pointer<Void> subImage;
  external Pointer<Void> addPixel;
}

/// Xlib's `XImage`. Field order and types mirror `X11/Xlib.h` exactly — the FFI
/// computes the C ABI offsets from the declarations below, so do not reorder.
final class XImage extends Struct {
  @Int32()
  external int width;
  @Int32()
  external int height;
  @Int32()
  external int xoffset;
  @Int32()
  external int format;
  external Pointer<Uint8> data;
  @Int32()
  external int byteOrder;
  @Int32()
  external int bitmapUnit;
  @Int32()
  external int bitmapBitOrder;
  @Int32()
  external int bitmapPad;
  @Int32()
  external int depth;
  @Int32()
  external int bytesPerLine;
  @Int32()
  external int bitsPerPixel;
  @UnsignedLong()
  external int redMask;
  @UnsignedLong()
  external int greenMask;
  @UnsignedLong()
  external int blueMask;
  external Pointer<Void> objData;
  external XImageFuncs f;
}

typedef _XOpenDisplayNative = Pointer<Void> Function(Pointer<Void>);
typedef _XCloseDisplayNative = Int32 Function(Pointer<Void>);
typedef XCloseDisplayDart = int Function(Pointer<Void>);
typedef _XDefaultRootWindowNative = UnsignedLong Function(Pointer<Void>);
typedef XDefaultRootWindowDart = int Function(Pointer<Void>);
typedef _XScreenOfDisplayNative = Int32 Function(Pointer<Void>);
typedef XScreenOfDisplayDart = int Function(Pointer<Void>);
typedef _XDisplayExtentNative = Int32 Function(Pointer<Void>, Int32);
typedef XDisplayExtentDart = int Function(Pointer<Void>, int);
typedef _XGetImageNative = Pointer<XImage> Function(
  Pointer<Void> display,
  UnsignedLong drawable,
  Int32 x,
  Int32 y,
  UnsignedInt width,
  UnsignedInt height,
  UnsignedLong planeMask,
  Int32 format,
);
typedef XGetImageDart = Pointer<XImage> Function(
  Pointer<Void> display,
  int drawable,
  int x,
  int y,
  int width,
  int height,
  int planeMask,
  int format,
);
typedef _XGetInputFocusNative = Int32 Function(
  Pointer<Void> display,
  Pointer<UnsignedLong> focusReturn,
  Pointer<Int32> revertToReturn,
);
typedef XGetInputFocusDart = int Function(
  Pointer<Void> display,
  Pointer<UnsignedLong> focusReturn,
  Pointer<Int32> revertToReturn,
);
typedef _XGetGeometryNative = Int32 Function(
  Pointer<Void> display,
  UnsignedLong drawable,
  Pointer<UnsignedLong> root,
  Pointer<Int32> x,
  Pointer<Int32> y,
  Pointer<Uint32> width,
  Pointer<Uint32> height,
  Pointer<Uint32> borderWidth,
  Pointer<Uint32> depth,
);
typedef XGetGeometryDart = int Function(
  Pointer<Void> display,
  int drawable,
  Pointer<UnsignedLong> root,
  Pointer<Int32> x,
  Pointer<Int32> y,
  Pointer<Uint32> width,
  Pointer<Uint32> height,
  Pointer<Uint32> borderWidth,
  Pointer<Uint32> depth,
);
typedef _XTranslateCoordinatesNative = Int32 Function(
  Pointer<Void> display,
  UnsignedLong sourceWindow,
  UnsignedLong destWindow,
  Int32 sourceX,
  Int32 sourceY,
  Pointer<Int32> destX,
  Pointer<Int32> destY,
  Pointer<UnsignedLong> child,
);
typedef XTranslateCoordinatesDart = int Function(
  Pointer<Void> display,
  int sourceWindow,
  int destWindow,
  int sourceX,
  int sourceY,
  Pointer<Int32> destX,
  Pointer<Int32> destY,
  Pointer<UnsignedLong> child,
);
typedef _XInternAtomNative = UnsignedLong Function(
  Pointer<Void> display,
  Pointer<Char> name,
  Int32 onlyIfExists,
);
typedef XInternAtomDart = int Function(
  Pointer<Void> display,
  Pointer<Char> name,
  int onlyIfExists,
);
typedef _XGetWindowPropertyNative = Int32 Function(
  Pointer<Void> display,
  UnsignedLong window,
  UnsignedLong property,
  Long offset,
  Long length,
  Int32 delete,
  UnsignedLong requestedType,
  Pointer<UnsignedLong> actualType,
  Pointer<Int32> actualFormat,
  Pointer<UnsignedLong> itemCount,
  Pointer<UnsignedLong> bytesAfter,
  Pointer<Pointer<Uint8>> data,
);
typedef XGetWindowPropertyDart = int Function(
  Pointer<Void> display,
  int window,
  int property,
  int offset,
  int length,
  int delete,
  int requestedType,
  Pointer<UnsignedLong> actualType,
  Pointer<Int32> actualFormat,
  Pointer<UnsignedLong> itemCount,
  Pointer<UnsignedLong> bytesAfter,
  Pointer<Pointer<Uint8>> data,
);
typedef _XFreeNative = Int32 Function(Pointer<Uint8>);
typedef XFreeDart = int Function(Pointer<Uint8>);
typedef _XSendEventNative = Int32 Function(
  Pointer<Void> display,
  UnsignedLong window,
  Int32 propagate,
  Long eventMask,
  Pointer<Uint8> event,
);
typedef XSendEventDart = int Function(
  Pointer<Void> display,
  int window,
  int propagate,
  int eventMask,
  Pointer<Uint8> event,
);
typedef _XFlushNative = Int32 Function(Pointer<Void>);
typedef XFlushDart = int Function(Pointer<Void>);
typedef _MallocNative = Pointer<Uint8> Function(IntPtr);
typedef MallocDart = Pointer<Uint8> Function(int);
typedef _FreeNative = Void Function(Pointer<Uint8>);
typedef FreeDart = void Function(Pointer<Uint8>);

/// Thrown when the X server cannot be reached or a call fails. Carries a
/// human-readable reason for the UI (never any screen content).
class X11Exception implements Exception {
  const X11Exception(this.message);

  final String message;

  @override
  String toString() => 'X11Exception: $message';
}

/// A loaded `libX11.so.6` plus the bound symbols.
///
/// Load one per isolate: the library handle and the display connection are not
/// shareable across isolates.
class X11Lib {
  X11Lib._(DynamicLibrary lib)
    : openDisplay = lib
          .lookup<NativeFunction<_XOpenDisplayNative>>('XOpenDisplay')
          .asFunction(),
      closeDisplay = lib
          .lookup<NativeFunction<_XCloseDisplayNative>>('XCloseDisplay')
          .asFunction<XCloseDisplayDart>(),
      defaultRootWindow = lib
          .lookup<NativeFunction<_XDefaultRootWindowNative>>(
            'XDefaultRootWindow',
          )
          .asFunction<XDefaultRootWindowDart>(),
      defaultScreen = lib
          .lookup<NativeFunction<_XScreenOfDisplayNative>>('XDefaultScreen')
          .asFunction<XScreenOfDisplayDart>(),
      displayWidth = lib
          .lookup<NativeFunction<_XDisplayExtentNative>>('XDisplayWidth')
          .asFunction<XDisplayExtentDart>(),
      displayHeight = lib
          .lookup<NativeFunction<_XDisplayExtentNative>>('XDisplayHeight')
          .asFunction<XDisplayExtentDart>(),
      getImage = lib
          .lookup<NativeFunction<_XGetImageNative>>('XGetImage')
          .asFunction<XGetImageDart>(),
      getInputFocus = lib
          .lookup<NativeFunction<_XGetInputFocusNative>>('XGetInputFocus')
          .asFunction<XGetInputFocusDart>(),
      getGeometry = lib
          .lookup<NativeFunction<_XGetGeometryNative>>('XGetGeometry')
          .asFunction<XGetGeometryDart>(),
      translateCoordinates = lib
          .lookup<NativeFunction<_XTranslateCoordinatesNative>>(
            'XTranslateCoordinates',
          )
          .asFunction<XTranslateCoordinatesDart>(),
      internAtom = lib
          .lookup<NativeFunction<_XInternAtomNative>>('XInternAtom')
          .asFunction<XInternAtomDart>(),
      getWindowProperty = lib
          .lookup<NativeFunction<_XGetWindowPropertyNative>>(
            'XGetWindowProperty',
          )
          .asFunction<XGetWindowPropertyDart>(),
      freeData = lib
          .lookup<NativeFunction<_XFreeNative>>('XFree')
          .asFunction<XFreeDart>(),
      sendEvent = lib
          .lookup<NativeFunction<_XSendEventNative>>('XSendEvent')
          .asFunction<XSendEventDart>(),
      flush = lib
          .lookup<NativeFunction<_XFlushNative>>('XFlush')
          .asFunction<XFlushDart>(),
      // libc, for the small scratch buffer the out-parameter calls need.
      malloc = DynamicLibrary.process()
          .lookup<NativeFunction<_MallocNative>>('malloc')
          .asFunction<MallocDart>(),
      free = DynamicLibrary.process()
          .lookup<NativeFunction<_FreeNative>>('free')
          .asFunction<FreeDart>();

  /// Opens `libX11.so.6`. Throws [X11Exception] when it is missing (for
  /// example on a pure Wayland session without Xlib installed).
  factory X11Lib.open() {
    try {
      return X11Lib._(DynamicLibrary.open('libX11.so.6'));
    } on Object catch (e) {
      throw X11Exception('libX11.so.6 could not be loaded: $e');
    }
  }

  final Pointer<Void> Function(Pointer<Void>) openDisplay;
  final XCloseDisplayDart closeDisplay;
  final XDefaultRootWindowDart defaultRootWindow;
  final XScreenOfDisplayDart defaultScreen;
  final XDisplayExtentDart displayWidth;
  final XDisplayExtentDart displayHeight;
  final XGetImageDart getImage;
  final XGetInputFocusDart getInputFocus;
  final XGetGeometryDart getGeometry;
  final XTranslateCoordinatesDart translateCoordinates;
  final XInternAtomDart internAtom;
  final XGetWindowPropertyDart getWindowProperty;

  /// `XFree` — releases buffers Xlib allocated (property data, window lists).
  final XFreeDart freeData;
  final XSendEventDart sendEvent;
  final XFlushDart flush;
  final MallocDart malloc;
  final FreeDart free;

  /// Releases an image with Xlib's own destructor (frees both the pixel buffer
  /// and the struct).
  void destroyImage(Pointer<XImage> image) {
    image.ref.f.destroyImage.asFunction<int Function(Pointer<XImage>)>().call(
      image,
    );
  }
}
