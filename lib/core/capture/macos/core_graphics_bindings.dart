// Minimal `dart:ffi` bindings for the CoreGraphics/CoreFoundation calls the
// macOS capture backend needs.
//
// Only the handful of symbols used to copy the screen, read display geometry and
// find the front window are bound; there is no code generation and no bundled
// binary — both frameworks ship with every macOS install (SPEC §7).
//
// `CGWindowListCreateImage` is deprecated since macOS 14 in favour of
// ScreenCaptureKit, but it still resolves and works (with the Screen Recording
// permission) through at least macOS 15, and it is the only synchronous,
// FFI-friendly path. Migrating to ScreenCaptureKit would mean a Swift/Obj-C
// method channel, which the rest of the app deliberately avoids.

import 'dart:ffi';

/// `CGWindowListOption`: only windows currently on screen, minus the desktop
/// wallpaper and Dock/menu-bar backing elements. Ordered front-to-back.
const int kCGWindowListOnScreenOnly = 1;
const int kCGWindowListExcludeDesktopElements = 16;

/// `kCGNullWindowID` — "no relative window" for the list/image calls.
const int kCGNullWindowID = 0;

/// `kCGWindowImageDefault` — native resolution, includes window shadows.
const int kCGWindowImageDefault = 0;

/// `kCFNumberIntType` — the `CFNumberGetValue` selector for a C `int`.
const int kCFNumberIntType = 9;

/// A CoreGraphics `CGPoint`/`CGSize`/`CGRect` — `CGFloat` is a 64-bit `double`
/// on every Mac the app targets. Laid out to match the C ABI so the values can
/// be passed and returned by value.
final class CGPoint extends Struct {
  @Double()
  external double x;
  @Double()
  external double y;
}

final class CGSize extends Struct {
  @Double()
  external double width;
  @Double()
  external double height;
}

final class CGRect extends Struct {
  external CGPoint origin;
  external CGSize size;
}

typedef _CGMainDisplayIDNative = Uint32 Function();
typedef CGMainDisplayIDDart = int Function();
typedef _CGGetActiveDisplayListNative = Int32 Function(
  Uint32 maxDisplays,
  Pointer<Uint32> displays,
  Pointer<Uint32> count,
);
typedef CGGetActiveDisplayListDart = int Function(
  int maxDisplays,
  Pointer<Uint32> displays,
  Pointer<Uint32> count,
);
typedef _CGDisplayBoundsNative = CGRect Function(Uint32 display);
typedef CGDisplayBoundsDart = CGRect Function(int display);
typedef _CGDisplayCopyDisplayModeNative = Pointer<Void> Function(
  Uint32 display,
);
typedef CGDisplayCopyDisplayModeDart = Pointer<Void> Function(int display);
typedef _CGDisplayModeGetSizeNative = IntPtr Function(Pointer<Void> mode);
typedef CGDisplayModeGetSizeDart = int Function(Pointer<Void> mode);
typedef _CGDisplayModeReleaseNative = Void Function(Pointer<Void> mode);
typedef CGDisplayModeReleaseDart = void Function(Pointer<Void> mode);
typedef _CGWindowListCreateImageNative = Pointer<Void> Function(
  CGRect bounds,
  Uint32 listOption,
  Uint32 windowId,
  Uint32 imageOption,
);
typedef CGWindowListCreateImageDart = Pointer<Void> Function(
  CGRect bounds,
  int listOption,
  int windowId,
  int imageOption,
);
typedef _CGImageGetSizeNative = IntPtr Function(Pointer<Void> image);
typedef CGImageGetSizeDart = int Function(Pointer<Void> image);
typedef _CGImageGetDataProviderNative = Pointer<Void> Function(
  Pointer<Void> image,
);
typedef CGImageGetDataProviderDart = Pointer<Void> Function(
  Pointer<Void> image,
);
typedef _CGImageCreateWithImageInRectNative = Pointer<Void> Function(
  Pointer<Void> image,
  CGRect rect,
);
typedef CGImageCreateWithImageInRectDart = Pointer<Void> Function(
  Pointer<Void> image,
  CGRect rect,
);
typedef _CGImageReleaseNative = Void Function(Pointer<Void> image);
typedef CGImageReleaseDart = void Function(Pointer<Void> image);
typedef _CGDataProviderCopyDataNative = Pointer<Void> Function(
  Pointer<Void> provider,
);
typedef CGDataProviderCopyDataDart = Pointer<Void> Function(
  Pointer<Void> provider,
);
typedef _CGWindowListCopyWindowInfoNative = Pointer<Void> Function(
  Uint32 option,
  Uint32 windowId,
);
typedef CGWindowListCopyWindowInfoDart = Pointer<Void> Function(
  int option,
  int windowId,
);
typedef _CGRectMakeWithDictionaryRepresentationNative = Bool Function(
  Pointer<Void> dict,
  Pointer<CGRect> rect,
);
typedef CGRectMakeWithDictionaryRepresentationDart = bool Function(
  Pointer<Void> dict,
  Pointer<CGRect> rect,
);
typedef _CGScreenCaptureAccessNative = Bool Function();
typedef CGScreenCaptureAccessDart = bool Function();

