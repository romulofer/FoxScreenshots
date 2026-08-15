import 'dart:ffi';
import 'dart:io' show pid;
import 'dart:isolate';
import 'dart:typed_data';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../image/png_codec.dart';
import '../image/raw_pixels.dart';
import 'macos/core_graphics_bindings.dart';
import 'screen_capture_service.dart';

/// macOS capture backend (SPEC §2.1).
///
/// Composites the on-screen windows with CoreGraphics `CGWindowListCreateImage`,
/// then reads the resulting `CGImage`'s pixels (32-bit BGRA) through its data
/// provider. The grab and the PNG encode run in a background isolate so a Retina
/// frame does not stall the UI, exactly as the X11 and Windows backends do.
///
/// Coordinates: geometry from CoreGraphics is in points (the virtual screen's
/// top-left is the union of every display's bounds), while the captured image is
/// in native pixels. A single scale — the main display's backing factor — bridges
/// the two. A region is cropped straight out of the full image in pixel space via
/// `CGImageCreateWithImageInRect`, so no point↔pixel arithmetic touches the crop.
/// Mixed-DPI multi-monitor layouts, where the scale is not uniform, are a known
/// limitation.
///
/// Screen capture on macOS 10.15+ requires the Screen Recording permission. The
/// backend preflights it and asks the system to prompt, raising
/// [CaptureFailure.screenRecordingDenied] until the user grants it.
class MacosScreenCaptureService implements ScreenCaptureService {
  const MacosScreenCaptureService();

  @override
  Future<CaptureResult> grabFullVirtualScreen() => _grab(null);

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) => _grab(region);

  @override
  Future<CaptureRegion?> activeWindowRegion() async {
    try {
      return await Isolate.run(_activeWindowRegionSync);
    } on MacCaptureException catch (e) {
      throw _asCaptureException(e);
    }
  }

  @override
  Future<({int width, int height})> virtualScreenSize() async {
    try {
      return await Isolate.run(_virtualScreenSizeSync);
    } on MacCaptureException catch (e) {
      throw _asCaptureException(e);
    }
  }

  Future<CaptureResult> _grab(CaptureRegion? region) async {
    final ({Uint8List png, int width, int height}) frame;
    try {
      frame = await Isolate.run(() => _grabSync(region));
    } on MacCaptureException catch (e) {
      throw _asCaptureException(e);
    }
    return CaptureResult(
      id: 'shot-${DateTime.now().microsecondsSinceEpoch}',
      pngBytes: frame.png,
      width: frame.width,
      height: frame.height,
      takenAt: DateTime.now(),
    );
  }

  /// Keeps CoreGraphics details out of the UI layer: one exception type to catch,
  /// with the missing-permission case mapped to its own dedicated message.
  CaptureException _asCaptureException(MacCaptureException e) =>
      CaptureException(
        e.deniedPermission
            ? CaptureFailure.screenRecordingDenied
            : CaptureFailure.displayUnavailable,
        details: e.message,
      );
}

/// Scratch heap for the out-parameter calls, laid out so each typed slice is
/// aligned: a 16-entry display list at 0, its count at 64, two `CGRect`s at 72
/// and 104, and a `CFNumber` int at 136.
const int _maxDisplays = 16;
const int _scratchBytes = 160;
const int _countOffset = 64;
const int _rectAOffset = 72;
const int _rectBOffset = 104;
const int _numberOffset = 136;

/// The virtual screen's origin and size in points, plus the main display's
/// backing scale that converts those points to captured pixels.
typedef _Layout = ({
  double x,
  double y,
  double width,
  double height,
  double scale,
});

/// Reads the display layout: the union of every active display's bounds (points)
/// and the main display's pixel/point ratio.
_Layout _readLayout(MacCaptureLib lib, Pointer<Uint8> scratch) {
  final displays = scratch.cast<Uint32>();
  final count = (scratch + _countOffset).cast<Uint32>();
  count.value = 0;
  if (lib.getActiveDisplayList(_maxDisplays, displays, count) != 0 ||
      count.value == 0) {
    throw const MacCaptureException('No active display to capture');
  }

  var minX = double.infinity;
  var minY = double.infinity;
  var maxX = double.negativeInfinity;
  var maxY = double.negativeInfinity;
  for (var i = 0; i < count.value; i++) {
    final bounds = lib.displayBounds(displays[i]);
    final x = bounds.origin.x;
    final y = bounds.origin.y;
    if (x < minX) minX = x;
    if (y < minY) minY = y;
    if (x + bounds.size.width > maxX) maxX = x + bounds.size.width;
    if (y + bounds.size.height > maxY) maxY = y + bounds.size.height;
  }

  final mode = lib.copyDisplayMode(lib.mainDisplayId());
  var scale = 1.0;
  if (mode != nullptr) {
    final points = lib.modeGetWidth(mode);
    final pixels = lib.modeGetPixelWidth(mode);
    if (points > 0 && pixels > 0) scale = pixels / points;
    lib.modeRelease(mode);
  }

  return (
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
    scale: scale,
  );
}

