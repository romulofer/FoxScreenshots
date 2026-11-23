package com.foxdevelops.foxscreenshots.platform

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.junit.Assume.assumeTrue

/** Names the live D-Bus tests stand in for; if the bus can start the real owner on demand, the tests would race it. */
private val STAND_IN_NAMES = listOf("org.freedesktop.portal.Desktop", "org.kde.StatusNotifierWatcher")

/**
 * Skips (never fails) a live D-Bus test unless it runs on a throwaway session bus: `FOX_ISOLATED_DBUS=1`
 * **and** a bus with no activatable service for the names the tests fake. With the stock `dbus-run-session`
 * configuration the first call to the portal starts the machine's real `xdg-desktop-portal`, which then owns
 * the name and breaks the "no portal" tests. The right bus is `composeApp/dbus-test-session.conf`.
 */
fun requireIsolatedBus() {
    assumeTrue("set FOX_ISOLATED_DBUS=1 under dbus-run-session", System.getenv("FOX_ISOLATED_DBUS") == "1")
    val activatable = DBusConnectionBuilder.forSessionBus().withShared(false).build().use { bus ->
        bus.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java)
            .ListActivatableNames().toSet()
    }
    val real = STAND_IN_NAMES.filter { it in activatable }
    assumeTrue(
        "the bus can start the real $real on demand; run with --config-file composeApp/dbus-test-session.conf",
        real.isEmpty(),
    )
}
