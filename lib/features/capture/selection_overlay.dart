import 'package:flutter/material.dart';

import '../../models/capture_region.dart';

/// Fullscreen, always-on-top frozen backdrop with a rubber-band selection
/// (SPEC §2.1). Scaffold placeholder: draws the drag rectangle and returns the
/// chosen [CaptureRegion] via [onSelected]. Magnifier + dimension badge and the
/// multi-monitor backdrop image are wired once a capture backend exists.
class SelectionOverlay extends StatefulWidget {
  const SelectionOverlay({
    required this.onSelected,
    required this.onCancel,
    this.backdrop,
    super.key,
  });

  final ValueChanged<CaptureRegion> onSelected;
  final VoidCallback onCancel;
  final ImageProvider? backdrop;

  @override
  State<SelectionOverlay> createState() => _SelectionOverlayState();
}

class _SelectionOverlayState extends State<SelectionOverlay> {
  Offset? _start;
  Offset? _current;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onPanStart: (d) => setState(() {
        _start = d.localPosition;
        _current = d.localPosition;
      }),
      onPanUpdate: (d) => setState(() => _current = d.localPosition),
      onPanEnd: (_) {
        final start = _start;
        final current = _current;
        if (start != null && current != null) {
          final region = CaptureRegion.fromPoints(start, current);
          if (!region.isEmpty) widget.onSelected(region);
        }
      },
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (widget.backdrop != null)
            Image(image: widget.backdrop!, fit: BoxFit.cover),
          const ColoredBox(color: Color(0x66000000)),
          if (_start != null && _current != null)
            CustomPaint(painter: _SelectionPainter(_start!, _current!)),
        ],
      ),
    );
  }
}

class _SelectionPainter extends CustomPainter {
  const _SelectionPainter(this.start, this.current);

  final Offset start;
  final Offset current;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Rect.fromPoints(start, current);
    final border = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5
      ..color = const Color(0xFFD9531E);
    canvas.drawRect(rect, border);
  }

  @override
  bool shouldRepaint(_SelectionPainter old) =>
      old.start != start || old.current != current;
}
