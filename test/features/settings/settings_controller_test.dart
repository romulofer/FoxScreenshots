import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/features/settings/settings_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late ProviderContainer container;

  Future<void> makeContainer() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    container = ProviderContainer(
      overrides: [
        settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
      ],
    );
    addTearDown(container.dispose);
  }

  test('começa com o tema do sistema e o idioma do sistema', () async {
    await makeContainer();
    final state = container.read(settingsControllerProvider);
    expect(state.themeMode, ThemeMode.system);
    expect(state.locale, isNull);
    expect(state.timerDelaySeconds, 3);
  });

  test('setThemeMode atualiza o estado e persiste', () async {
    await makeContainer();
    final controller = container.read(settingsControllerProvider.notifier);

    await controller.setThemeMode(ThemeMode.dark);

    expect(
      container.read(settingsControllerProvider).themeMode,
      ThemeMode.dark,
    );
    expect(container.read(settingsServiceProvider).themeMode, 'dark');
  });

  test('setLocale nulo volta para o idioma do sistema', () async {
    await makeContainer();
    final controller = container.read(settingsControllerProvider.notifier);

    await controller.setLocale(const Locale('en'));
    expect(
      container.read(settingsControllerProvider).locale,
      const Locale('en'),
    );

    await controller.setLocale(null);
    expect(container.read(settingsControllerProvider).locale, isNull);
    expect(container.read(settingsServiceProvider).localeTag, 'system');
  });
}