typedef _CFReleaseNative = Void Function(Pointer<Void> ref);
typedef CFReleaseDart = void Function(Pointer<Void> ref);
typedef _CFDataGetBytePtrNative = Pointer<Uint8> Function(Pointer<Void> data);
typedef CFDataGetBytePtrDart = Pointer<Uint8> Function(Pointer<Void> data);
typedef _CFDataGetLengthNative = IntPtr Function(Pointer<Void> data);
typedef CFDataGetLengthDart = int Function(Pointer<Void> data);
typedef _CFArrayGetCountNative = IntPtr Function(Pointer<Void> array);
typedef CFArrayGetCountDart = int Function(Pointer<Void> array);
typedef _CFArrayGetValueAtIndexNative = Pointer<Void> Function(
  Pointer<Void> array,
  IntPtr index,
);
typedef CFArrayGetValueAtIndexDart = Pointer<Void> Function(
  Pointer<Void> array,
  int index,
);
typedef _CFDictionaryGetValueNative = Pointer<Void> Function(
  Pointer<Void> dict,
  Pointer<Void> key,
);
typedef CFDictionaryGetValueDart = Pointer<Void> Function(
  Pointer<Void> dict,
  Pointer<Void> key,
);
typedef _CFNumberGetValueNative = Bool Function(
  Pointer<Void> number,
  IntPtr type,
  Pointer<Void> out,
);
typedef CFNumberGetValueDart = bool Function(
  Pointer<Void> number,
  int type,
  Pointer<Void> out,
);
typedef _MallocNative = Pointer<Uint8> Function(IntPtr size);
typedef MallocDart = Pointer<Uint8> Function(int size);
typedef _FreeNative = Void Function(Pointer<Uint8> ptr);
typedef FreeDart = void Function(Pointer<Uint8> ptr);

/// Thrown when a CoreGraphics call fails. Carries a human-readable reason for
/// the UI (never any screen content).
class MacCaptureException implements Exception {
  const MacCaptureException(this.message, {this.deniedPermission = false});

  final String message;

  /// True when the failure is a missing Screen Recording grant, so the backend
  /// can surface the dedicated "grant permission" message rather than a generic
  /// display error.
  final bool deniedPermission;

  @override
  String toString() => 'MacCaptureException: $message';
}

