import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/hotkey/hotkey_service.dart';

// hotkey_manager's method channel (see MethodChannelHotKeyManager.register):
// the plugin serializes HotKey.modifiers with `instance.modifiers?.map(...)`,
// so a null list becomes a null 'modifiers' entry in the call arguments. The
// Windows native plugin does `std::get<EncodableList>` on that entry and
// crashes on a null/monostate value; a force-cast on the macOS side crashes
// the same way. registerCapture must therefore always pass a List, never a
// null modifiers value, even for bindings with no modifier keys.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('dev.leanflutter.plugins/hotkey_manager');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('binding sem modificadores manda lista vazia, nunca null', () async {
    await const HotkeyService().registerCapture(() {}, binding: 'PrintScreen');

    final args =
        calls.singleWhere((c) => c.method == 'register').arguments
            as Map<Object?, Object?>;
    expect(args['modifiers'], isA<List<dynamic>>());
    expect(args['modifiers'], isEmpty);
  });

  test('binding com modificadores preserva a lista', () async {
    await const HotkeyService().registerCapture(() {}, binding: 'Ctrl+Shift+S');

    final args =
        calls.singleWhere((c) => c.method == 'register').arguments
            as Map<Object?, Object?>;
    expect(args['modifiers'], isNotEmpty);
  });
}
