import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations_en.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations_pt.dart';
import 'package:foxscreenshots/core/tray/tray_service.dart';
import 'package:foxscreenshots/features/menu/tray_menu.dart';

void main() {
  group('trayMenuLabels', () {
    test('rotula toda ação da bandeja, em cada idioma', () {
      for (final l10n in [AppLocalizationsPt(), AppLocalizationsEn()]) {
        final labels = trayMenuLabels(l10n);
        for (final action in TrayAction.values) {
          expect(
            labels[action],
            isNotEmpty,
            reason: '${action.name} ficaria sem rótulo em ${l10n.localeName}',
          );
        }
      }
    });

    test(
      'oferece mostrar a janela — na bandeja é o único caminho de volta',
      () {
        expect(
          trayMenuLabels(AppLocalizationsPt())[TrayAction.show],
          'Mostrar janela',
        );
        expect(
          trayMenuLabels(AppLocalizationsEn())[TrayAction.show],
          'Show window',
        );
      },
    );
  });
}
