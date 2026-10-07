import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/l10n/gen/app_localizations.dart';
import 'package:foxscreenshots/features/home/widgets/thumbnail_tile.dart';
import 'package:foxscreenshots/features/preview/preview_screen.dart';
import 'package:foxscreenshots/models/capture_result.dart';

/// 1x1 transparent PNG.
final Uint8List _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA'
  '60e6kgAAAABJRU5ErkJggg==',
);

CaptureResult _capture() => CaptureResult(
  id: 'a',
  pngBytes: _png,
  width: 1,
  height: 1,
  takenAt: DateTime(2026),
);

Widget _app(Widget home) => MaterialApp(
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

void main() {
  testWidgets('tapping the thumbnail image calls onPreview, not onEdit', (
    tester,
  ) async {
    var previewed = 0;
    var edited = 0;
    await tester.pumpWidget(
      _app(
        Scaffold(
          body: SizedBox(
            width: 240,
            height: 180,
            child: ThumbnailTile(
              capture: _capture(),
              onPreview: () => previewed++,
              onEdit: () => edited++,
              onCopy: () {},
              onSave: () {},
              onDelete: () {},
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byType(Image));
    expect(previewed, 1);
    expect(edited, 0);
  });

  testWidgets('preview screen shows the image and closes with Esc', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => PreviewScreen(capture: _capture()),
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.byType(PreviewScreen), findsOneWidget);
    expect(find.byType(InteractiveViewer), findsOneWidget);
    expect(find.textContaining('1×1'), findsOneWidget);

    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(find.byType(PreviewScreen), findsNothing);
  });

  testWidgets('preview screen closes with the close button', (tester) async {
    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => PreviewScreen(capture: _capture()),
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();
    expect(find.byType(PreviewScreen), findsNothing);
  });
}