/// The loaded CoreGraphics/CoreFoundation frameworks plus the bound symbols.
///
/// Load one per isolate: CoreFoundation objects are reference-counted and must
/// be released on the thread that created them, and a fresh binding per isolate
/// matches the X11 and Windows backends.
class MacCaptureLib {
  MacCaptureLib._(DynamicLibrary cg, DynamicLibrary cf, DynamicLibrary libc)
    : mainDisplayId = cg
          .lookup<NativeFunction<_CGMainDisplayIDNative>>('CGMainDisplayID')
          .asFunction<CGMainDisplayIDDart>(),
      getActiveDisplayList = cg
          .lookup<NativeFunction<_CGGetActiveDisplayListNative>>(
            'CGGetActiveDisplayList',
          )
          .asFunction<CGGetActiveDisplayListDart>(),
      displayBounds = cg
          .lookup<NativeFunction<_CGDisplayBoundsNative>>('CGDisplayBounds')
          .asFunction<CGDisplayBoundsDart>(),
      copyDisplayMode = cg
          .lookup<NativeFunction<_CGDisplayCopyDisplayModeNative>>(
            'CGDisplayCopyDisplayMode',
          )
          .asFunction<CGDisplayCopyDisplayModeDart>(),
      modeGetPixelWidth = cg
          .lookup<NativeFunction<_CGDisplayModeGetSizeNative>>(
            'CGDisplayModeGetPixelWidth',
          )
          .asFunction<CGDisplayModeGetSizeDart>(),
      modeGetWidth = cg
          .lookup<NativeFunction<_CGDisplayModeGetSizeNative>>(
            'CGDisplayModeGetWidth',
          )
          .asFunction<CGDisplayModeGetSizeDart>(),
      modeRelease = cg
          .lookup<NativeFunction<_CGDisplayModeReleaseNative>>(
            'CGDisplayModeRelease',
          )
          .asFunction<CGDisplayModeReleaseDart>(),
      windowListCreateImage = cg
          .lookup<NativeFunction<_CGWindowListCreateImageNative>>(
            'CGWindowListCreateImage',
          )
          .asFunction<CGWindowListCreateImageDart>(),
      imageGetWidth = cg
          .lookup<NativeFunction<_CGImageGetSizeNative>>('CGImageGetWidth')
          .asFunction<CGImageGetSizeDart>(),
      imageGetHeight = cg
          .lookup<NativeFunction<_CGImageGetSizeNative>>('CGImageGetHeight')
          .asFunction<CGImageGetSizeDart>(),
      imageGetBytesPerRow = cg
          .lookup<NativeFunction<_CGImageGetSizeNative>>(
            'CGImageGetBytesPerRow',
          )
          .asFunction<CGImageGetSizeDart>(),
      imageGetBitsPerPixel = cg
          .lookup<NativeFunction<_CGImageGetSizeNative>>(
            'CGImageGetBitsPerPixel',
          )
          .asFunction<CGImageGetSizeDart>(),
      imageGetDataProvider = cg
          .lookup<NativeFunction<_CGImageGetDataProviderNative>>(
            'CGImageGetDataProvider',
          )
          .asFunction<CGImageGetDataProviderDart>(),
      imageCreateWithImageInRect = cg
          .lookup<NativeFunction<_CGImageCreateWithImageInRectNative>>(
            'CGImageCreateWithImageInRect',
          )
          .asFunction<CGImageCreateWithImageInRectDart>(),
      imageRelease = cg
          .lookup<NativeFunction<_CGImageReleaseNative>>('CGImageRelease')
          .asFunction<CGImageReleaseDart>(),
      dataProviderCopyData = cg
          .lookup<NativeFunction<_CGDataProviderCopyDataNative>>(
            'CGDataProviderCopyData',
          )
          .asFunction<CGDataProviderCopyDataDart>(),
      windowListCopyWindowInfo = cg
          .lookup<NativeFunction<_CGWindowListCopyWindowInfoNative>>(
            'CGWindowListCopyWindowInfo',
          )
          .asFunction<CGWindowListCopyWindowInfoDart>(),
      rectFromDictionary = cg
          .lookup<
            NativeFunction<_CGRectMakeWithDictionaryRepresentationNative>
          >('CGRectMakeWithDictionaryRepresentation')
          .asFunction<CGRectMakeWithDictionaryRepresentationDart>(),
      preflightScreenCaptureAccess = cg
          .lookup<NativeFunction<_CGScreenCaptureAccessNative>>(
            'CGPreflightScreenCaptureAccess',
          )
          .asFunction<CGScreenCaptureAccessDart>(),
      requestScreenCaptureAccess = cg
          .lookup<NativeFunction<_CGScreenCaptureAccessNative>>(
            'CGRequestScreenCaptureAccess',
          )
          .asFunction<CGScreenCaptureAccessDart>(),
      windowBoundsKey = cg.lookup<Pointer<Void>>('kCGWindowBounds').value,
      windowLayerKey = cg.lookup<Pointer<Void>>('kCGWindowLayer').value,
      windowOwnerPidKey = cg.lookup<Pointer<Void>>('kCGWindowOwnerPID').value,
      release = cf
          .lookup<NativeFunction<_CFReleaseNative>>('CFRelease')
          .asFunction<CFReleaseDart>(),
      dataGetBytePtr = cf
          .lookup<NativeFunction<_CFDataGetBytePtrNative>>('CFDataGetBytePtr')
          .asFunction<CFDataGetBytePtrDart>(),
      dataGetLength = cf
          .lookup<NativeFunction<_CFDataGetLengthNative>>('CFDataGetLength')
          .asFunction<CFDataGetLengthDart>(),
      arrayGetCount = cf
          .lookup<NativeFunction<_CFArrayGetCountNative>>('CFArrayGetCount')
          .asFunction<CFArrayGetCountDart>(),
      arrayGetValueAtIndex = cf
          .lookup<NativeFunction<_CFArrayGetValueAtIndexNative>>(
            'CFArrayGetValueAtIndex',
          )
          .asFunction<CFArrayGetValueAtIndexDart>(),
      dictionaryGetValue = cf
          .lookup<NativeFunction<_CFDictionaryGetValueNative>>(
            'CFDictionaryGetValue',
          )
          .asFunction<CFDictionaryGetValueDart>(),
      numberGetValue = cf
          .lookup<NativeFunction<_CFNumberGetValueNative>>('CFNumberGetValue')
          .asFunction<CFNumberGetValueDart>(),
      malloc = libc
          .lookup<NativeFunction<_MallocNative>>('malloc')
          .asFunction<MallocDart>(),
      free = libc
          .lookup<NativeFunction<_FreeNative>>('free')
          .asFunction<FreeDart>();

