import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/diagnostics/dependency_checker.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/features/home/widgets/dependency_banner.dart';

void main() {
  Future<void> pumpBanner(
    WidgetTester tester,
    List<DependencyIssue> issues, {
    Locale locale = const Locale('pt'),
  }) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [dependencyIssuesProvider.overrideWithValue(issues)],
        child: MaterialApp(
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: DependencyBanner()),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('não mostra nada quando a máquina tem tudo', (tester) async {
    await pumpBanner(tester, const []);

    expect(find.byType(Card), findsNothing);
  });

  testWidgets('avisa sobre uma dependência bloqueante em pt-BR', (tester) async {
    await pumpBanner(tester, const [
      DependencyIssue(SystemDependency.x11Library, DependencySeverity.blocking),
    ]);

    expect(
      find.text('A captura de tela está indisponível neste sistema'),
      findsOneWidget,
    );
    expect(find.textContaining('libX11.so.6'), findsOneWidget);
    expect(find.byIcon(Icons.error_outline), findsOneWidget);
  });

  testWidgets('usa o texto mais brando quando só há degradação', (
    tester,
  ) async {
    await pumpBanner(tester, const [
      DependencyIssue(SystemDependency.keybinder, DependencySeverity.degraded),
    ]);

    expect(
      find.text('Alguns recursos estão indisponíveis neste sistema'),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
  });

  testWidgets('lista todos os problemas e localiza para en-US', (tester) async {
    await pumpBanner(tester, const [
      DependencyIssue(
        SystemDependency.waylandSession,
        DependencySeverity.blocking,
      ),
      DependencyIssue(
        SystemDependency.appIndicator,
        DependencySeverity.degraded,
      ),
    ], locale: const Locale('en'));

    expect(
      find.text('Screen capture is unavailable on this system'),
      findsOneWidget,
    );
    expect(find.textContaining('Wayland session'), findsOneWidget);
    expect(find.textContaining('libayatana-appindicator3'), findsOneWidget);
  });

  testWidgets('pode ser dispensado', (tester) async {
    await pumpBanner(tester, const [
      DependencyIssue(SystemDependency.keybinder, DependencySeverity.degraded),
    ]);

    await tester.tap(find.text('Dispensar'));
    await tester.pumpAndSettle();

    expect(find.byType(Card), findsNothing);
  });
}
