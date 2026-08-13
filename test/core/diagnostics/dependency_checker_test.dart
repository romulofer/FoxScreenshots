import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/diagnostics/dependency_checker.dart';

void main() {
  /// Builds a checker whose probes answer from [present] instead of the host.
  LinuxDependencyChecker checker({
    Set<String> present = const {
      LinuxDependencyChecker.x11Soname,
      LinuxDependencyChecker.keybinderSoname,
      LinuxDependencyChecker.appIndicatorSoname,
    },
    bool displayReachable = true,
    Map<String, String> environment = const {'XDG_SESSION_TYPE': 'x11'},
  }) {
    return LinuxDependencyChecker(
      environment: environment,
      canLoadLibrary: present.contains,
      canOpenDisplay: () => displayReachable,
    );
  }

  test('reports nothing on a fully equipped X11 machine', () {
    expect(checker().check(), isEmpty);
  });

  test('flags a missing libX11 as blocking', () {
    final issues = checker(
      present: const {
        LinuxDependencyChecker.keybinderSoname,
        LinuxDependencyChecker.appIndicatorSoname,
      },
    ).check();

    expect(issues, [
      const DependencyIssue(
        SystemDependency.x11Library,
        DependencySeverity.blocking,
      ),
    ]);
  });

  test('flags an unreachable display once the library itself loaded', () {
    final issues = checker(displayReachable: false).check();

    expect(issues, [
      const DependencyIssue(
        SystemDependency.xDisplay,
        DependencySeverity.blocking,
      ),
    ]);
  });

  test('does not also blame the display when libX11 is missing', () {
    final issues = checker(present: const {}, displayReachable: false).check();

    expect(
      issues.map((i) => i.dependency),
      isNot(contains(SystemDependency.xDisplay)),
    );
  });

  test('flags a Wayland session instead of probing X', () {
    final issues = checker(
      environment: const {'XDG_SESSION_TYPE': 'wayland'},
      displayReachable: false,
    ).check();

    expect(issues.first.dependency, SystemDependency.waylandSession);
    expect(issues.first.severity, DependencySeverity.blocking);
  });

  test('detects Wayland from WAYLAND_DISPLAY alone', () {
    final issues = checker(
      environment: const {'WAYLAND_DISPLAY': 'wayland-0'},
    ).check();

    expect(
      issues.map((i) => i.dependency),
      contains(SystemDependency.waylandSession),
    );
  });

  test('missing keybinder only degrades, it does not block', () {
    final issues = checker(
      present: const {
        LinuxDependencyChecker.x11Soname,
        LinuxDependencyChecker.appIndicatorSoname,
      },
    ).check();

    expect(issues, [
      const DependencyIssue(
        SystemDependency.keybinder,
        DependencySeverity.degraded,
      ),
    ]);
  });

  test('missing appindicator only degrades', () {
    final issues = checker(
      present: const {
        LinuxDependencyChecker.x11Soname,
        LinuxDependencyChecker.keybinderSoname,
      },
    ).check();

    expect(issues, [
      const DependencyIssue(
        SystemDependency.appIndicator,
        DependencySeverity.degraded,
      ),
    ]);
  });

  test('lists every missing component at once', () {
    final issues = checker(present: const {}).check();

    expect(issues.map((i) => i.dependency), [
      SystemDependency.x11Library,
      SystemDependency.keybinder,
      SystemDependency.appIndicator,
    ]);
  });

  test('the no-op checker never reports anything', () {
    expect(const NoDependencyChecker().check(), isEmpty);
  });
}
