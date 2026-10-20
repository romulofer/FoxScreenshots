package com.foxdevelops.foxscreenshots.app

import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/**
 * Union of every display, in AWT's logical units: the area the overlay must
 * cover so a selection can cross monitors.
 */
fun virtualScreenBounds(): Rectangle {
    val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration.bounds }
    if (bounds.isEmpty()) return Rectangle(0, 0, 1280, 720)
    return bounds.reduce { acc, next -> acc.union(next) }
}
