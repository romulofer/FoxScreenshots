import 'dart:ffi';
import 'dart:isolate';
import 'dart:typed_data';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../image/png_codec.dart';
import '../image/raw_pixels.dart';
import 'screen_capture_service.dart';
import 'x11/x11_bindings.dart';

/// Linux/X11 capture backend (SPEC §2.1).
///
/// Reads pixels straight off the root window with `XGetImage`, which spans the
/// whole multi-monitor virtual screen, so no compositing step is needed. The
/// grab and the PNG encode both run in a background isolate: a 4K frame would
/// otherwise stall the UI for hundreds of milliseconds.
///
/// Wayland sessions are rejected up front by [defaultScreenCaptureService] —
/// under Wayland the X root window is not the real desktop, so a grab would
/// silently produce a black image instead of failing.
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

/// Reads the focused window's geometry, translated to root coordinates.
///
/// Runs inside an isolate. Returns `null` when the focus is the root window,
/// nothing, or the pointer (`PointerRoot`) — i.e. there is no window to frame.
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

  // One scratch block for every out-parameter below; offsets are 8-byte
  // aligned where an X `Window` (unsigned long) lands.
  final scratch = x11.malloc(96);
  try {
    final focus = scratch.cast<UnsignedLong>();
    final revertTo = (scratch + 8).cast<Int32>();
    x11.getInputFocus(display, focus, revertTo);

    final window = focus.value;
    final root = x11.defaultRootWindow(display);
    if (window == none || window == pointerRoot || window == root) return null;

    final rootOut = (scratch + 16).cast<UnsignedLong>();
    final xOut = (scratch + 24).cast<Int32>();
    final yOut = (scratch + 28).cast<Int32>();
    final widthOut = (scratch + 32).cast<Uint32>();
    final heightOut = (scratch + 36).cast<Uint32>();
    final borderOut = (scratch + 40).cast<Uint32>();
    final depthOut = (scratch + 44).cast<Uint32>();
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

    // The geometry above is relative to the window's parent, so map (0,0) of
    // the window onto the root to get virtual-screen coordinates.
    final absX = (scratch + 48).cast<Int32>();
    final absY = (scratch + 52).cast<Int32>();
    final child = (scratch + 56).cast<UnsignedLong>();
    x11.translateCoordinates(display, window, root, 0, 0, absX, absY, child);

    return CaptureRegion(
      x: absX.value,
      y: absY.value,
      width: widthOut.value,
      height: heightOut.value,
    ).clampedTo(
      x11.displayWidth(display, x11.defaultScreen(display)),
      x11.displayHeight(display, x11.defaultScreen(display)),
    );
  } finally {
    x11.free(scratch);
    x11.closeDisplay(display);
  }
}

/// Converts an [XImage]'s pixel buffer to tightly-packed RGBA.
Uint8List _toRgba(XImage image) {
  const lsbFirst = 0;
  if (image.byteOrder != lsbFirst) {
    throw const X11Exception('Unsupported X server byte order (MSBFirst)');
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

  final bytes = image.data.asTypedList(image.bytesPerLine * image.height);
  return rgbaFromRaw(
    source: bytes,
    width: image.width,
    height: image.height,
    bytesPerLine: image.bytesPerLine,
    bitsPerPixel: image.bitsPerPixel,
    order: order,
  );
}
