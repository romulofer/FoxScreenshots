import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/hotkey/hotkey_service.dart';
import 'package:hotkey_manager/hotkey_manager.dart';

void main() {
  test('usa PrintScreen para combinações desconhecidas', () {
    final parsed = parseCaptureHotkey('???');
    expect(parsed.key, PhysicalKeyboardKey.printScreen);
    expect(parsed.modifiers, isEmpty);
  });

  test('entende combinações com modificadores', () {
    final parsed = parseCaptureHotkey('Ctrl+Shift+S');
    expect(parsed.key, PhysicalKeyboardKey.keyS);
    expect(
      parsed.modifiers,
      containsAll([HotKeyModifier.control, HotKeyModifier.shift]),
    );
  });

  test('entende as teclas de função', () {
    expect(parseCaptureHotkey('F9').key, PhysicalKeyboardKey.f9);
  });
}
