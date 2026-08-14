import 'dart:ffi';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../image/png_codec.dart';
import '../image/raw_pixels.dart';
import 'screen_capture_service.dart';
import 'x11/x11_bindings.dart';
import 'x11/x11_properties.dart';

/// Linux/X11 capture backend (SPEC §2.1).
///
/// Reads pixels straight off the root window with `XGetImage`, which spans the
/// whole multi-monitor virtual screen, so no compositing step is needed. The
/// grab and the PNG encode both run in a background isolate: a 4K frame would
/// otherwise stall the UI for hundreds of milliseconds.
///
/// Wayland sessions never reach this backend: [defaultScreenCaptureService]
/// sends them to the portal instead, because the X root window under Wayland is
/// not the real desktop and a grab there would quietly return a black image.
class X11ScreenCaptureService implements ScreenCaptureService {
  const X11ScreenCaptureService();

  @override
  Future<CaptureResult> grabFullVirtualScreen() => _grab(null);

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) => _grab(region);

  @override
  Future<CaptureRegion?> activeWindowRegion() async {
    try {
      return await Isolate.run(_activeWindowRegionSync);
    } on X11Exception catch (e) {
      throw CaptureException(
        CaptureFailure.displayUnavailable,
        details: e.message,
      );
    }
  }

  @override
  Future<({int width, int height})> virtualScreenSize() async {
    try {
      return await Isolate.run(_virtualScreenSizeSync);
    } on X11Exception catch (e) {
      throw CaptureException(
        CaptureFailure.displayUnavailable,
        details: e.message,
      );
    }
  }

  Future<CaptureResult> _grab(CaptureRegion? region) async {
    final ({Uint8List png, int width, int height}) frame;
    try {
      frame = await Isolate.run(() => _grabSync(region));
    } on X11Exception catch (e) {
      // Keep Xlib details out of the UI layer: one exception type to catch.
      throw CaptureException(
        CaptureFailure.displayUnavailable,
        details: e.message,
      );
    }
    return CaptureResult(
      id: 'shot-${DateTime.now().microsecondsSinceEpoch}',
      pngBytes: frame.png,
      width: frame.width,
      height: frame.height,
      takenAt: DateTime.now(),
    );
  }
}

/// Grabs [region] (or the whole virtual screen when `null`) and encodes it.
///
/// Runs inside an isolate, so it opens its own X connection and closes it
/// before returning — nothing X-related escapes this function.
({Uint8List png, int width, int height}) _grabSync(CaptureRegion? region) {
  final x11 = X11Lib.open();
  final display = x11.openDisplay(nullptr);
  if (display == nullptr) {
    throw const X11Exception(
      'Could not connect to the X server (is DISPLAY set?)',
    );
  }

  try {
    final screen = x11.defaultScreen(display);
    final root = x11.defaultRootWindow(display);
    final screenWidth = x11.displayWidth(display, screen);
    final screenHeight = x11.displayHeight(display, screen);

    final area =
        (region ??
                CaptureRegion(
                  x: 0,
                  y: 0,
                  width: screenWidth,
                  height: screenHeight,
                ))
            .clampedTo(screenWidth, screenHeight);
    if (area.isEmpty) {
      throw const X11Exception('The selected region is outside the screen');
    }

    final image = x11.getImage(
      display,
      root,
      area.x,
      area.y,
      area.width,
      area.height,
      allPlanes,
      zPixmap,
    );
    if (image == nullptr) {
      throw const X11Exception('XGetImage returned no data');
    }

    try {
      final rgba = _toRgba(image.ref);
      return (
        png: encodeRgbaSync(rgba, area.width, area.height, PngCodec.fastLevel),
        width: area.width,
        height: area.height,
      );
    } finally {
      x11.destroyImage(image);
    }
  } finally {
    x11.closeDisplay(display);
  }
}

/// Reads the virtual screen dimensions in physical pixels. Runs in an isolate.
({int width, int height}) _virtualScreenSizeSync() {
  final x11 = X11Lib.open();
  final display = x11.openDisplay(nullptr);
  if (display == nullptr) {
    throw const X11Exception(
      'Could not connect to the X server (is DISPLAY set?)',
    );
  }
  try {
    final screen = x11.defaultScreen(display);
    return (
      width: x11.displayWidth(display, screen),
      height: x11.displayHeight(display, screen),
    );
  } finally {
    x11.closeDisplay(display);
  }
}