/// Ensures the Screen Recording grant is held, prompting for it the first time.
/// Throws a permission-flavoured [MacCaptureException] when it is not granted.
void _requireScreenRecording(MacCaptureLib lib) {
  if (lib.preflightScreenCaptureAccess()) return;
  // Opens System Settings > Privacy the first time; the answer only takes effect
  // on a later launch, so this attempt still fails.
  lib.requestScreenCaptureAccess();
  throw const MacCaptureException(
    'Screen Recording permission is not granted',
    deniedPermission: true,
  );
}

/// Composites the whole virtual screen into a `CGImage`. Caller owns the result
/// and must release it with `CGImageRelease`.
Pointer<Void> _captureVirtualScreen(
  MacCaptureLib lib,
  Pointer<Uint8> scratch,
  _Layout layout,
) {
  final rect = (scratch + _rectAOffset).cast<CGRect>();
  rect.ref.origin.x = layout.x;
  rect.ref.origin.y = layout.y;
  rect.ref.size.width = layout.width;
  rect.ref.size.height = layout.height;

  final image = lib.windowListCreateImage(
    rect.ref,
    kCGWindowListOnScreenOnly,
    kCGNullWindowID,
    kCGWindowImageDefault,
  );
  if (image == nullptr) {
    throw const MacCaptureException(
      'CGWindowListCreateImage returned no image',
    );
  }
  return image;
}

/// Repacks a `CGImage`'s pixels into RGBA and PNG-encodes them. Releases the
/// copied pixel data (but not [image], which the caller owns).
({Uint8List png, int width, int height}) _encodeImage(
  MacCaptureLib lib,
  Pointer<Void> image,
) {
  final width = lib.imageGetWidth(image);
  final height = lib.imageGetHeight(image);
  if (width <= 0 || height <= 0) {
    throw const MacCaptureException('Captured image has no area');
  }

  final provider = lib.imageGetDataProvider(image);
  if (provider == nullptr) {
    throw const MacCaptureException('CGImage has no data provider');
  }
  // CGDataProviderCopyData returns an owned CFData; the byte pointer is valid
  // until it is released.
  final data = lib.dataProviderCopyData(provider);
  if (data == nullptr) {
    throw const MacCaptureException('Could not copy the image pixels');
  }
  try {
    final bytes = lib.dataGetBytePtr(data);
    final length = lib.dataGetLength(data);
    if (bytes == nullptr || length <= 0) {
      throw const MacCaptureException('CGImage pixel buffer is empty');
    }
    // CGWindowListCreateImage yields 32-bit little-endian premultiplied BGRA;
    // rgbaFromRaw forces an opaque alpha, so premultiplication is a no-op here.
    final rgba = rgbaFromRaw(
      source: bytes.asTypedList(length),
      width: width,
      height: height,
      bytesPerLine: lib.imageGetBytesPerRow(image),
      bitsPerPixel: lib.imageGetBitsPerPixel(image),
      order: RawPixelOrder.bgra,
    );
    return (
      png: encodeRgbaSync(rgba, width, height, PngCodec.fastLevel),
      width: width,
      height: height,
    );
  } finally {
    lib.release(data);
  }
}

