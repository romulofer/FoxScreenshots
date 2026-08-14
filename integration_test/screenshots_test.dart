import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/app.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/desktop/desktop_integration.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/core/storage/clipboard_service.dart';
import 'package:foxscreenshots/core/storage/output_service.dart';
import 'package:foxscreenshots/core/storage/settings_service.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/selection_overlay.dart';
import 'package:foxscreenshots/features/editor/models/editor_tool.dart';
import 'package:foxscreenshots/features/editor/widgets/editor_canvas.dart';
import 'package:foxscreenshots/features/editor/widgets/tool_rail.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:foxscreenshots/models/capture_result.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../test/helpers/fake_capture_window.dart';
import '../test/helpers/fake_clipboard.dart';
import 'helpers/e2e_app.dart';

/// Gerador das imagens do README — **não faz parte da suíte e2e**
/// (`all_tests.dart` não o importa, e a CI não o executa).
///
/// Roda o app de verdade contra as mesmas bordas falsas dos testes e2e, com uma
/// "área de trabalho" sintética no lugar da tela do usuário, e grava um PNG por
/// tela em `docs/images/`. Rodar depois de mexer na UI:
///
/// ```bash
/// xvfb-run -a flutter test integration_test/screenshots_test.dart -d linux
/// ```
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  for (final locale in const ['pt', 'en']) {
    group('screenshots ($locale)', () {
      testWidgets('janela principal, seleção, editor e configurações', (
        tester,
      ) async {
        await _pumpShowcase(tester, locale: locale);

        // Uma captura de tela cheia (a que o editor vai abrir) e várias
        // recortadas, para a galeria não ficar com miniaturas idênticas.
        await _tapIcon(tester, Icons.fullscreen);
        for (final rect in _galleryCrops) {
          await _instantCapture(
            tester,
            from: rect.topLeft,
            to: rect.bottomRight,
          );
        }

        // A última serve de pose para a foto do seletor: o retângulo fica
        // desenhado na tela no momento em que o PNG é gravado.
        await _tapIcon(tester, Icons.crop_free);
        final gesture = await _dragTo(
          tester,
          from: const Offset(180, 190),
          to: const Offset(700, 480),
        );
        expect(find.byType(CaptureSelectionOverlay), findsOneWidget);
        // O seletor não mostra texto durante o arrasto: uma imagem serve para
        // as duas metades do README.
        if (locale == 'pt') await _shoot(tester, 'selecao', null);
        await gesture.up();
        await tester.pumpAndSettle();

        // Cada tela é fotografada na altura em que o seu conteúdo cabe, para o
        // README não ficar cheio de imagens com metade vazia.
        await _resize(tester, 560);
        await _shoot(tester, 'janela-principal', locale);
        await _resize(tester, _windowSize.height);

        // Editor, com uma amostra de cada família de ferramenta. Abre a captura
        // mais antiga — a de tela cheia, que preenche a área de desenho.
        await tester.tap(find.byIcon(Icons.edit_outlined).last);
        await tester.pumpAndSettle();
        await _annotate(tester, locale: locale);
        await _shoot(tester, 'editor', locale);

        await _tapIcon(tester, Icons.arrow_back);
        await tester.pumpAndSettle();
        await tester.tap(find.text(_discardLabel(locale)));
        await tester.pumpAndSettle();

        await _tapIcon(tester, Icons.settings_outlined);
        await _resize(tester, 340);
        await _shoot(tester, 'configuracoes', locale);
      });
    });
  }
}

/// Tamanho da janela retratada, em pixels lógicos.
const Size _windowSize = Size(1040, 680);

/// Fator de escala do PNG gravado: 2× deixa o texto nítido em telas HiDPI sem
/// gerar um arquivo grande demais para o README.
const double _shotScale = 2;

/// Tamanho da área de trabalho sintética que o backend falso "fotografa".
const int _desktopWidth = 1280;
const int _desktopHeight = 800;

final _shotKey = GlobalKey(debugLabel: 'screenshot-boundary');

