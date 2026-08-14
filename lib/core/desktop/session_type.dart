import 'dart:io';

/// Which display server the app is talking to.
///
/// Nearly everything platform-specific hangs off this: how pixels are grabbed,
/// whether the app may place its own overlay window, and whether global hotkeys
/// can be registered at all. Read once from the environment so every subsystem
/// agrees on the answer.
enum DesktopSession {
  /// X11 (or XWayland with a real X root window).
  x11,

  /// Wayland, where clients cannot read the screen or their own position and
  /// everything goes through xdg-desktop-portal.
  wayland,

  /// Windows, macOS, or a Linux session with no display server at all.
  other,
}

/// Reads the session type from the environment.
///
/// `XDG_SESSION_TYPE` is the authority; `WAYLAND_DISPLAY` catches sessions that
/// leave it unset (a nested compositor, some login managers). A Flutter app can
/// itself be running on XWayland inside a Wayland session — the check errs
/// towards Wayland, which is the more restricted path and never grabs a black
/// frame from an X root window that is not the real desktop.
DesktopSession currentDesktopSession({Map<String, String>? environment}) {
  if (!Platform.isLinux) return DesktopSession.other;

  final env = environment ?? Platform.environment;
  if (env['XDG_SESSION_TYPE']?.toLowerCase() == 'wayland' ||
      (env['WAYLAND_DISPLAY']?.isNotEmpty ?? false)) {
    return DesktopSession.wayland;
  }
  if (env['XDG_SESSION_TYPE']?.toLowerCase() == 'x11' ||
      (env['DISPLAY']?.isNotEmpty ?? false)) {
    return DesktopSession.x11;
  }
  return DesktopSession.other;
}
