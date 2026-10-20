package com.foxdevelops.foxscreenshots.platform.tray

import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

/** The tray icon and its menu; behind an interface so the integration is testable without a tray. */
interface TrayPresenter {
    /**
     * Installs the icon, or refreshes its [tooltip] and [labels] when already
     * there. Returns `false` when the desktop has no tray or refused the icon.
     */
    fun show(
        tooltip: String,
        labels: Map<TrayAction, String>,
        onOpenWindow: () -> Unit,
        onAction: (TrayAction) -> Unit,
    ): Boolean

    fun dispose()
}

/**
 * The system-tray icon and its context menu, through AWT. Left click opens the
 * main window; right click shows the menu.
 *
 * Built straight on `java.awt.TrayIcon` rather than a toolkit wrapper because
 * the wrappers only expose the double-click action, and the hub must open on a
 * single left click.
 */
class AwtTray(private val icon: Image = defaultIcon()) : TrayPresenter {
    private var trayIcon: TrayIcon? = null

    /** Whether this desktop has a tray at all. */
    val isSupported: Boolean get() = !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()

    override fun show(
        tooltip: String,
        labels: Map<TrayAction, String>,
        onOpenWindow: () -> Unit,
        onAction: (TrayAction) -> Unit,
    ): Boolean {
        if (!isSupported) return false
        val menu = PopupMenu()
        for (action in TrayAction.entries) {
            if (action == TrayAction.Instant) menu.addSeparator()
            if (action == TrayAction.Settings) menu.addSeparator()
            menu.add(MenuItem(labels.getValue(action)).apply { addActionListener { onAction(action) } })
        }

        val existing = trayIcon
        if (existing != null) {
            existing.toolTip = tooltip
            existing.popupMenu = menu
            return true
        }
        val created = TrayIcon(icon, tooltip, menu).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) onOpenWindow()
                }
            })
        }
        return try {
            SystemTray.getSystemTray().add(created)
            trayIcon = created
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun dispose() {
        trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        trayIcon = null
    }

    companion object {
        fun defaultIcon(): Image {
            val stream = AwtTray::class.java.getResourceAsStream("/icon.png")
                ?: throw IllegalStateException("icon.png is missing from the app resources")
            return stream.use { ImageIO.read(it) }
        }
    }
}