/// Sobe o app com as bordas de sistema operacional trocadas por dublês.
///
/// Não reaproveita `pumpE2EApp` porque aqui o backend de captura devolve uma
/// área de trabalho desenhada (e não o gradiente de teste), e a raiz precisa de
/// um [RepaintBoundary] para virar PNG.
Future<void> _pumpShowcase(
  WidgetTester tester, {
  required String locale,
}) async {
  tester.view.physicalSize = _windowSize * _shotScale;
  tester.view.devicePixelRatio = _shotScale;
  addTearDown(tester.view.reset);

  SharedPreferences.setMockInitialValues({
    'locale_tag': locale,
    'theme_mode': 'dark',
    'timer_delay_seconds': 5,
  });
  final prefs = await SharedPreferences.getInstance();

  final desktop = await _renderDesktopPng(tester);
  final output = TempDirOutputService();
  addTearDown(output.cleanUp);

  final container = ProviderContainer(
    overrides: [
      settingsServiceProvider.overrideWithValue(SettingsService(prefs)),
      screenCaptureServiceProvider.overrideWithValue(
        _MockDesktopCaptureService(desktop),
      ),
      captureWindowControllerProvider.overrideWithValue(
        FakeCaptureWindow(
          virtualScreen: Rect.fromLTWH(
            0,
            0,
            _windowSize.width,
            _windowSize.height,
          ),
        ),
      ),
      desktopIntegrationProvider.overrideWithValue(FakeDesktopIntegration()),
      clipboardServiceProvider.overrideWithValue(RecordingClipboardService()),
      outputServiceProvider.overrideWithValue(output),
      pngCodecProvider.overrideWithValue(const PngCodec.inline()),
    ],
  );
  addTearDown(container.dispose);

  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: RepaintBoundary(key: _shotKey, child: const FoxScreenShotsApp()),
    ),
  );
  await tester.pumpAndSettle();
}

/// Recortes que povoam a galeria da janela principal, em pixels lógicos.
const _galleryCrops = <Rect>[
  Rect.fromLTRB(120, 140, 560, 430),
  Rect.fromLTRB(430, 260, 900, 600),
  Rect.fromLTRB(60, 300, 480, 590),
  Rect.fromLTRB(520, 90, 940, 380),
  Rect.fromLTRB(240, 120, 620, 380),
  Rect.fromLTRB(600, 320, 1000, 620),
];

/// Muda a altura da janela retratada, mantendo a largura.
Future<void> _resize(WidgetTester tester, double height) async {
  tester.view.physicalSize = Size(_windowSize.width, height) * _shotScale;
  await tester.pumpAndSettle();
}

/// Grava o quadro atual em `docs/images/<nome>[-<locale>].png`.
Future<void> _shoot(WidgetTester tester, String name, String? locale) async {
  final boundary =
      _shotKey.currentContext!.findRenderObject()! as RenderRepaintBoundary;

  final bytes = await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: _shotScale);
    try {
      final data = await image.toByteData(format: ui.ImageByteFormat.png);
      return data!.buffer.asUint8List();
    } finally {
      image.dispose();
    }
  });

  final suffix = locale == null ? '' : '-$locale';
  final dir = Directory('docs/images')..createSync(recursive: true);
  File('${dir.path}/$name$suffix.png').writeAsBytesSync(bytes!, flush: true);
}

/// Captura instantânea completa: abre o seletor, arrasta e solta.
Future<void> _instantCapture(
  WidgetTester tester, {
  required Offset from,
  required Offset to,
}) async {
  await _tapIcon(tester, Icons.crop_free);
  final gesture = await _dragTo(tester, from: from, to: to);
  await gesture.up();
  await tester.pumpAndSettle();
}

