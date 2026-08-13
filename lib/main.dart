import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hotkey_manager/hotkey_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:window_manager/window_manager.dart';

import 'app.dart';
import 'core/storage/settings_service.dart';

/// Bootstrap: init desktop window + plugins, load persisted settings, then run
/// the app. Tray and global-hotkey wiring happen from the widget tree once the
/// first frame has localized labels (SPEC §1, §4).
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await windowManager.ensureInitialized();
  const windowOptions = WindowOptions(
    size: Size(920, 640),
    minimumSize: Size(640, 480),
    center: true,
    title: 'FoxScreenShots',
  );
  await windowManager.waitUntilReadyToShow(windowOptions, () async {
    await windowManager.show();
    await windowManager.focus();
  });

  // Drop any global hotkeys left registered by a previous run.
  await hotKeyManager.unregisterAll();

  final prefs = await SharedPreferences.getInstance();

  runApp(
    ProviderScope(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
      ],
      child: const FoxScreenShotsApp(),
    ),
  );
}