/// Grabs [region] (or the whole virtual screen when `null`) and encodes it.
///
/// Runs inside an isolate: it opens its own framework bindings, releases every
/// CoreFoundation object it created, and frees the scratch buffer before
/// returning — nothing CoreGraphics-related escapes this function.
({Uint8List png, int width, int height}) _grabSync(CaptureRegion? region) {
  final lib = MacCaptureLib.open();
  _requireScreenRecording(lib);

  final scratch = lib.malloc(_scratchBytes);
  if (scratch == nullptr) {
    throw const MacCaptureException('malloc failed for the scratch buffer');
  }

  Pointer<Void> full = nullptr;
  Pointer<Void> cropped = nullptr;
  try {
    final layout = _readLayout(lib, scratch);
    full = _captureVirtualScreen(lib, scratch, layout);

    if (region == null) return _encodeImage(lib, full);

    final area = region.clampedTo(
      lib.imageGetWidth(full),
      lib.imageGetHeight(full),
    );
    if (area.isEmpty) {
      throw const MacCaptureException(
        'The selected region is outside the screen',
      );
    }
    // CGImageCreateWithImageInRect crops in the image's own pixel space, which is
    // exactly the space the region is expressed in.
    final rect = (scratch + _rectBOffset).cast<CGRect>();
    rect.ref.origin.x = area.x.toDouble();
    rect.ref.origin.y = area.y.toDouble();
    rect.ref.size.width = area.width.toDouble();
    rect.ref.size.height = area.height.toDouble();
    cropped = lib.imageCreateWithImageInRect(full, rect.ref);
    if (cropped == nullptr) {
      throw const MacCaptureException('Could not crop the captured image');
    }
    return _encodeImage(lib, cropped);
  } finally {
    if (cropped != nullptr) lib.imageRelease(cropped);
    if (full != nullptr) lib.imageRelease(full);
    lib.free(scratch);
  }
}

/// Reads the virtual screen dimensions in physical pixels. Runs in an isolate.
({int width, int height}) _virtualScreenSizeSync() {
  final lib = MacCaptureLib.open();
  final scratch = lib.malloc(_scratchBytes);
  if (scratch == nullptr) {
    throw const MacCaptureException('malloc failed for the scratch buffer');
  }
  try {
    final layout = _readLayout(lib, scratch);
    return (
      width: (layout.width * layout.scale).round(),
      height: (layout.height * layout.scale).round(),
    );
  } finally {
    lib.free(scratch);
  }
}

/// Reads the front window's frame, translated to virtual-screen pixels.
///
/// Runs inside an isolate. Returns `null` when nothing usable is in front, or
/// when the front window belongs to this app — the hub is hidden by the time
/// this runs, and framing where it used to be would capture the desktop
/// underneath it (the same guard the X11 and Windows backends apply).
CaptureRegion? _activeWindowRegionSync() {
  final lib = MacCaptureLib.open();
  final scratch = lib.malloc(_scratchBytes);
  if (scratch == nullptr) {
    throw const MacCaptureException('malloc failed for the scratch buffer');
  }

  final windows = lib.windowListCopyWindowInfo(
    kCGWindowListOnScreenOnly | kCGWindowListExcludeDesktopElements,
    kCGNullWindowID,
  );
  if (windows == nullptr) {
    lib.free(scratch);
    return null;
  }

  try {
    final layout = _readLayout(lib, scratch);
    final numberOut = (scratch + _numberOffset).cast<Int32>();
    final count = lib.arrayGetCount(windows);
    // The list is ordered front-to-back, so the first normal-layer window that
    // is not ours is the one the user was looking at.
    for (var i = 0; i < count; i++) {
      final window = lib.arrayGetValueAtIndex(windows, i);
      if (window == nullptr) continue;

      // Layer 0 is the normal window layer; menus, the Dock and the wallpaper
      // live on other layers and must never be framed.
      if (!_readInt(lib, window, lib.windowLayerKey, numberOut) ||
          numberOut.value != 0) {
        continue;
      }
      if (_readInt(lib, window, lib.windowOwnerPidKey, numberOut) &&
          numberOut.value == pid) {
        continue;
      }

      final boundsDict = lib.dictionaryGetValue(window, lib.windowBoundsKey);
      if (boundsDict == nullptr) continue;
      final rect = (scratch + _rectBOffset).cast<CGRect>();
      if (!lib.rectFromDictionary(boundsDict, rect)) continue;

      final region =
          CaptureRegion(
            x: ((rect.ref.origin.x - layout.x) * layout.scale).round(),
            y: ((rect.ref.origin.y - layout.y) * layout.scale).round(),
            width: (rect.ref.size.width * layout.scale).round(),
            height: (rect.ref.size.height * layout.scale).round(),
          ).clampedTo(
            (layout.width * layout.scale).round(),
            (layout.height * layout.scale).round(),
          );
      if (region.width <= 1 || region.height <= 1) continue;
      return region;
    }
    return null;
  } finally {
    lib.release(windows);
    lib.free(scratch);
  }
}

/// Reads an integer value out of a window-info dictionary into [out]. Returns
/// `false` when the key is absent or not a number.
bool _readInt(
  MacCaptureLib lib,
  Pointer<Void> dict,
  Pointer<Void> key,
  Pointer<Int32> out,
) {
  final number = lib.dictionaryGetValue(dict, key);
  if (number == nullptr) return false;
  out.value = 0;
  return lib.numberGetValue(number, kCFNumberIntType, out.cast());
}