/// Desenha uma marca de cada família de ferramenta do editor.
///
/// As coordenadas são pixels lógicos da janela e ficam dentro da imagem
/// desenhada (que ocupa cerca de x∈[130, 975], y∈[70, 595] com a captura de
/// tela cheia aberta).
Future<void> _annotate(WidgetTester tester, {required String locale}) async {
  expect(find.byType(EditorCanvas), findsOneWidget);

  await _selectTool(tester, EditorTool.rectangle);
  await _drag(tester, const Offset(330, 180), const Offset(560, 320));

  await _selectTool(tester, EditorTool.arrow);
  await _drag(tester, const Offset(770, 150), const Offset(600, 230));

  await _selectTool(tester, EditorTool.ellipse);
  await _drag(tester, const Offset(680, 300), const Offset(860, 410));

  await _selectTool(tester, EditorTool.highlight);
  await _drag(tester, const Offset(330, 390), const Offset(620, 415));

  // Sobre as linhas de texto da janela de baixo, onde a tarja tem o que cobrir.
  await _selectTool(tester, EditorTool.pixelate);
  await _drag(tester, const Offset(560, 430), const Offset(860, 500));

  await _selectTool(tester, EditorTool.step);
  await tester.tapAt(const Offset(300, 165));
  await tester.pumpAndSettle();

  await _selectTool(tester, EditorTool.text);
  await tester.tapAt(const Offset(640, 560));
  await tester.pumpAndSettle();
  await tester.enterText(
    find.byType(TextField),
    locale == 'pt' ? 'olhe aqui' : 'look here',
  );
  await tester.tap(
    find.descendant(
      of: find.byType(AlertDialog),
      matching: find.byType(FilledButton),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _selectTool(WidgetTester tester, EditorTool tool) async {
  final button = find.ancestor(
    of: find.byIcon(toolIcon(tool)),
    matching: find.byType(IconButton),
  );
  await tester.ensureVisible(button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}

Future<void> _tapIcon(WidgetTester tester, IconData icon) async {
  await tester.tap(find.byIcon(icon).first);
  await tester.pumpAndSettle();
}

/// Arrasta em passos e solta.
Future<void> _drag(WidgetTester tester, Offset from, Offset to) async {
  final gesture = await _dragTo(tester, from: from, to: to);
  await gesture.up();
  await tester.pumpAndSettle();
}

/// Arrasta em passos e **mantém o botão pressionado**, para o quadro poder ser
/// fotografado no meio do gesto.
///
/// Em passos porque um único movimento além do limiar de arrasto é reportado
/// como o *início* do gesto, sem nenhuma atualização atrás dele.
Future<TestGesture> _dragTo(
  WidgetTester tester, {
  required Offset from,
  required Offset to,
  int steps = 6,
}) async {
  final gesture = await tester.startGesture(from);
  await tester.pump();
  for (var i = 1; i <= steps; i++) {
    await gesture.moveTo(Offset.lerp(from, to, i / steps)!);
    await tester.pump();
  }
  return gesture;
}

String _discardLabel(String locale) => locale == 'pt' ? 'Descartar' : 'Discard';

/// Backend de captura que devolve sempre a mesma área de trabalho desenhada.
class _MockDesktopCaptureService implements ScreenCaptureService {
  _MockDesktopCaptureService(this._desktopPng);

  final Uint8List _desktopPng;

  @override
  Future<CaptureResult> grabFullVirtualScreen() async => CaptureResult(
    id: 'shot-${DateTime.now().microsecondsSinceEpoch}',
    pngBytes: _desktopPng,
    width: _desktopWidth,
    height: _desktopHeight,
    takenAt: DateTime(2026, 8, 14, 10, 32),
  );

  /// Recorta a área pedida da mesma imagem, para as miniaturas da galeria não
  /// saírem todas iguais.
  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) async {
    final cropped = await const PngCodec.inline().crop(_desktopPng, region);
    return CaptureResult(
      id: 'shot-${DateTime.now().microsecondsSinceEpoch}',
      pngBytes: cropped!.pngBytes,
      width: cropped.width,
      height: cropped.height,
      takenAt: DateTime(2026, 8, 14, 10, 33),
    );
  }

  @override
  Future<CaptureRegion?> activeWindowRegion() async =>
      const CaptureRegion(x: 96, y: 72, width: 720, height: 480);

  @override
  Future<({int width, int height})> virtualScreenSize() async =>
      (width: _desktopWidth, height: _desktopHeight);
}

/// Desenha uma área de trabalho plausível e a codifica em PNG.
///
/// Nada de tela real: as imagens do README não devem carregar nada da máquina
/// de quem as gerou.
Future<Uint8List> _renderDesktopPng(WidgetTester tester) async {
  final bytes = await tester.runAsync(() async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    _paintMockDesktop(
      canvas,
      const Size(_desktopWidth * 1.0, _desktopHeight * 1.0),
    );
    final picture = recorder.endRecording();
    try {
      final image = await picture.toImage(_desktopWidth, _desktopHeight);
      try {
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        return data!.buffer.asUint8List();
      } finally {
        image.dispose();
      }
    } finally {
      picture.dispose();
    }
  });
  return bytes!;
}

void _paintMockDesktop(Canvas canvas, Size size) {
  canvas.drawRect(
    Offset.zero & size,
    Paint()
      ..shader = ui.Gradient.linear(
        Offset.zero,
        Offset(size.width, size.height),
        const [Color(0xFF12263F), Color(0xFF3A1F4D), Color(0xFF7A3B1E)],
        const [0, 0.55, 1],
      ),
  );

  // Barra superior do "sistema".
  canvas.drawRect(
    Rect.fromLTWH(0, 0, size.width, 34),
    Paint()..color = const Color(0xCC0B1626),
  );
  for (var i = 0; i < 4; i++) {
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(18 + i * 96.0, 11, 72, 12),
        const Radius.circular(6),
      ),
      Paint()..color = const Color(0x66FFFFFF),
    );
  }

  _paintWindow(
    canvas,
    const Rect.fromLTWH(90, 96, 700, 470),
    accent: const Color(0xFF4C8DFF),
    rows: 9,
  );
  _paintWindow(
    canvas,
    const Rect.fromLTWH(540, 330, 640, 400),
    accent: const Color(0xFFFF8A3D),
    rows: 7,
  );
}

