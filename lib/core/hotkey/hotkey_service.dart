import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hotkey_manager/hotkey_manager.dart';

/// Registers the global capture hotkey (SPEC §1). Wraps `hotkey_manager` so the
/// rest of the app never touches the plugin directly.
class HotkeyService {
  const HotkeyService();

  /// Clears any OS-level registrations left over from a previous run. Call once
  /// at startup before registering.
  Future<void> reset() => hotKeyManager.unregisterAll();

  /// Registers a system-wide trigger that calls [onPressed].
  ///
  /// [binding] is a persisted label such as `PrintScreen`, `F9`, or
  /// `Ctrl+Shift+S`. Unknown values fall back to PrintScreen so a corrupt
  /// preference cannot leave the app without a capture hotkey.
  Future<void> registerCapture(
    VoidCallback onPressed, {
    String binding = 'PrintScreen',
  }) async {
    await hotKeyManager.unregisterAll();
    final parsed = parseCaptureHotkey(binding);
    final hotKey = HotKey(
      key: parsed.key,
      modifiers: parsed.modifiers,
      scope: HotKeyScope.system,
    );
    await hotKeyManager.register(hotKey, keyDownHandler: (_) => onPressed());
  }
}

/// Result of parsing a persisted hotkey string.
typedef ParsedCaptureHotkey = ({
  PhysicalKeyboardKey key,
  List<HotKeyModifier> modifiers,
});

/// Parses labels written by [SettingsService] into a `hotkey_manager` binding.
ParsedCaptureHotkey parseCaptureHotkey(String binding) {
  final parts = binding
      .split('+')
      .map((p) => p.trim())
      .where((p) => p.isNotEmpty)
      .toList(growable: false);
  if (parts.isEmpty) {
    return (
      key: PhysicalKeyboardKey.printScreen,
      modifiers: const <HotKeyModifier>[],
    );
  }

  final modifiers = <HotKeyModifier>[];
  PhysicalKeyboardKey? key;
  for (final part in parts) {
    switch (part.toLowerCase()) {
      case 'ctrl':
      case 'control':
        modifiers.add(HotKeyModifier.control);
      case 'alt':
      case 'option':
        modifiers.add(HotKeyModifier.alt);
      case 'shift':
        modifiers.add(HotKeyModifier.shift);
      case 'meta':
      case 'cmd':
      case 'command':
      case 'super':
      case 'win':
        modifiers.add(HotKeyModifier.meta);
      default:
        key = _keyFromToken(part) ?? key;
    }
  }

  return (key: key ?? PhysicalKeyboardKey.printScreen, modifiers: modifiers);
}

PhysicalKeyboardKey? _keyFromToken(String token) {
  final normalized = token.toLowerCase().replaceAll(' ', '');
  return switch (normalized) {
    'printscreen' ||
    'prtsc' ||
    'prntscrn' ||
    'snapshot' => PhysicalKeyboardKey.printScreen,
    'f1' => PhysicalKeyboardKey.f1,
    'f2' => PhysicalKeyboardKey.f2,
    'f3' => PhysicalKeyboardKey.f3,
    'f4' => PhysicalKeyboardKey.f4,
    'f5' => PhysicalKeyboardKey.f5,
    'f6' => PhysicalKeyboardKey.f6,
    'f7' => PhysicalKeyboardKey.f7,
    'f8' => PhysicalKeyboardKey.f8,
    'f9' => PhysicalKeyboardKey.f9,
    'f10' => PhysicalKeyboardKey.f10,
    'f11' => PhysicalKeyboardKey.f11,
    'f12' => PhysicalKeyboardKey.f12,
    'a' => PhysicalKeyboardKey.keyA,
    'b' => PhysicalKeyboardKey.keyB,
    'c' => PhysicalKeyboardKey.keyC,
    'd' => PhysicalKeyboardKey.keyD,
    'e' => PhysicalKeyboardKey.keyE,
    'f' => PhysicalKeyboardKey.keyF,
    'g' => PhysicalKeyboardKey.keyG,
    'h' => PhysicalKeyboardKey.keyH,
    'i' => PhysicalKeyboardKey.keyI,
    'j' => PhysicalKeyboardKey.keyJ,
    'k' => PhysicalKeyboardKey.keyK,
    'l' => PhysicalKeyboardKey.keyL,
    'm' => PhysicalKeyboardKey.keyM,
    'n' => PhysicalKeyboardKey.keyN,
    'o' => PhysicalKeyboardKey.keyO,
    'p' => PhysicalKeyboardKey.keyP,
    'q' => PhysicalKeyboardKey.keyQ,
    'r' => PhysicalKeyboardKey.keyR,
    's' => PhysicalKeyboardKey.keyS,
    't' => PhysicalKeyboardKey.keyT,
    'u' => PhysicalKeyboardKey.keyU,
    'v' => PhysicalKeyboardKey.keyV,
    'w' => PhysicalKeyboardKey.keyW,
    'x' => PhysicalKeyboardKey.keyX,
    'y' => PhysicalKeyboardKey.keyY,
    'z' => PhysicalKeyboardKey.keyZ,
    _ => null,
  };
}

final hotkeyServiceProvider = Provider<HotkeyService>((ref) {
  return const HotkeyService();
});
