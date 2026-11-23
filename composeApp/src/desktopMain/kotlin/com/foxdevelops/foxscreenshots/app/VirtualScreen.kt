package com.foxdevelops.foxscreenshots.app

import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Size assumed when the system reports no screens at all (headless). */
private const val FALLBACK_WIDTH = 1280
private const val FALLBACK_HEIGHT = 720

/**
 * Union of every display, in AWT's logical units: the area the overlay must
 * cover so a selection can cross monitors.
 */
fun virtualScreenBounds(): Rectangle {
    val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration.bounds }
    if (bounds.isEmpty()) return Rectangle(0, 0, FALLBACK_WIDTH, FALLBACK_HEIGHT)
    return bounds.reduce { acc, next -> acc.union(next) }
}
