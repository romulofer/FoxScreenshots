import 'dart:ffi';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../capture/x11/x11_bindings.dart';
import '../desktop/session_type.dart';

/// A system component the app needs at runtime.
enum SystemDependency {
  /// `libX11.so.6` — the screen capture backend talks to it directly.
  x11Library,

  /// A reachable X display (`DISPLAY`, and a server that accepts the
  /// connection).
  xDisplay,

  /// The session is Wayland: capture goes through xdg-desktop-portal, which
  /// asks the user for permission and cannot frame another window.
  waylandSession,

  /// `libkeybinder-3.0.so.0` — global hotkeys.
  keybinder,

  /// `libayatana-appindicator3.so.1` — the tray icon.
  appIndicator,
}

/// How badly a missing dependency hurts.
enum DependencySeverity {
  /// Capture cannot work at all.
  blocking,

  /// The app runs, but one feature is degraded.
  degraded,
}

/// One unmet requirement, ready to be shown to the user.
class DependencyIssue {
  const DependencyIssue(this.dependency, this.severity);

  final SystemDependency dependency;
  final DependencySeverity severity;

  @override
  bool operator ==(Object other) =>
      other is DependencyIssue &&
      other.dependency == dependency &&
      other.severity == severity;

  @override
  int get hashCode => Object.hash(dependency, severity);

  @override
  String toString() => 'DependencyIssue(${dependency.name}, ${severity.name})';
}

/// Checks that the shared libraries and display server the app relies on are
/// actually there, so a user on a bare distro gets an explanation instead of a
/// dead button (or a crash).
abstract interface class DependencyChecker {
  /// Unmet requirements, most severe first. Empty means everything is in place.
  Future<List<DependencyIssue>> check();
}

/// Linux implementation: probes each shared library with `dlopen` and tries a
/// throwaway connection to the X server.
///
/// The probes are injectable so the logic can be unit-tested on any machine.
class LinuxDependencyChecker implements DependencyChecker {
  LinuxDependencyChecker({
    Map<String, String>? environment,
    bool Function(String soname)? canLoadLibrary,
    Future<bool> Function()? canOpenDisplay,
  }) : _env = environment ?? Platform.environment,
       _canLoadLibrary = canLoadLibrary ?? canLoadSharedLibrary,
       _canOpenDisplay = canOpenDisplay ?? canConnectToXDisplay;

  /// Sonames as the dynamic linker knows them (`ldconfig -p`).
  static const String x11Soname = 'libX11.so.6';
  static const String keybinderSoname = 'libkeybinder-3.0.so.0';
  static const String appIndicatorSoname = 'libayatana-appindicator3.so.1';

  final Map<String, String> _env;
  final bool Function(String) _canLoadLibrary;
  final Future<bool> Function() _canOpenDisplay;

  @override
  Future<List<DependencyIssue>> check() async {
    final issues = <DependencyIssue>[];

    final isWayland =
        currentDesktopSession(environment: _env) == DesktopSession.wayland;

    if (isWayland) {
      // Capture still works, through the portal — with a permission prompt,
      // without window framing, and without global hotkeys.
      issues.add(
        const DependencyIssue(
          SystemDependency.waylandSession,
          DependencySeverity.degraded,
        ),
      );
    } else if (!_canLoadLibrary(x11Soname)) {
      issues.add(
        const DependencyIssue(
          SystemDependency.x11Library,
          DependencySeverity.blocking,
        ),
      );
    } else if (!await _canOpenDisplay()) {
      // Only worth reporting once the library itself loaded.
      issues.add(
        const DependencyIssue(
          SystemDependency.xDisplay,
          DependencySeverity.blocking,
        ),
      );
    }

    // Wayland has no way for a client to grab a key globally — the library may
    // well be installed, and it still cannot bind anything.
    if (isWayland || !_canLoadLibrary(keybinderSoname)) {
      issues.add(
        const DependencyIssue(
          SystemDependency.keybinder,
          DependencySeverity.degraded,
        ),
      );
    }
    if (!_canLoadLibrary(appIndicatorSoname)) {
      issues.add(
        const DependencyIssue(
          SystemDependency.appIndicator,
          DependencySeverity.degraded,
        ),
      );
    }

    return issues;
  }
}

/// Reports nothing missing; used on platforms with no checker yet, and in
/// widget tests.
class NoDependencyChecker implements DependencyChecker {
  const NoDependencyChecker();

  @override
  Future<List<DependencyIssue>> check() async => const [];
}

/// `dlopen` probe: does this shared library exist and load?
bool canLoadSharedLibrary(String soname) {
  try {
    DynamicLibrary.open(soname);
    return true;
  } on Object {
    return false;
  }
}

/// Opens and immediately closes an X connection, to prove the display server
/// is reachable before the user tries to capture.
///
/// Runs in a background isolate: `XOpenDisplay` can block for a long time on
/// a slow or half-dead connection (e.g. X11 forwarded over SSH, a hung
/// compositor), and this probe used to run straight on the UI isolate during
/// the first build — a hung X server meant a frozen app before it ever showed
/// a window.
Future<bool> canConnectToXDisplay() async {
  try {
    return await Isolate.run(_canConnectToXDisplaySync);
  } on Object {
    return false;
  }
}

bool _canConnectToXDisplaySync() {
  try {
    final x11 = X11Lib.open();
    final display = x11.openDisplay(nullptr);
    if (display == nullptr) return false;
    x11.closeDisplay(display);
    return true;
  } on Object {
    return false;
  }
}

final dependencyCheckerProvider = Provider<DependencyChecker>((ref) {
  if (Platform.isLinux) return LinuxDependencyChecker();
  return const NoDependencyChecker();
});

/// Unmet requirements for this machine, evaluated once per app run.
final dependencyIssuesProvider = FutureProvider<List<DependencyIssue>>((ref) {
  return ref.watch(dependencyCheckerProvider).check();
});
