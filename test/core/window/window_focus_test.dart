@TestOn('linux')
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/window/window_focus.dart';
import 'package:foxscreenshots/core/window/x11_window_focus.dart';

void main() {
  group('defaultWindowFocuser', () {
    test('força o foco pelo Xlib direto numa sessão X11', () {
      expect(
        defaultWindowFocuser(
          environment: const {'XDG_SESSION_TYPE': 'x11', 'DISPLAY': ':0'},
        ),
        isA<X11WindowFocus>(),
      );
    });

    test('não tenta nada no Wayland', () {
      // Wayland não dá a um cliente nenhum jeito de pedir foco fora do que o
      // window_manager já tenta (gtk_window_present); não há EWMH/Xlib aqui.
      expect(
        defaultWindowFocuser(
          environment: const {'XDG_SESSION_TYPE': 'wayland'},
        ),
        isA<NoWindowFocuser>(),
      );
    });

    test('não tenta nada sem servidor gráfico', () {
      expect(
        defaultWindowFocuser(environment: const {}),
        isA<NoWindowFocuser>(),
      );
    });
  });

  test('a implementação vazia não faz nada', () async {
    const focuser = NoWindowFocuser();
    await expectLater(focuser.forceFocus(), completes);
  });
}
