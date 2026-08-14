import 'dart:io';
import 'dart:typed_data';

import 'package:dbus/dbus.dart';

/// Why a portal request could not be served.
enum PortalFailure {
  /// The user dismissed the desktop's permission dialog, or the compositor
  /// refused the request.
  denied,

  /// No portal is running, or it failed mid-request.
  unavailable,
}

/// A portal request that did not produce a screenshot.
class PortalException implements Exception {
  const PortalException(this.failure, {this.details});

  final PortalFailure failure;

  /// Developer-facing context; never carries screen content (SPEC §7).
  final String? details;

  @override
  String toString() =>
      'PortalException(${failure.name})${details == null ? '' : ': $details'}';
}

/// Screen capture through `xdg-desktop-portal` — the only way to read the
/// screen under Wayland, where no client may grab another's pixels (SPEC §2.1).
///
/// Behind an interface so the capture backend can be unit-tested without a
/// session bus.
abstract interface class ScreenshotPortal {
  /// Asks the desktop for a screenshot of everything and returns the PNG bytes.
  ///
  /// The desktop decides how the request is confirmed: most compositors show a
  /// permission dialog the first time and remember the answer.
  Future<Uint8List> capture();
}

/// `org.freedesktop.portal.Screenshot` over the session bus.
///
/// The portal answers asynchronously: the method call only hands back the
/// object path of a request, and the picture arrives later on that request's
/// `Response` signal. The subscription is therefore opened *before* the call,
/// so a fast portal cannot answer into the void.
class XdgScreenshotPortal implements ScreenshotPortal {
  XdgScreenshotPortal({
    DBusClient? bus,
    this.timeout = const Duration(minutes: 2),
  }) : _providedBus = bus;

  static const String _portalName = 'org.freedesktop.portal.Desktop';
  static const String _portalPath = '/org/freedesktop/portal/desktop';
  static const String _screenshotInterface =
      'org.freedesktop.portal.Screenshot';
  static const String _requestInterface = 'org.freedesktop.portal.Request';

  /// The `Response` codes the portal documents.
  static const int _responseSuccess = 0;
  static const int _responseCancelled = 1;

  final DBusClient? _providedBus;

  /// How long to wait for the user to answer the desktop's dialog before
  /// giving up. Generous: the dialog is modal and the user may be reading it.
  final Duration timeout;

  @override
  Future<Uint8List> capture() async {
    final bus = _providedBus ?? DBusClient.session();
    try {
      return await _capture(bus);
    } on DBusServiceUnknownException catch (e) {
      throw PortalException(PortalFailure.unavailable, details: e.toString());
    } on DBusMethodResponseException catch (e) {
      throw PortalException(PortalFailure.unavailable, details: e.toString());
    } on SocketException catch (e) {
      throw PortalException(PortalFailure.unavailable, details: e.message);
    } finally {
      if (_providedBus == null) await bus.close();
    }
  }

  Future<Uint8List> _capture(DBusClient bus) async {
    final portal = DBusRemoteObject(
      bus,
      name: _portalName,
      path: DBusObjectPath(_portalPath),
    );

    // The portal derives the request's object path from the caller's bus name
    // and this token, so the reply can be subscribed to before it exists.
    final token = 'foxscreenshots_${DateTime.now().microsecondsSinceEpoch}';
    final request = DBusRemoteObject(
      bus,
      name: _portalName,
      path: DBusObjectPath('$_portalPath/request/${_busPrefix(bus)}/$token'),
    );
    final responses = DBusRemoteObjectSignalStream(
      object: request,
      interface: _requestInterface,
      name: 'Response',
    );
    final response = responses.first;

    await portal.callMethod(_screenshotInterface, 'Screenshot', [
      // No parent window: the app hides itself before capturing, so there is
      // nothing for the dialog to sit on top of.
      const DBusString(''),
      DBusDict.stringVariant({
        'handle_token': DBusString(token),
        'interactive': const DBusBoolean(false),
      }),
    ], replySignature: DBusSignature('o'));

    final signal = await response.timeout(
      timeout,
      onTimeout: () => throw const PortalException(
        PortalFailure.denied,
        details: 'the desktop never answered the screenshot request',
      ),
    );
    return _readResult(signal);
  }

  /// Turns the reply into pixels, and takes the file the portal wrote with it:
  /// leaving screenshots lying around in a shared directory would outlive the
  /// session they were taken in (SPEC §7).
  Future<Uint8List> _readResult(DBusSignal signal) async {
    final code = signal.values[0].asUint32();
    if (code == _responseCancelled) {
      throw const PortalException(PortalFailure.denied);
    }
    if (code != _responseSuccess) {
      throw PortalException(
        PortalFailure.unavailable,
        details: 'portal response $code',
      );
    }

    final uri = signal.values[1].asStringVariantDict()['uri']?.asString();
    if (uri == null) {
      throw const PortalException(
        PortalFailure.unavailable,
        details: 'portal reported success without an image',
      );
    }

    final file = File.fromUri(Uri.parse(uri));
    try {
      return await file.readAsBytes();
    } on FileSystemException catch (e) {
      throw PortalException(PortalFailure.unavailable, details: e.message);
    } finally {
      try {
        await file.delete();
      } on FileSystemException {
        // Not ours to insist on; the capture already succeeded.
      }
    }
  }

  /// The caller's unique bus name in the form the portal builds paths from:
  /// `:1.42` becomes `1_42`.
  String _busPrefix(DBusClient bus) =>
      bus.uniqueName.replaceFirst(':', '').replaceAll('.', '_');
}