  /// Opens both system frameworks and libc. Throws [MacCaptureException] when
  /// one is missing, which on a real Mac should never happen.
  factory MacCaptureLib.open() {
    try {
      return MacCaptureLib._(
        DynamicLibrary.open(
          '/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics',
        ),
        DynamicLibrary.open(
          '/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation',
        ),
        DynamicLibrary.process(),
      );
    } on Object catch (e) {
      throw MacCaptureException(
        'A required macOS framework failed to load: $e',
      );
    }
  }

  final CGMainDisplayIDDart mainDisplayId;
  final CGGetActiveDisplayListDart getActiveDisplayList;
  final CGDisplayBoundsDart displayBounds;
  final CGDisplayCopyDisplayModeDart copyDisplayMode;
  final CGDisplayModeGetSizeDart modeGetPixelWidth;
  final CGDisplayModeGetSizeDart modeGetWidth;
  final CGDisplayModeReleaseDart modeRelease;
  final CGWindowListCreateImageDart windowListCreateImage;
  final CGImageGetSizeDart imageGetWidth;
  final CGImageGetSizeDart imageGetHeight;
  final CGImageGetSizeDart imageGetBytesPerRow;
  final CGImageGetSizeDart imageGetBitsPerPixel;
  final CGImageGetDataProviderDart imageGetDataProvider;
  final CGImageCreateWithImageInRectDart imageCreateWithImageInRect;
  final CGImageReleaseDart imageRelease;
  final CGDataProviderCopyDataDart dataProviderCopyData;
  final CGWindowListCopyWindowInfoDart windowListCopyWindowInfo;
  final CGRectMakeWithDictionaryRepresentationDart rectFromDictionary;

  /// `true` when the app already holds the Screen Recording grant.
  final CGScreenCaptureAccessDart preflightScreenCaptureAccess;

  /// Prompts for the Screen Recording grant (opens System Settings the first
  /// time). Returns `true` only if already granted; the prompt result arrives
  /// on a later launch, so a first denial still throws.
  final CGScreenCaptureAccessDart requestScreenCaptureAccess;

  /// `CFStringRef` keys into the window-info dictionaries.
  final Pointer<Void> windowBoundsKey;
  final Pointer<Void> windowLayerKey;
  final Pointer<Void> windowOwnerPidKey;

  final CFReleaseDart release;
  final CFDataGetBytePtrDart dataGetBytePtr;
  final CFDataGetLengthDart dataGetLength;
  final CFArrayGetCountDart arrayGetCount;
  final CFArrayGetValueAtIndexDart arrayGetValueAtIndex;
  final CFDictionaryGetValueDart dictionaryGetValue;
  final CFNumberGetValueDart numberGetValue;
  final MallocDart malloc;
  final FreeDart free;
}
