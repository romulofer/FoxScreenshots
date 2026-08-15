import 'dart:ffi';
import 'dart:isolate';
import 'dart:typed_data';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../image/png_codec.dart';
import '../image/raw_pixels.dart';
import 'screen_capture_service.dart';
import 'windows/win32_bindings.dart';

/// Windows capture backend (SPEC §2.1).
///
/// Copies the whole multi-monitor virtual screen with GDI: a `BitBlt` from the
/// desktop device context into a top-down 32-bit DIB section, whose bits are
/// already B, G, R, X on Windows — the same shape the shared [rgbaFromRaw]
/// repacker expects. The grab and the PNG encode run in a background isolate so
/// a 4K frame does not stall the UI, exactly as the X11 backend does.
///
/// `CAPTUREBLT` is ORed into the raster op so layered windows (alpha overlays,
/// drop shadows) appear in the grab instead of leaving holes.
class WindowsScreenCaptureService implements ScreenCaptureService {
  const WindowsScreenCaptureService();

  @override
  Future<CaptureResult> grabFullVirtualScreen() => _grab(null);

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) => _grab(region);

  @override
  Future<CaptureRegion?> activeWindowRegion() async {
    try {
      return await Isolate.run(_activeWindowRegionSync);
    } on Win32Exception catch (e) {
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
    } on Win32Exception catch (e) {
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
    } on Win32Exception catch (e) {
      // Keep GDI details out of the UI layer: one exception type to catch.
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

/// Scratch heap for the out-parameter calls. Laid out so each typed slice is
/// aligned: the header at 0, the `void**` at 40, the RECT at 56, the PID at 72.
const int _scratchBytes = 96;
const int _bitsOffset = 40;
const int _rectOffset = 56;
const int _pidOffset = 72;

/// Grabs [region] (or the whole virtual screen when `null`) and encodes it.
///
/// Runs inside an isolate: it opens its own DLL bindings, releases every GDI
/// object it created, and frees the scratch buffer before returning — nothing
/// Win32-related escapes this function.
({Uint8List png, int width, int height}) _grabSync(CaptureRegion? region) {
  final win = Win32Lib.open();
  final screenDc = win.getDc(0);
  if (screenDc == 0) {
    throw const Win32Exception('GetDC(NULL) returned no screen device context');
  }

  final scratch = win.malloc(_scratchBytes);
  if (scratch == nullptr) {
    throw const Win32Exception('malloc failed for the Win32 scratch buffer');
  }

  var memDc = 0;
  var dib = 0;
  try {
    final originX = win.getSystemMetrics(smXVirtualScreen);
    final originY = win.getSystemMetrics(smYVirtualScreen);
    final fullWidth = win.getSystemMetrics(smCxVirtualScreen);
    final fullHeight = win.getSystemMetrics(smCyVirtualScreen);
    if (fullWidth <= 0 || fullHeight <= 0) {
      throw const Win32Exception('The virtual screen reports no area');
    }

    final area =
        (region ??
                CaptureRegion(
                  x: 0,
                  y: 0,
                  width: fullWidth,
                  height: fullHeight,
                ))
            .clampedTo(fullWidth, fullHeight);
    if (area.isEmpty) {
      throw const Win32Exception('The selected region is outside the screen');
    }

    memDc = win.createCompatibleDc(screenDc);
    if (memDc == 0) {
      throw const Win32Exception('CreateCompatibleDC failed');
    }

    final header = scratch.cast<BitmapInfoHeader>();
    final info = header.ref;
    info.biSize = sizeOf<BitmapInfoHeader>();
    info.biWidth = area.width;
    // Negative height requests a top-down DIB, so row 0 is the top of the
    // screen and the bytes match rgbaFromRaw's row order without a flip.
    info.biHeight = -area.height;
    info.biPlanes = 1;
    info.biBitCount = 32;
    info.biCompression = biRgb;
    info.biSizeImage = 0;
    info.biXPelsPerMeter = 0;
    info.biYPelsPerMeter = 0;
    info.biClrUsed = 0;
    info.biClrImportant = 0;

    final bitsOut = (scratch + _bitsOffset).cast<Pointer<Uint8>>();
    bitsOut.value = nullptr;
    dib = win.createDibSection(memDc, header, dibRgbColors, bitsOut, 0, 0);
    final bits = bitsOut.value;
    if (dib == 0 || bits == nullptr) {
      throw const Win32Exception('CreateDIBSection returned no pixel buffer');
    }

    win.selectObject(memDc, dib);
    final copied = win.bitBlt(
      memDc,
      0,
      0,
      area.width,
      area.height,
      screenDc,
      originX + area.x,
      originY + area.y,
      srcCopy | captureBlt,
    );
    if (copied == 0) {
      throw const Win32Exception('BitBlt failed to copy the screen');
    }

    // 32-bit DIB rows are already DWORD-aligned, so the stride is exactly
    // width*4; the source bytes are B, G, R, X on Windows.
    final rgba = rgbaFromRaw(
      source: bits.asTypedList(area.width * area.height * 4),
      width: area.width,
      height: area.height,
      bytesPerLine: area.width * 4,
      bitsPerPixel: 32,
      order: RawPixelOrder.bgra,
    );
    return (
      png: encodeRgbaSync(rgba, area.width, area.height, PngCodec.fastLevel),
      width: area.width,
      height: area.height,
    );
  } finally {
    if (dib != 0) win.deleteObject(dib);
    if (memDc != 0) win.deleteDc(memDc);
    win.free(scratch);
    win.releaseDc(0, screenDc);
  }
}

/// Reads the virtual screen dimensions in physical pixels. Runs in an isolate.
({int width, int height}) _virtualScreenSizeSync() {
  final win = Win32Lib.open();
  final width = win.getSystemMetrics(smCxVirtualScreen);
  final height = win.getSystemMetrics(smCyVirtualScreen);
  if (width <= 0 || height <= 0) {
    throw const Win32Exception('The virtual screen reports no area');
  }
  return (width: width, height: height);
}

/// Reads the foreground window's frame, translated to virtual-screen origin.
///
/// Runs inside an isolate. Returns `null` when nothing usable is focused, or
/// when the focused window belongs to this app — the hub is hidden by the time
/// this runs, and framing where it used to be would capture the desktop
/// underneath it (the same guard the X11 backend applies).
CaptureRegion? _activeWindowRegionSync() {
  final win = Win32Lib.open();
  final scratch = win.malloc(_scratchBytes);
  if (scratch == nullptr) {
    throw const Win32Exception('malloc failed for the Win32 scratch buffer');
  }
  try {
    final hWnd = win.getForegroundWindow();
    if (hWnd == 0) return null;

    final pidOut = (scratch + _pidOffset).cast<Uint32>();
    pidOut.value = 0;
    win.getWindowThreadProcessId(hWnd, pidOut);
    if (pidOut.value == win.getCurrentProcessId()) return null;

    final rect = (scratch + _rectOffset).cast<Rect>();
    if (!win.windowFrame(hWnd, rect)) return null;
    final frame = rect.ref;

    final originX = win.getSystemMetrics(smXVirtualScreen);
    final originY = win.getSystemMetrics(smYVirtualScreen);
    final region = CaptureRegion(
      x: frame.left - originX,
      y: frame.top - originY,
      width: frame.right - frame.left,
      height: frame.bottom - frame.top,
    ).clampedTo(
      win.getSystemMetrics(smCxVirtualScreen),
      win.getSystemMetrics(smCyVirtualScreen),
    );
    // A one-pixel frame is a hidden or collapsed window, not a capture target.
    if (region.width <= 1 || region.height <= 1) return null;
    return region;
  } finally {
    win.free(scratch);
  }
}
