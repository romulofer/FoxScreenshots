// Minimal `dart:ffi` bindings for the Win32 calls the capture backend needs.
//
// Only the handful of GDI/USER32 symbols used to copy the virtual screen and
// read the foreground window are bound; there is no code generation and no
// bundled binary — `user32.dll`, `gdi32.dll`, `kernel32.dll` and `msvcrt.dll`
// ship with every Windows install (SPEC §7: no bundled binaries).

import 'dart:ffi';

/// `GetSystemMetrics` indices for the multi-monitor virtual screen. The origin
/// can be negative when a monitor sits to the left of, or above, the primary.
const int smXVirtualScreen = 76;
const int smYVirtualScreen = 77;
const int smCxVirtualScreen = 78;
const int smCyVirtualScreen = 79;

/// `BITMAPINFOHEADER.biCompression` — uncompressed, and the `CreateDIBSection`
/// colour-usage flag for a literal RGB layout (no palette).
const int biRgb = 0;
const int dibRgbColors = 0;

/// `BitBlt` raster op: plain copy, ORed with `CAPTUREBLT` so layered windows
/// (drop shadows, alpha-blended overlays) land in the grab too.
const int srcCopy = 0x00CC0020;
const int captureBlt = 0x40000000;

/// `DwmGetWindowAttribute` selector for the true frame bounds, which exclude the
/// invisible resize border and drop shadow that `GetWindowRect` includes.
const int dwmwaExtendedFrameBounds = 9;

/// Win32 `RECT` — four `LONG`s, left/top/right/bottom.
final class Rect extends Struct {
  @Int32()
  external int left;
  @Int32()
  external int top;
  @Int32()
  external int right;
  @Int32()
  external int bottom;
}

/// Win32 `BITMAPINFOHEADER`. Field order and widths mirror `wingdi.h` exactly —
/// the FFI derives the C ABI offsets from the declarations, so do not reorder.
final class BitmapInfoHeader extends Struct {
  @Uint32()
  external int biSize;
  @Int32()
  external int biWidth;
  @Int32()
  external int biHeight;
  @Uint16()
  external int biPlanes;
  @Uint16()
  external int biBitCount;
  @Uint32()
  external int biCompression;
  @Uint32()
  external int biSizeImage;
  @Int32()
  external int biXPelsPerMeter;
  @Int32()
  external int biYPelsPerMeter;
  @Uint32()
  external int biClrUsed;
  @Uint32()
  external int biClrImportant;
}

typedef _GetDcNative = IntPtr Function(IntPtr hWnd);
typedef GetDcDart = int Function(int hWnd);
typedef _ReleaseDcNative = Int32 Function(IntPtr hWnd, IntPtr hDc);
typedef ReleaseDcDart = int Function(int hWnd, int hDc);
typedef _GetSystemMetricsNative = Int32 Function(Int32 index);
typedef GetSystemMetricsDart = int Function(int index);
typedef _GetForegroundWindowNative = IntPtr Function();
typedef GetForegroundWindowDart = int Function();
typedef _GetWindowRectNative = Int32 Function(IntPtr hWnd, Pointer<Rect> rect);
typedef GetWindowRectDart = int Function(int hWnd, Pointer<Rect> rect);
typedef _GetWindowThreadProcessIdNative = Uint32 Function(
  IntPtr hWnd,
  Pointer<Uint32> processId,
);
typedef GetWindowThreadProcessIdDart = int Function(
  int hWnd,
  Pointer<Uint32> processId,
);
typedef _GetCurrentProcessIdNative = Uint32 Function();
typedef GetCurrentProcessIdDart = int Function();
typedef _CreateCompatibleDcNative = IntPtr Function(IntPtr hDc);
typedef CreateCompatibleDcDart = int Function(int hDc);
typedef _CreateDibSectionNative = IntPtr Function(
  IntPtr hDc,
  Pointer<BitmapInfoHeader> info,
  Uint32 usage,
  Pointer<Pointer<Uint8>> bits,
  IntPtr section,
  Uint32 offset,
);
typedef CreateDibSectionDart = int Function(
  int hDc,
  Pointer<BitmapInfoHeader> info,
  int usage,
  Pointer<Pointer<Uint8>> bits,
  int section,
  int offset,
);
typedef _SelectObjectNative = IntPtr Function(IntPtr hDc, IntPtr object);
typedef SelectObjectDart = int Function(int hDc, int object);
typedef _BitBltNative = Int32 Function(
  IntPtr dst,
  Int32 x,
  Int32 y,
  Int32 width,
  Int32 height,
  IntPtr src,
  Int32 srcX,
  Int32 srcY,
  Uint32 rop,
);
typedef BitBltDart = int Function(
  int dst,
  int x,
  int y,
  int width,
  int height,
  int src,
  int srcX,
  int srcY,
  int rop,
);
typedef _DeleteObjectNative = Int32 Function(IntPtr object);
typedef DeleteObjectDart = int Function(int object);
typedef _DeleteDcNative = Int32 Function(IntPtr hDc);
typedef DeleteDcDart = int Function(int hDc);
typedef _DwmGetWindowAttributeNative = Int32 Function(
  IntPtr hWnd,
  Uint32 attribute,
  Pointer<Void> value,
  Uint32 size,
);
typedef DwmGetWindowAttributeDart = int Function(
  int hWnd,
  int attribute,
  Pointer<Void> value,
  int size,
);
typedef _MallocNative = Pointer<Uint8> Function(IntPtr size);
typedef MallocDart = Pointer<Uint8> Function(int size);
typedef _FreeNative = Void Function(Pointer<Uint8> ptr);
typedef FreeDart = void Function(Pointer<Uint8> ptr);

