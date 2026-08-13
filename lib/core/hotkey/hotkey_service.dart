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

  /// Registers [key] (default PrintScreen) as a system-wide trigger that calls
  /// [onPressed]. Re-registering replaces the previous binding.
  Future<void> registerCapture(
    VoidCallback onPressed, {
    PhysicalKeyboardKey key = PhysicalKeyboardKey.printScreen,
    List<HotKeyModifier>? modifiers,
  }) async {
    await hotKeyManager.unregisterAll();
    final hotKey = HotKey(
      key: key,
      modifiers: modifiers,
      scope: HotKeyScope.system,
    );
    await hotKeyManager.register(hotKey, keyDownHandler: (_) => onPressed());
  }
}

final hotkeyServiceProvider = Provider<HotkeyService>((ref) {
  return const HotkeyService();
});
