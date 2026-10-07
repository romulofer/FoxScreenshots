import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/hotkey/scroll_lock_detector.dart';

class _FakeDetector implements ScrollLockDetector {
  _FakeDetector(this.answers);

  final List<bool> answers;
  int calls = 0;

  @override
  Future<bool> isActive() async {
    final i = calls < answers.length ? calls : answers.length - 1;
    calls++;
    return answers[i];
  }
}

void main() {
  test('lê o Scroll Lock do bit certo da máscara de LEDs', () {
    expect(isScrollLockLit(0x0), isFalse);
    expect(isScrollLockLit(0x1), isFalse); // Caps Lock
    expect(isScrollLockLit(0x2), isFalse); // Num Lock
    expect(isScrollLockLit(0x3), isFalse); // Caps + Num
    expect(isScrollLockLit(0x4), isTrue);
    expect(isScrollLockLit(0x6), isTrue); // Num + Scroll (o caso real)
  });

  test('provider emite só quando o estado muda', () async {
    final detector = _FakeDetector([false, false, true]);
    final container = ProviderContainer(
      overrides: [scrollLockDetectorProvider.overrideWithValue(detector)],
    );
    addTearDown(container.dispose);

    final seen = <bool>[];
    container.listen(scrollLockActiveProvider, (_, next) {
      final v = next.valueOrNull;
      if (v != null) seen.add(v);
    });

    await Future<void>.delayed(
      scrollLockPollInterval * 2 + const Duration(milliseconds: 500),
    );

    expect(seen, [false, true]);
  });
}