/// Thrown when a Win32 call fails. Carries a human-readable reason for the UI
/// (never any screen content).
class Win32Exception implements Exception {
  const Win32Exception(this.message);

  final String message;

  @override
  String toString() => 'Win32Exception: $message';
}

/// The loaded Win32 DLLs plus the bound symbols.
///
/// Load one per isolate: device-context and window handles are process-wide, but
/// keeping a fresh binding per isolate matches the X11 backend and avoids
/// sharing FFI function pointers across isolate boundaries.
class Win32Lib {
  Win32Lib._(
    DynamicLibrary user32,
    DynamicLibrary gdi32,
    DynamicLibrary kernel32,
    DynamicLibrary crt,
    DynamicLibrary? dwmapi,
  ) : getDc = user32
          .lookup<NativeFunction<_GetDcNative>>('GetDC')
          .asFunction<GetDcDart>(),
      releaseDc = user32
          .lookup<NativeFunction<_ReleaseDcNative>>('ReleaseDC')
          .asFunction<ReleaseDcDart>(),
      getSystemMetrics = user32
          .lookup<NativeFunction<_GetSystemMetricsNative>>('GetSystemMetrics')
          .asFunction<GetSystemMetricsDart>(),
      getForegroundWindow = user32
          .lookup<NativeFunction<_GetForegroundWindowNative>>(
            'GetForegroundWindow',
          )
          .asFunction<GetForegroundWindowDart>(),
      _getWindowRect = user32
          .lookup<NativeFunction<_GetWindowRectNative>>('GetWindowRect')
          .asFunction<GetWindowRectDart>(),
      getWindowThreadProcessId = user32
          .lookup<NativeFunction<_GetWindowThreadProcessIdNative>>(
            'GetWindowThreadProcessId',
          )
          .asFunction<GetWindowThreadProcessIdDart>(),
      getCurrentProcessId = kernel32
          .lookup<NativeFunction<_GetCurrentProcessIdNative>>(
            'GetCurrentProcessId',
          )
          .asFunction<GetCurrentProcessIdDart>(),
      createCompatibleDc = gdi32
          .lookup<NativeFunction<_CreateCompatibleDcNative>>(
            'CreateCompatibleDC',
          )
          .asFunction<CreateCompatibleDcDart>(),
      createDibSection = gdi32
          .lookup<NativeFunction<_CreateDibSectionNative>>('CreateDIBSection')
          .asFunction<CreateDibSectionDart>(),
      selectObject = gdi32
          .lookup<NativeFunction<_SelectObjectNative>>('SelectObject')
          .asFunction<SelectObjectDart>(),
      bitBlt = gdi32
          .lookup<NativeFunction<_BitBltNative>>('BitBlt')
          .asFunction<BitBltDart>(),
      deleteObject = gdi32
          .lookup<NativeFunction<_DeleteObjectNative>>('DeleteObject')
          .asFunction<DeleteObjectDart>(),
      deleteDc = gdi32
          .lookup<NativeFunction<_DeleteDcNative>>('DeleteDC')
          .asFunction<DeleteDcDart>(),
      malloc = crt
          .lookup<NativeFunction<_MallocNative>>('malloc')
          .asFunction<MallocDart>(),
      free = crt
          .lookup<NativeFunction<_FreeNative>>('free')
          .asFunction<FreeDart>(),
      _dwmGetWindowAttribute = dwmapi
          ?.lookup<NativeFunction<_DwmGetWindowAttributeNative>>(
            'DwmGetWindowAttribute',
          )
          .asFunction<DwmGetWindowAttributeDart>();

  /// Loads the system DLLs. Throws [Win32Exception] when one is missing, which
  /// on a supported Windows install should never happen except for `dwmapi.dll`
  /// — that one is optional and its absence just falls back to `GetWindowRect`.
  factory Win32Lib.open() {
    try {
      DynamicLibrary? dwmapi;
      try {
        dwmapi = DynamicLibrary.open('dwmapi.dll');
      } on Object {
        dwmapi = null;
      }
      return Win32Lib._(
        DynamicLibrary.open('user32.dll'),
        DynamicLibrary.open('gdi32.dll'),
        DynamicLibrary.open('kernel32.dll'),
        DynamicLibrary.open('msvcrt.dll'),
        dwmapi,
      );
    } on Object catch (e) {
      throw Win32Exception('A required Windows library failed to load: $e');
    }
  }

  final GetDcDart getDc;
  final ReleaseDcDart releaseDc;
  final GetSystemMetricsDart getSystemMetrics;
  final GetForegroundWindowDart getForegroundWindow;
  final GetWindowRectDart _getWindowRect;
  final GetWindowThreadProcessIdDart getWindowThreadProcessId;
  final GetCurrentProcessIdDart getCurrentProcessId;
  final CreateCompatibleDcDart createCompatibleDc;
  final CreateDibSectionDart createDibSection;
  final SelectObjectDart selectObject;
  final BitBltDart bitBlt;
  final DeleteObjectDart deleteObject;
  final DeleteDcDart deleteDc;
  final MallocDart malloc;
  final FreeDart free;
  final DwmGetWindowAttributeDart? _dwmGetWindowAttribute;

  /// Fills [out] with [hWnd]'s frame in screen coordinates. Prefers the DWM
  /// extended bounds (the visible frame); falls back to `GetWindowRect`, which
  /// on Windows 10+ is a few pixels larger because of the invisible border.
  /// Returns `false` when neither call succeeds.
  bool windowFrame(int hWnd, Pointer<Rect> out) {
    final dwm = _dwmGetWindowAttribute;
    if (dwm != null) {
      final hr = dwm(hWnd, dwmwaExtendedFrameBounds, out.cast(), sizeOf<Rect>());
      if (hr == 0) return true;
    }
    return _getWindowRect(hWnd, out) != 0;
  }
}
