@TestOn('linux')
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/window/overlay_stacking.dart';
import 'package:foxscreenshots/core/window/x11_overlay_stacking.dart';

void main() {
  group('defaultOverlayStacking', () {
    test('usa o EWMH em uma sessão X11', () {
      expect(
        defaultOverlayStacking(
          environment: const {'XDG_SESSION_TYPE': 'x11', 'DISPLAY': ':0'},
        ),
        isA<X11OverlayStacking>(),
      );
    });

    test('não tenta nada no Wayland', () {
      // There a window neither spans monitors nor is told where it is; the
      // toolkit's own fullscreen is used instead.
      expect(
        defaultOverlayStacking(
          environment: const {'XDG_SESSION_TYPE': 'wayland'},
        ),
        isA<NoOverlayStacking>(),
      );
    });

    test('não tenta nada sem servidor gráfico', () {
      expect(
        defaultOverlayStacking(environment: const {}),
        isA<NoOverlayStacking>(),
      );
    });
  });

  test('a implementação vazia diz que não cobriu nada', () async {
    const stacking = NoOverlayStacking();

    expect(await stacking.spanAllMonitors(), isFalse);
    await expectLater(stacking.clear(), completes);
  });
}
