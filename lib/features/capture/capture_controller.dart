import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../core/image/png_codec.dart';
import '../../core/navigation/app_navigator.dart';
import '../../core/window/capture_window_controller.dart';
import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../home/session_controller.dart';
import '../settings/settings_controller.dart';
import 'image_decoder.dart';
import 'screen_mapping.dart';
import 'selection_overlay.dart';

/// Runs the capture flows (SPEC §2.1) end to end: hide the hub window, grab
/// pixels, let the user pick a region on the frozen frame, then hand the result
/// to the session.
///
/// Every flow returns `null` when the user cancels, and throws
/// [CaptureException] when the platform cannot deliver a frame. The hub window
/// is always restored, success or not.
class CaptureController {
  CaptureController(this._ref);

  final Ref _ref;

  /// Guards against overlapping captures from toolbar, tray and hotkey.
  bool _busy = false;

  ScreenCaptureService get _service => _ref.read(screenCaptureServiceProvider);
  CaptureWindowController get _window =>
      _ref.read(captureWindowControllerProvider);
  PngCodec get _codec => _ref.read(pngCodecProvider);

  /// Entry point for the toolbar, the tray menu and the global hotkey.
  Future<CaptureResult?> capture(CaptureMode mode) => switch (mode) {
    CaptureMode.instant => captureInstant(),
    CaptureMode.timer => captureWithTimer(),
    CaptureMode.fullScreen => captureFullScreen(),
    CaptureMode.activeWindow => captureActiveWindow(),
  };

  /// Instant mode: freeze every screen, drag a region, crop it out of the
  /// frozen frame (SPEC §2.1). Cropping the freeze — rather than re-grabbing —
  /// is what makes open menus and tooltips survive in the shot.
  Future<CaptureResult?> captureInstant() => _guarded(() async {
    return _run(() async {
      final frozen = await _service.grabFullVirtualScreen();
      final region = await _selectRegion(
        screenWidth: frozen.width,
        screenHeight: frozen.height,
        pngBytes: frozen.pngBytes,
      );
      if (region == null) return null;

      final cropped = await _codec.crop(frozen.pngBytes, region);
      if (cropped == null) return null;
      return _record(
        CaptureResult(
          id: _newId(),
          pngBytes: cropped.pngBytes,
          width: cropped.width,
          height: cropped.height,
          takenAt: DateTime.now(),
        ),
      );
    });
  });

  /// Timer mode: frame a region over the *live* desktop, wait, then grab that
  /// region so menus/tooltips opened during the delay appear in the shot
  /// (SPEC §2.1).
  Future<CaptureResult?> captureWithTimer({Duration? delay}) => _guarded(
    () async {
      return _run(() async {
        final size = await _service.virtualScreenSize();
        final region = await _selectRegion(
          screenWidth: size.width,
          screenHeight: size.height,
          transparent: true,
        );
        if (region == null) return null;

        final wait =
            delay ??
            Duration(
              seconds: _ref.read(settingsControllerProvider).timerDelaySeconds,
            );
        await Future<void>.delayed(wait);
        return _record(await _service.grabRegion(region));
      });
    },
  );

  /// Whole virtual screen, no selection step.
  Future<CaptureResult?> captureFullScreen() => _guarded(() async {
    return _run(() async => _record(await _service.grabFullVirtualScreen()));
  });

  /// The window that had focus after the hub was hidden.
  Future<CaptureResult?> captureActiveWindow() => _guarded(() async {
    return _run(() async {
      final region = await _resolveActiveWindow();
      if (region == null || region.isEmpty) {
        throw const CaptureException(CaptureFailure.noActiveWindow);
      }
      return _record(await _service.grabRegion(region));
    });
  });

  /// Rejects overlapping captures from toolbar, tray and hotkey.
  Future<CaptureResult?> _guarded(
    Future<CaptureResult?> Function() body,
  ) async {
    if (_busy) return null;
    _busy = true;
    try {
      return await body();
    } finally {
      _busy = false;
    }
  }

  /// Hides the hub for the duration of [body] and always brings it back.
  Future<CaptureResult?> _run(Future<CaptureResult?> Function() body) async {
    await _window.hideForCapture();
    try {
      return await body();
    } finally {
      await _window.restore();
    }
  }

  /// Prefer a non-empty focus after hide; call twice in case the first read
  /// still sees nothing focused.
  Future<CaptureRegion?> _resolveActiveWindow() async {
    final first = await _service.activeWindowRegion();
    if (first != null && !first.isEmpty) return first;
    return _service.activeWindowRegion();
  }

  /// Shows a fullscreen selection overlay and resolves with the region the
  /// user dragged (screen pixels), or `null` if they cancelled.
  ///
  /// Pass [pngBytes] for instant (frozen) mode. Omit it and set
  /// [transparent] for timer (live) mode.
  Future<CaptureRegion?> _selectRegion({
    required int screenWidth,
    required int screenHeight,
    Uint8List? pngBytes,
    bool transparent = false,
  }) async {
    final navigator = _ref.read(navigatorKeyProvider).currentState;
    if (navigator == null) {
      throw const CaptureException(CaptureFailure.windowNotReady);
    }

    ui.Image? backdrop;
    if (pngBytes != null) {
      backdrop = await _ref.read(imageDecoderProvider)(pngBytes);
    }

    // Size and position the window first, then push the overlay against that
    // placement, then show it: the hub is never seen stretched across the
    // monitors, and a frozen frame is drawn 1:1 from the start.
    final requested = await _window.enterOverlay(transparent: transparent);
    final mapping = ValueNotifier<ScreenMapping>(
      ScreenMapping.fromPlacement(requested, imageWidth: screenWidth),
    );

    try {
      final pending = navigator.push<CaptureRegion>(
        PageRouteBuilder<CaptureRegion>(
          opaque: !transparent,
          barrierColor: transparent ? Colors.transparent : null,
          transitionDuration: Duration.zero,
          reverseTransitionDuration: Duration.zero,
          pageBuilder: (context, _, _) => CaptureSelectionOverlay(
            backdrop: backdrop,
            screenWidth: screenWidth,
            screenHeight: screenHeight,
            mapping: mapping,
            onSelected: (region) => Navigator.of(context).pop(region),
            onCancel: () => Navigator.of(context).pop(),
          ),
        ),
      );

      // A window manager may clamp the overlay (to one monitor, or away from a
      // panel); re-map against what it actually granted.
      final granted = await _window.revealOverlay();
      mapping.value = ScreenMapping.fromPlacement(
        granted,
        imageWidth: screenWidth,
      );

      return await pending;
    } finally {
      try {
        await _window.leaveOverlay();
      } catch (_) {
        // Still tear down mapping/image even if the window restore fails.
      }
      mapping.dispose();
      backdrop?.dispose();
    }
  }

  CaptureResult _record(CaptureResult result) {
    _ref.read(sessionControllerProvider.notifier).add(result);
    return result;
  }

  String _newId() => 'shot-${DateTime.now().microsecondsSinceEpoch}';
}

final captureControllerProvider = Provider<CaptureController>((ref) {
  return CaptureController(ref);
});
