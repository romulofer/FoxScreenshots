@TestOn('linux')
library;

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

  test('não reporta nada em uma máquina X11 completa', () {
    expect(checker().check(), isEmpty);
  });

  test('aponta a falta da libX11 como bloqueante', () {
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

  test('aponta um display inacessível quando a biblioteca em si carregou', () {
    final issues = checker(displayReachable: false).check();

    expect(issues, [
      const DependencyIssue(
        SystemDependency.xDisplay,
        DependencySeverity.blocking,
      ),
    ]);
  });

  test('não culpa também o display quando a libX11 está faltando', () {
    final issues = checker(present: const {}, displayReachable: false).check();

    expect(
      issues.map((i) => i.dependency),
      isNot(contains(SystemDependency.xDisplay)),
    );
  });

  test('aponta a sessão Wayland em vez de sondar o X', () {
    final issues = checker(
      environment: const {'XDG_SESSION_TYPE': 'wayland'},
      displayReachable: false,
    ).check();

    // Capture still works there, through the portal — so it only degrades.
    expect(issues.first.dependency, SystemDependency.waylandSession);
    expect(issues.first.severity, DependencySeverity.degraded);
  });

  test('no Wayland o atalho global é dado como perdido', () {
    final issues = checker(environment: const {'XDG_SESSION_TYPE': 'wayland'})
        .check();

    // The library is installed in this scenario and still cannot bind a key.
    expect(
      issues.map((i) => i.dependency),
      containsAll([
        SystemDependency.waylandSession,
        SystemDependency.keybinder,
      ]),
    );
  });

  test('detecta o Wayland só pela WAYLAND_DISPLAY', () {
    final issues = checker(environment: const {'WAYLAND_DISPLAY': 'wayland-0'})
        .check();

    expect(
      issues.map((i) => i.dependency),
      contains(SystemDependency.waylandSession),
    );
  });

  test('a falta do keybinder só degrada, não bloqueia', () {
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

  test('a falta do appindicator só degrada', () {
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

  test('lista de uma vez tudo o que está faltando', () {
    final issues = checker(present: const {}).check();

    expect(issues.map((i) => i.dependency), [
      SystemDependency.x11Library,
      SystemDependency.keybinder,
      SystemDependency.appIndicator,
    ]);
  });

  test('o verificador vazio nunca reporta nada', () {
    expect(const NoDependencyChecker().check(), isEmpty);
  });
}
