import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/hotkey/scroll_lock_detector.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/features/home/widgets/scroll_lock_banner.dart';

void main() {
  Future<void> pumpBanner(
    WidgetTester tester, {
    required bool active,
    Locale locale = const Locale('pt'),
  }) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          scrollLockActiveProvider.overrideWith((ref) => Stream.value(active)),
        ],
        child: MaterialApp(
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: ScrollLockBanner()),
        ),
      ),
    );
    await tester.pump();
  }

  testWidgets('não mostra nada com o Scroll Lock desligado', (tester) async {
    await pumpBanner(tester, active: false);

    expect(find.byType(Card), findsNothing);
  });

  testWidgets('avisa em pt-BR com o Scroll Lock ligado', (tester) async {
    await pumpBanner(tester, active: true);

    expect(find.text('Scroll Lock está ativo'), findsOneWidget);
    expect(find.textContaining('atalhos globais'), findsOneWidget);
  });

  testWidgets('avisa em inglês com o Scroll Lock ligado', (tester) async {
    await pumpBanner(tester, active: true, locale: const Locale('en'));

    expect(find.text('Scroll Lock is on'), findsOneWidget);
  });
}
