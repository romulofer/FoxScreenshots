package com.foxdevelops.foxscreenshots.app

import com.foxdevelops.foxscreenshots.core.desktop.DesktopIntegration
import kotlin.system.exitProcess

/**
 * Ends the process after the windows are gone.
 *
 * `exitApplication()` only closes the Compose windows. The keyboard hook and the D-Bus connections keep
 * non-daemon threads alive, so on their own they leave the JVM (and its tray icon) running with no window.
 * Cleanup runs off the caller's thread, because the caller may be the D-Bus thread that cleanup is about to
 * close, and a watchdog forces the exit if a native library hangs on the way out.
 */
object Shutdown {
    fun finish(desktop: DesktopIntegration, graceMillis: Long = GRACE_MILLIS, exit: (Int) -> Unit = ::exitProcess) {
        Thread {
            runCatching { desktop.close() }
            exit(0)
        }.apply {
            name = "shutdown"
            isDaemon = true
            start()
        }
        Thread {
            Thread.sleep(graceMillis)
            Runtime.getRuntime().halt(0)
        }.apply {
            name = "shutdown-watchdog"
            isDaemon = true
            start()
        }
    }

    private const val GRACE_MILLIS = 3_000L
}