/// Reads the active window's geometry, translated to root coordinates.
///
/// Runs inside an isolate. Returns `null` when nothing usable is focused.
///
/// The window manager's own answer (`_NET_ACTIVE_WINDOW`) is asked for first,
/// because `XGetInputFocus` frequently reports a 1x1 focus proxy — Muffin,
/// Metacity and Mutter all park the input focus on one — and framing that
/// yields an empty region, which the UI reports as "no active window".
CaptureRegion? _activeWindowRegionSync() {
  const none = 0;
  const pointerRoot = 1;

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
    final activeAtom = internAtom(x11, display, scratch, '_NET_ACTIVE_WINDOW');
    final candidates = [
      ...readCardinals(x11, display, scratch, root, activeAtom),
      _focusedWindow(x11, display, scratch),
    ];

    for (final window in candidates) {
      if (window == none || window == pointerRoot || window == root) continue;
      // Our own window is never the answer: the hub is hidden by the time this
      // runs, and framing where it used to be would capture the desktop
      // underneath it.
      if (windowPid(x11, display, scratch, window) == pid) continue;

      final region = _windowRegionSync(x11, display, scratch, root, window);
      if (region != null && !region.isEmpty) return region;
    }
    return null;
  } finally {
    x11.free(scratch);
    x11.closeDisplay(display);
  }
}

/// The window holding the input focus, or 0.
int _focusedWindow(X11Lib x11, Pointer<Void> display, Pointer<Uint8> scratch) {
  final focus = (scratch + callerScratchOffset).cast<UnsignedLong>();
  final revertTo = (scratch + callerScratchOffset + 8).cast<Int32>();
  focus.value = 0;
  x11.getInputFocus(display, focus, revertTo);
  return focus.value;
}

/// Geometry of [window] in root coordinates, clipped to the virtual screen.
///
/// A window one pixel wide or tall is a focus proxy rather than something the
/// user meant to capture, so it is reported as nothing at all.
CaptureRegion? _windowRegionSync(
  X11Lib x11,
  Pointer<Void> display,
  Pointer<Uint8> scratch,
  int root,
  int window,
) {
  final base = scratch + callerScratchOffset;
  final rootOut = (base + 16).cast<UnsignedLong>();
  final xOut = (base + 24).cast<Int32>();
  final yOut = (base + 28).cast<Int32>();
  final widthOut = (base + 32).cast<Uint32>();
  final heightOut = (base + 36).cast<Uint32>();
  final borderOut = (base + 40).cast<Uint32>();
  final depthOut = (base + 44).cast<Uint32>();
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
  if (widthOut.value <= 1 || heightOut.value <= 1) return null;

  // The geometry above is relative to the window's parent, so map (0,0) of
  // the window onto the root to get virtual-screen coordinates.
  final absX = (base + 48).cast<Int32>();
  final absY = (base + 52).cast<Int32>();
  final child = (base + 56).cast<UnsignedLong>();
  if (x11.translateCoordinates(
        display,
        window,
        root,
        0,
        0,
        absX,
        absY,
        child,
      ) ==
      0) {
    return null;
  }

  final screen = x11.defaultScreen(display);
  return CaptureRegion(
    x: absX.value,
    y: absY.value,
    width: widthOut.value,
    height: heightOut.value,
  ).clampedTo(
    x11.displayWidth(display, screen),
    x11.displayHeight(display, screen),
  );
}

/// Converts an [XImage]'s pixel buffer to tightly-packed RGBA.
Uint8List _toRgba(XImage image) {
  const lsbFirst = 0;
  const maxDimension = 16384;
  if (image.byteOrder != lsbFirst) {
    throw const X11Exception('Unsupported X server byte order (MSBFirst)');
  }
  if (image.width <= 0 ||
      image.height <= 0 ||
      image.width > maxDimension ||
      image.height > maxDimension) {
    throw X11Exception(
      'XImage dimensions out of range (${image.width}x${image.height})',
    );
  }
  if (image.data == nullptr) {
    throw const X11Exception('XImage has a null data pointer');
  }
  if (image.bytesPerLine < image.width * (image.bitsPerPixel ~/ 8)) {
    throw const X11Exception('XImage bytesPerLine shorter than a scanline');
  }
  final byteLength = image.bytesPerLine * image.height;
  if (byteLength <= 0) {
    throw const X11Exception('XImage byte length overflow or empty');
  }

  // With LSBFirst byte order the low mask byte is the first byte in memory.
  final order = switch ((image.blueMask & 0xFF, image.redMask & 0xFF)) {
    (0xFF, _) => RawPixelOrder.bgra,
    (_, 0xFF) => RawPixelOrder.rgba,
    _ => throw X11Exception(
      'Unsupported pixel layout (red 0x${image.redMask.toRadixString(16)}, '
      'blue 0x${image.blueMask.toRadixString(16)})',
    ),
  };

  final bytes = image.data.asTypedList(byteLength);
  return rgbaFromRaw(
    source: bytes,
    width: image.width,
    height: image.height,
    bytesPerLine: image.bytesPerLine,
    bitsPerPixel: image.bitsPerPixel,
    order: order,
  );
}