/// Uma "janela" genérica: barra de título, barra lateral e linhas de conteúdo.
void _paintWindow(
  Canvas canvas,
  Rect frame, {
  required Color accent,
  required int rows,
}) {
  final body = RRect.fromRectAndRadius(frame, const Radius.circular(14));

  canvas.drawRRect(
    body.shift(const Offset(0, 10)),
    Paint()
      ..color = const Color(0x552B0B3A)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 18),
  );
  canvas.drawRRect(body, Paint()..color = const Color(0xFF161B26));

  canvas.save();
  canvas.clipRRect(body);

  const titleBarHeight = 38.0;
  canvas.drawRect(
    Rect.fromLTWH(frame.left, frame.top, frame.width, titleBarHeight),
    Paint()..color = const Color(0xFF222A38),
  );
  for (var i = 0; i < 3; i++) {
    canvas.drawCircle(
      Offset(frame.left + 22 + i * 20, frame.top + titleBarHeight / 2),
      6,
      Paint()
        ..color = const [
          Color(0xFFFF5F57),
          Color(0xFFFEBC2E),
          Color(0xFF28C840),
        ][i],
    );
  }

  final sidebar = Rect.fromLTWH(
    frame.left,
    frame.top + titleBarHeight,
    150,
    frame.height - titleBarHeight,
  );
  canvas.drawRect(sidebar, Paint()..color = const Color(0xFF1D2431));
  for (var i = 0; i < 6; i++) {
    final selected = i == 1;
    final row = Rect.fromLTWH(
      sidebar.left + 14,
      sidebar.top + 18 + i * 34,
      sidebar.width - 28,
      20,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(row, const Radius.circular(6)),
      Paint()..color = selected ? accent : const Color(0x33FFFFFF),
    );
  }

  final contentLeft = sidebar.right + 26;
  final contentWidth = frame.right - contentLeft - 26;
  canvas.drawRRect(
    RRect.fromRectAndRadius(
      Rect.fromLTWH(
        contentLeft,
        frame.top + titleBarHeight + 22,
        contentWidth * 0.45,
        22,
      ),
      const Radius.circular(8),
    ),
    Paint()..color = accent.withValues(alpha: 0.85),
  );
  // Linhas de "texto" feitas de palavras curtas em vez de uma barra só: sem
  // esse detalhe fino, borrar ou pixelar a região não muda nada na imagem e a
  // foto do editor não mostraria o que a tarja faz.
  const wordWidths = <double>[38, 62, 24, 50, 30, 44, 56, 28];
  for (var i = 0; i < rows; i++) {
    final lineWidth = contentWidth * (i.isEven ? 0.92 : 0.66);
    final paint = Paint()
      ..color = Color.lerp(
        const Color(0x33FFFFFF),
        const Color(0x14FFFFFF),
        i / rows,
      )!;
    var x = contentLeft;
    var word = i;
    while (x < contentLeft + lineWidth) {
      final width = wordWidths[word++ % wordWidths.length];
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(
            x,
            frame.top + titleBarHeight + 68 + i * 30,
            width.clamp(0, contentLeft + lineWidth - x),
            12,
          ),
          const Radius.circular(6),
        ),
        paint,
      );
      x += width + 10;
    }
  }

  canvas.restore();
}
