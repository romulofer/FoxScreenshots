import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// The timer-mode countdown badge (SPEC §2.1).
///
/// Shown in a small always-on-top, click-through window while the shot is
/// pending, so the user knows when the frame fires and can still open menus and
/// tooltips underneath. Removed right before the grab, so it never lands in the
/// screenshot.
class CountdownOverlay extends StatelessWidget {
  const CountdownOverlay({required this.remaining, super.key});

  /// Seconds left before the shot; the badge rebuilds as it ticks down.
  final ValueListenable<int> remaining;

  @override
  Widget build(BuildContext context) {
    return Material(
      type: MaterialType.transparency,
      child: Center(
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: const Color(0xE6000000),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.camera_alt_outlined,
                  color: Colors.white,
                  size: 32,
                ),
                const SizedBox(width: 16),
                ValueListenableBuilder<int>(
                  valueListenable: remaining,
                  builder: (context, value, _) => Text(
                    '$value',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 44,
                      fontWeight: FontWeight.w700,
                      fontFeatures: [FontFeature.tabularFigures()],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
