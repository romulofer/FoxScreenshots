package com.foxdevelops.foxscreenshots.platform.desktop

import com.foxdevelops.foxscreenshots.core.desktop.DependencyIssue
import com.foxdevelops.foxscreenshots.core.desktop.DependencySeverity
import com.foxdevelops.foxscreenshots.core.desktop.DesktopEvents
import com.foxdevelops.foxscreenshots.core.desktop.DesktopIntegration
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.core.desktop.SystemIssues
import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import com.foxdevelops.foxscreenshots.core.hotkey.GlobalHotkeys
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.core.settings.Settings
import com.foxdevelops.foxscreenshots.platform.hotkey.HotkeyUnavailableException
import com.foxdevelops.foxscreenshots.platform.session.DesktopSession
import com.foxdevelops.foxscreenshots.platform.tray.TrayPresenter

/** Tray icon + global hotkeys on the real desktop. Failures degrade the feature and are reported, never thrown. */
class SystemDesktopIntegration(
    private val tray: TrayPresenter,
    private val hotkeys: GlobalHotkeys,
    private val issues: SystemIssues,
    private val session: DesktopSession = DesktopSession.detect(),
) : DesktopIntegration {

    override fun attach(strings: Strings, settings: Settings, events: DesktopEvents) {
        attachTray(strings, events)
        attachHotkeys(settings, events)
    }

    private fun attachTray(strings: Strings, events: DesktopEvents) {
        val shown = tray.show(
            tooltip = strings.appTitle,
            labels = mapOf(
                TrayAction.Show to strings.showWindow,
                TrayAction.Instant to strings.captureInstant,
                TrayAction.Timer to strings.captureTimer,
                TrayAction.Repeat to strings.captureRepeat,
                TrayAction.Settings to strings.settingsTitle,
                TrayAction.Quit to strings.quit,
            ),
            onOpenWindow = events::onOpenWindow,
            onAction = events::onTrayAction,
        )
        if (shown) {
            issues.clear(SystemDependency.Tray)
        } else {
            issues.report(DependencyIssue(SystemDependency.Tray, DependencySeverity.Degraded))
        }
    }

    private fun attachHotkeys(settings: Settings, events: DesktopEvents) {
        // Wayland gives a client no way to watch keys globally: nothing to install.
        if (session == DesktopSession.Wayland) {
            issues.report(DependencyIssue(SystemDependency.Hotkeys, DependencySeverity.Degraded))
            return
        }
        val bindings = mapOf(
            HotkeyAction.Instant to HotkeyBinding.parse(settings.hotkey, HotkeyKey.PrintScreen),
            HotkeyAction.Timer to HotkeyBinding.parse(settings.timerHotkey, HotkeyKey.F7),
            HotkeyAction.Repeat to HotkeyBinding.parse(settings.repeatHotkey, HotkeyKey.F6),
        )
        try {
            hotkeys.register(bindings, events::onHotkey)
            issues.clear(SystemDependency.Hotkeys)
        } catch (_: HotkeyUnavailableException) {
            issues.report(DependencyIssue(SystemDependency.Hotkeys, DependencySeverity.Degraded))
        }
    }

    override fun close() {
        tray.dispose()
        hotkeys.close()
    }
}
