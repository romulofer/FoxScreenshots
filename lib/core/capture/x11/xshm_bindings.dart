// Minimal `dart:ffi` bindings for the MIT-SHM extension (`libXext.so.6`) plus
// the handful of libc `shm*` calls it needs, so a local X11 grab can skip one
// full-frame copy through the core protocol connection.
//
// Optional by design: `libXext` ships on essentially every X11 desktop, but
// [X11ShmLib.open] and every call site around it is expected to fail cleanly
// (an exception, not a crash) when the library, the extension, or the shared
// memory itself is unavailable — a remote/network display in particular has
// no MIT-SHM support at all. Callers must treat any failure here as "fall
// back to `XGetImage`", never as fatal.

import 'dart:ffi';

import 'x11_bindings.dart';

/// Layout of Xlib's `XShmSegmentInfo`. Field order and types mirror
/// `X11/extensions/XShm.h` exactly, same as [XImage] in `x11_bindings.dart`.
final class XShmSegmentInfo extends Struct {
  @UnsignedLong()
  external int shmseg;
  @Int32()
  external int shmid;
  external Pointer<Uint8> shmaddr;
  @Int32()
  external int readOnly;
}

typedef _XShmQueryExtensionNative = Int32 Function(Pointer<Void>);
typedef XShmQueryExtensionDart = int Function(Pointer<Void>);
typedef _XShmAttachNative = Int32 Function(
  Pointer<Void>,
  Pointer<XShmSegmentInfo>,
);
typedef XShmAttachDart = int Function(Pointer<Void>, Pointer<XShmSegmentInfo>);
typedef _XShmDetachNative = Int32 Function(
  Pointer<Void>,
  Pointer<XShmSegmentInfo>,
);
typedef XShmDetachDart = int Function(Pointer<Void>, Pointer<XShmSegmentInfo>);
typedef _XShmCreateImageNative = Pointer<XImage> Function(
  Pointer<Void> display,
  Pointer<Void> visual,
  UnsignedInt depth,
  Int32 format,
  Pointer<Uint8> data,
  Pointer<XShmSegmentInfo> shminfo,
  UnsignedInt width,
  UnsignedInt height,
);
typedef XShmCreateImageDart = Pointer<XImage> Function(
  Pointer<Void> display,
  Pointer<Void> visual,
  int depth,
  int format,
  Pointer<Uint8> data,
  Pointer<XShmSegmentInfo> shminfo,
  int width,
  int height,
);
typedef _XShmGetImageNative = Int32 Function(
  Pointer<Void> display,
  UnsignedLong drawable,
  Pointer<XImage> image,
  Int32 x,
  Int32 y,
  UnsignedLong planeMask,
);
typedef XShmGetImageDart = int Function(
  Pointer<Void> display,
  int drawable,
  Pointer<XImage> image,
  int x,
  int y,
  int planeMask,
);

typedef _ShmgetNative = Int32 Function(Int32 key, IntPtr size, Int32 shmflg);
typedef ShmgetDart = int Function(int key, int size, int shmflg);
typedef _ShmatNative = Pointer<Uint8> Function(
  Int32 shmid,
  Pointer<Void> shmaddr,
  Int32 shmflg,
);
typedef ShmatDart = Pointer<Uint8> Function(
  int shmid,
  Pointer<Void> shmaddr,
  int shmflg,
);
typedef _ShmdtNative = Int32 Function(Pointer<Uint8> shmaddr);
typedef ShmdtDart = int Function(Pointer<Uint8> shmaddr);
typedef _ShmctlNative = Int32 Function(
  Int32 shmid,
  Int32 cmd,
  Pointer<Void> buf,
);
typedef ShmctlDart = int Function(int shmid, int cmd, Pointer<Void> buf);

/// `IPC_CREAT` (`sys/ipc.h`) — create the segment if [ShmgetDart.key] is
/// `IPC_PRIVATE`. OR'd with the owner-only permission bits.
const int ipcCreat = 0x200;

/// `IPC_PRIVATE` — always allocate a brand new segment.
const int ipcPrivate = 0;

/// `IPC_RMID` (`shmctl` command) — mark the segment for removal once every
/// attached process (us and the X server) detaches. Issued right after
/// `shmat` succeeds, so a crash before the explicit detach at the end of a
/// grab never leaks the segment.
const int ipcRmid = 0;

/// `libXext.so.6` plus the MIT-SHM symbols, and the libc `shm*` calls that
/// manage the segment itself.
///
/// Load one per isolate, same rule as [X11Lib]: nothing here is shareable
/// across isolates.
class X11ShmLib {
  X11ShmLib._(DynamicLibrary lib)
    : queryExtension = lib
          .lookup<NativeFunction<_XShmQueryExtensionNative>>(
            'XShmQueryExtension',
          )
          .asFunction<XShmQueryExtensionDart>(),
      attach = lib
          .lookup<NativeFunction<_XShmAttachNative>>('XShmAttach')
          .asFunction<XShmAttachDart>(),
      detach = lib
          .lookup<NativeFunction<_XShmDetachNative>>('XShmDetach')
          .asFunction<XShmDetachDart>(),
      createImage = lib
          .lookup<NativeFunction<_XShmCreateImageNative>>('XShmCreateImage')
          .asFunction<XShmCreateImageDart>(),
      getImage = lib
          .lookup<NativeFunction<_XShmGetImageNative>>('XShmGetImage')
          .asFunction<XShmGetImageDart>(),
      shmget = DynamicLibrary.process()
          .lookup<NativeFunction<_ShmgetNative>>('shmget')
          .asFunction<ShmgetDart>(),
      shmat = DynamicLibrary.process()
          .lookup<NativeFunction<_ShmatNative>>('shmat')
          .asFunction<ShmatDart>(),
      shmdt = DynamicLibrary.process()
          .lookup<NativeFunction<_ShmdtNative>>('shmdt')
          .asFunction<ShmdtDart>(),
      shmctl = DynamicLibrary.process()
          .lookup<NativeFunction<_ShmctlNative>>('shmctl')
          .asFunction<ShmctlDart>();

  /// Opens `libXext.so.6`. Throws on any failure — missing library, or a
  /// libc missing the (universally present, but never assume) `shm*` calls.
  factory X11ShmLib.open() {
    try {
      return X11ShmLib._(DynamicLibrary.open('libXext.so.6'));
    } on Object catch (e) {
      throw X11Exception('libXext.so.6 could not be loaded: $e');
    }
  }

  final XShmQueryExtensionDart queryExtension;
  final XShmAttachDart attach;
  final XShmDetachDart detach;
  final XShmCreateImageDart createImage;
  final XShmGetImageDart getImage;
  final ShmgetDart shmget;
  final ShmatDart shmat;
  final ShmdtDart shmdt;
  final ShmctlDart shmctl;
}
