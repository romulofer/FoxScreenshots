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
  Future<CaptureResult?> captureInstant() async {
    return _run(() async {
      final frozen = await _service.grabFullVirtualScreen();
      final region = await _selectRegion(frozen);
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
  }

  /// Timer mode: pick the region on a frozen frame first, then grab that same
  /// region live after the configured delay, leaving the user free to open
  /// menus or hover states in the meantime (SPEC §2.1).
  Future<CaptureResult?> captureWithTimer({Duration? delay}) async {
    return _run(() async {
      final frozen = await _service.grabFullVirtualScreen();
      final region = await _selectRegion(frozen);
      if (region == null) return null;

      final wait =
          delay ??
          Duration(
            seconds: _ref.read(settingsControllerProvider).timerDelaySeconds,
          );
      await Future<void>.delayed(wait);
      return _record(await _service.grabRegion(region));
    });
  }

  /// Whole virtual screen, no selection step.
  Future<CaptureResult?> captureFullScreen() async {
    return _run(() async => _record(await _service.grabFullVirtualScreen()));
  }

  /// The window that had focus before the hub was hidden.
  Future<CaptureResult?> captureActiveWindow() async {
    return _run(() async {
      final region = await _service.activeWindowRegion();
      if (region == null || region.isEmpty) {
        throw const CaptureException(CaptureFailure.noActiveWindow);
      }
      return _record(await _service.grabRegion(region));
    });
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

  /// Shows [frozen] fullscreen and resolves with the region the user dragged,
  /// in image pixels, or `null` if they cancelled.
  Future<CaptureRegion?> _selectRegion(CaptureResult frozen) async {
    final navigator = _ref.read(navigatorKeyProvider).currentState;
    if (navigator == null) {
      throw const CaptureException(CaptureFailure.windowNotReady);
    }

    final backdrop = await _ref.read(imageDecoderProvider)(frozen.pngBytes);

    // Size and position the window first, then push the overlay against that
    // placement, then show it: the hub is never seen stretched across the
    // monitors, and the frozen frame is drawn 1:1 from the start.
    final requested = await _window.enterOverlay();
    final mapping = ValueNotifier<ScreenMapping>(
      ScreenMapping.fromPlacement(requested, imageWidth: backdrop.width),
    );

    try {
      final pending = navigator.push<CaptureRegion>(
        PageRouteBuilder<CaptureRegion>(
          opaque: true,
          transitionDuration: Duration.zero,
          reverseTransitionDuration: Duration.zero,
          pageBuilder: (context, _, _) => CaptureSelectionOverlay(
            backdrop: backdrop,
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
        imageWidth: backdrop.width,
      );

      final region = await pending;
      await _window.leaveOverlay();
      return region;
    } finally {
      mapping.dispose();
      backdrop.dispose();
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
