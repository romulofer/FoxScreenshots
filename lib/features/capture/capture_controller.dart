import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../core/image/png_codec.dart';
import '../../core/navigation/app_navigator.dart';
import '../../core/storage/clipboard_service.dart';
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
      final frame = _service.grabFullVirtualScreen();
      final region = await _selectRegion(frame);
      if (region == null) return null;

      // Already resolved by the time `_selectRegion` returns a region —
      // awaiting it again just reads the cached value.
      final frozen = await frame;
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

  /// Timer mode: frame a region, wait, then grab that region live so
  /// menus/tooltips opened during the delay appear in the shot (SPEC §2.1).
  ///
  /// The framing step runs over a frozen snapshot rather than a see-through
  /// window: a transparent overlay needs a compositing window manager, and on a
  /// plain X11 session it comes out solid black, leaving the user to draw a
  /// rectangle over nothing. The snapshot is only a backdrop — the pixels that
  /// end up in the file are grabbed after the delay.
  Future<CaptureResult?> captureWithTimer({Duration? delay}) => _guarded(
    () async {
      return _run(() async {
        final region = await _selectRegion(_service.grabFullVirtualScreen());
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

  /// Shows a fullscreen selection overlay over the frame [frame] resolves to,
  /// and resolves with the region the user dragged (screen pixels), or `null`
  /// if they cancelled.
  ///
  /// [frame] is a grab already in flight rather than an already-decoded one:
  /// resizing the (still-hidden) hub window for the overlay does not depend on
  /// the grabbed pixels at all, so it starts immediately instead of waiting for
  /// the grab to finish first — the two used to run back to back, stacking
  /// their latencies on every single capture and making the hotkey feel like
  /// it had hung.
  Future<CaptureRegion?> _selectRegion(Future<CaptureResult> frame) async {
    final navigator = _ref.read(navigatorKeyProvider).currentState;
    if (navigator == null) {
      // Nobody will await this grab now; silence its error instead of letting
      // an unrelated display hiccup surface as an unhandled Future exception.
      frame.ignore();
      throw const CaptureException(CaptureFailure.windowNotReady);
    }

    // Size and position the window first, then push the overlay against that
    // placement, then show it: the hub is never seen stretched across the
    // monitors, and a frozen frame is drawn 1:1 from the start. Kicked off
    // before the grab resolves — the resize does not need its pixels.
    final overlayFuture = _window.enterOverlay();
    final CaptureResult frozen;
    try {
      frozen = await frame;
    } catch (_) {
      // The resize above may have gone through even though the grab racing it
      // failed; undo it the same way a cancelled selection would, so the hub
      // does not come back with the overlay's chrome (hidden title bar, fixed
      // size, black background) still applied.
      try {
        await overlayFuture;
      } catch (_) {
        // Already failing for the grab's reason; this one adds nothing.
      }
      try {
        await _window.leaveOverlay();
      } catch (_) {
        // Best effort: the grab's exception is what the caller needs to see.
      }
      rethrow;
    }

    final backdrop = await _ref.read(imageDecoderProvider)(frozen.pngBytes);
    try {
      final requested = await overlayFuture;
      final mapping = ValueNotifier<ScreenMapping>(
        ScreenMapping.fromPlacement(
          requested,
          imageWidth: frozen.width,
          imageHeight: frozen.height,
        ),
      );

      try {
        final pending = navigator.push<CaptureRegion>(
          PageRouteBuilder<CaptureRegion>(
            transitionDuration: Duration.zero,
            reverseTransitionDuration: Duration.zero,
            pageBuilder: (context, _, _) => CaptureSelectionOverlay(
              backdrop: backdrop,
              screenWidth: frozen.width,
              screenHeight: frozen.height,
              mapping: mapping,
              onSelected: (region) => Navigator.of(context).pop(region),
              onCancel: () => Navigator.of(context).pop(),
            ),
          ),
        );

        // A window manager may clamp the overlay (to one monitor, or away from
        // a panel); re-map against what it actually granted.
        final granted = await _window.revealOverlay();
        mapping.value = ScreenMapping.fromPlacement(
          granted,
          imageWidth: frozen.width,
          imageHeight: frozen.height,
        );
        // The route's first frame is built while the window is still hidden,
        // and on Linux a hidden window's surface is not reliably composited.
        // When the window manager grants exactly the requested placement,
        // `mapping.value` above is a no-op (ValueNotifier skips
        // notifyListeners on an equal value), so nothing else forces a
        // repaint once the window turns visible — leaving the overlay blank
        // (no crosshair, no dim) even though dragging still works. Force a
        // frame unconditionally.
        WidgetsBinding.instance.scheduleFrame();

        return await pending;
      } finally {
        try {
          await _window.leaveOverlay();
        } catch (_) {
          // Still tear down mapping/image even if the window restore fails.
        }
        mapping.dispose();
      }
    } finally {
      backdrop.dispose();
    }
  }

  /// Files the capture in the session and puts it on the clipboard.
  ///
  /// Copying straight away is what makes the hotkey useful on its own: hit
  /// PrintScreen, then paste. A clipboard-less session (no portal, headless)
  /// must not lose the capture, so the failure is swallowed — the shot is still
  /// in the gallery, where Copy can be retried.
  Future<CaptureResult> _record(CaptureResult result) async {
    _ref.read(sessionControllerProvider.notifier).add(result);
    try {
      await _ref.read(clipboardServiceProvider).copyPng(result.pngBytes);
    } catch (_) {
      // Nothing to report from here: no BuildContext, and the capture is safe.
    }
    return result;
  }

  String _newId() => 'shot-${DateTime.now().microsecondsSinceEpoch}';
}

final captureControllerProvider = Provider<CaptureController>((ref) {
  return CaptureController(ref);
});
