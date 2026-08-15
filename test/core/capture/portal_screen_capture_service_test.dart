import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/capture/portal/screenshot_portal.dart';
import 'package:foxscreenshots/core/capture/portal_screen_capture_service.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/core/image/png_codec.dart';
import 'package:foxscreenshots/models/capture_region.dart';

import '../../helpers/fake_capture_service.dart';

/// Portal stand-in: hands back a fixed frame, or a fixed failure, and counts
/// how often it was asked.
class _FakePortal implements ScreenshotPortal {
  _FakePortal({this.png, this.failure});

  final Uint8List? png;
  final PortalException? failure;
  int calls = 0;

  @override
  Future<Uint8List> capture() async {
    calls++;
    final error = failure;
    if (error != null) throw error;
    return png!;
  }
}

void main() {
  final frame = solidPng(40, 30);

  PortalScreenCaptureService serviceFor(_FakePortal portal) =>
      PortalScreenCaptureService(portal, codec: const PngCodec.inline());

  test('a tela inteira vem do portal, com as dimensões do PNG', () async {
    final portal = _FakePortal(png: frame);

    final result = await serviceFor(portal).grabFullVirtualScreen();

    expect(result.width, 40);
    expect(result.height, 30);
    expect(result.pngBytes, frame);
    expect(portal.calls, 1);
  });

  test('uma região é recortada de um quadro novo', () async {
    final portal = _FakePortal(png: frame);

    final result = await serviceFor(portal)
        .grabRegion(const CaptureRegion(x: 5, y: 5, width: 10, height: 8));

    expect(result.width, 10);
    expect(result.height, 8);
    // Fresh pixels, not the frozen ones: that is what makes timer mode work.
    expect(portal.calls, 1);
  });

  test('a recusa do usuário vira uma falha própria', () async {
    final portal = _FakePortal(
      failure: const PortalException(PortalFailure.denied),
    );

    await expectLater(
      serviceFor(portal).grabFullVirtualScreen(),
      throwsA(
        isA<CaptureException>().having(
          (e) => e.failure,
          'failure',
          CaptureFailure.portalDenied,
        ),
      ),
    );
  });

  test('portal ausente vira uma falha própria', () async {
    final portal = _FakePortal(
      failure: const PortalException(PortalFailure.unavailable),
    );

    await expectLater(
      serviceFor(portal).grabFullVirtualScreen(),
      throwsA(
        isA<CaptureException>().having(
          (e) => e.failure,
          'failure',
          CaptureFailure.portalUnavailable,
        ),
      ),
    );
  });

  test('uma resposta que não é PNG não escapa como FormatException', () async {
    final portal = _FakePortal(png: Uint8List.fromList([1, 2, 3, 4]));

    await expectLater(
      serviceFor(portal).grabFullVirtualScreen(),
      throwsA(
        isA<CaptureException>().having(
          (e) => e.failure,
          'failure',
          CaptureFailure.portalUnavailable,
        ),
      ),
    );
  });

  test('a janela ativa é desconhecida no Wayland', () async {
    final service = serviceFor(_FakePortal(png: frame));

    expect(await service.activeWindowRegion(), isNull);
  });
}
