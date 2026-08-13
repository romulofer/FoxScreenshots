import 'dart:ffi';
import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../capture/x11/x11_bindings.dart';

/// A system component the app needs at runtime.
enum SystemDependency {
  /// `libX11.so.6` — the screen capture backend talks to it directly.
  x11Library,

  /// A reachable X display (`DISPLAY`, and a server that accepts the
  /// connection).
  xDisplay,

  /// The session is Wayland, where the X backend cannot see the desktop.
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
  List<DependencyIssue> check();
}

/// Linux implementation: probes each shared library with `dlopen` and tries a
/// throwaway connection to the X server.
///
/// The probes are injectable so the logic can be unit-tested on any machine.
class LinuxDependencyChecker implements DependencyChecker {
  LinuxDependencyChecker({
    Map<String, String>? environment,
    bool Function(String soname)? canLoadLibrary,
    bool Function()? canOpenDisplay,
  }) : _env = environment ?? Platform.environment,
       _canLoadLibrary = canLoadLibrary ?? canLoadSharedLibrary,
       _canOpenDisplay = canOpenDisplay ?? canConnectToXDisplay;

  /// Sonames as the dynamic linker knows them (`ldconfig -p`).
  static const String x11Soname = 'libX11.so.6';
  static const String keybinderSoname = 'libkeybinder-3.0.so.0';
  static const String appIndicatorSoname = 'libayatana-appindicator3.so.1';

  final Map<String, String> _env;
  final bool Function(String) _canLoadLibrary;
  final bool Function() _canOpenDisplay;

  @override
  List<DependencyIssue> check() {
    final issues = <DependencyIssue>[];

    final sessionType = _env['XDG_SESSION_TYPE']?.toLowerCase();
    final isWayland =
        sessionType == 'wayland' ||
        (_env['WAYLAND_DISPLAY']?.isNotEmpty ?? false);

    if (isWayland) {
      issues.add(
        const DependencyIssue(
          SystemDependency.waylandSession,
          DependencySeverity.blocking,
        ),
      );
    } else if (!_canLoadLibrary(x11Soname)) {
      issues.add(
        const DependencyIssue(
          SystemDependency.x11Library,
          DependencySeverity.blocking,
        ),
      );
    } else if (!_canOpenDisplay()) {
      // Only worth reporting once the library itself loaded.
      issues.add(
        const DependencyIssue(
          SystemDependency.xDisplay,
          DependencySeverity.blocking,
        ),
      );
    }

    if (!_canLoadLibrary(keybinderSoname)) {
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
  List<DependencyIssue> check() => const [];
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
bool canConnectToXDisplay() {
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
final dependencyIssuesProvider = Provider<List<DependencyIssue>>((ref) {
  return ref.watch(dependencyCheckerProvider).check();
});
