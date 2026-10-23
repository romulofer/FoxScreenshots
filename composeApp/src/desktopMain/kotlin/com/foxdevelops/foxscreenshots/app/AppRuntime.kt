package com.foxdevelops.foxscreenshots.app

import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.capture.UnsupportedScreenCaptureService
import com.foxdevelops.foxscreenshots.core.desktop.DependencyChecker
import com.foxdevelops.foxscreenshots.core.desktop.NoDependencyChecker
import com.foxdevelops.foxscreenshots.core.desktop.SystemIssues
import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.features.capture.CaptureController
import com.foxdevelops.foxscreenshots.features.home.SessionController
import com.foxdevelops.foxscreenshots.platform.desktop.LinuxDependencyChecker
import com.foxdevelops.foxscreenshots.platform.desktop.SystemDesktopIntegration
import com.foxdevelops.foxscreenshots.core.hotkey.GlobalHotkeys
import com.foxdevelops.foxscreenshots.platform.hotkey.NativeHookHotkeys
import com.foxdevelops.foxscreenshots.platform.hotkey.PortalGlobalHotkeys
import com.foxdevelops.foxscreenshots.platform.image.SkiaImageProcessor
import com.foxdevelops.foxscreenshots.platform.macos.MacosScreenCaptureService
import com.foxdevelops.foxscreenshots.platform.portal.PortalScreenCaptureService
import com.foxdevelops.foxscreenshots.platform.portal.XdgScreenshotPortal
import com.foxdevelops.foxscreenshots.platform.session.DesktopSession
import com.foxdevelops.foxscreenshots.platform.session.HostOs
import com.foxdevelops.foxscreenshots.platform.storage.AwtClipboardService
import com.foxdevelops.foxscreenshots.platform.storage.DesktopOutputService
import com.foxdevelops.foxscreenshots.platform.storage.FileSettingsStore
import com.foxdevelops.foxscreenshots.platform.tray.AwtTray
import com.foxdevelops.foxscreenshots.platform.tray.FallbackTray
import com.foxdevelops.foxscreenshots.platform.tray.StatusNotifierTray
import com.foxdevelops.foxscreenshots.platform.tray.TrayPresenter
import com.foxdevelops.foxscreenshots.platform.windows.WindowsScreenCaptureService
import com.foxdevelops.foxscreenshots.platform.x11.X11ScreenCaptureService
import com.foxdevelops.foxscreenshots.ui.AppContainer
import java.util.Locale
import java.util.Properties

/** Everything the running app is made of: the container the screens read, plus the windows the capture flows drive. */
class AppRuntime(
    val container: AppContainer,
    val surface: ComposeCaptureSurface,
    val dependencyChecker: DependencyChecker,
    /** Whether the capture overlay must ask for fullscreen: a Wayland compositor ignores a window's requested position and size. */
    val overlayFullscreen: Boolean = false,
    /** Whether a global Esc can cancel the timer: the system-wide hook sees keys, the Wayland portal would grab Esc from everyone. */
    val escapeCancelsTimer: Boolean = true,
) {
    companion object {
        /** Wires the real thing for this machine. */
        fun create(): AppRuntime {
            val os = HostOs.detect()
            val session = DesktopSession.detect()
            val settingsFile = FileSettingsStore.defaultFile()
            val settings = SettingsController(
                FileSettingsStore(settingsFile, importFrom = FileSettingsStore.legacyFlutterPreferences()),
            )
            val sessionList = SessionController()
            val processor = SkiaImageProcessor()
            val clipboard = AwtClipboardService()
            val output = DesktopOutputService()
            val issues = SystemIssues()
            val surface = ComposeCaptureSurface()
            val capture = CaptureController(
                service = captureService(os, session),
                surface = surface,
                processor = processor,
                session = sessionList,
                settings = settings,
                clipboard = clipboard,
                output = output,
            )
            val container = AppContainer(
                settings = settings,
                session = sessionList,
                processor = processor,
                clipboard = clipboard,
                output = output,
                issues = issues,
                capture = capture,
                desktop = SystemDesktopIntegration(trayFor(os), hotkeysFor(session, settingsFile.resolveSibling("native")), issues),
                systemLanguage = Locale.getDefault().language,
                appVersion = readVersion(),
            )
            val checker = if (os == HostOs.Linux) LinuxDependencyChecker(session) else NoDependencyChecker
            val wayland = session == DesktopSession.Wayland
            return AppRuntime(container, surface, checker, overlayFullscreen = wayland, escapeCancelsTimer = !wayland)
        }

        /** The backend for this session: GDI on Windows, CoreGraphics on macOS, Xlib on X11, the portal on Wayland. */
        fun captureService(os: HostOs, session: DesktopSession): ScreenCaptureService = when (os) {
            HostOs.Windows -> WindowsScreenCaptureService()
            HostOs.MacOs -> MacosScreenCaptureService()
            HostOs.Linux -> when (session) {
                DesktopSession.X11 -> X11ScreenCaptureService()
                DesktopSession.Wayland -> PortalScreenCaptureService(XdgScreenshotPortal())
                DesktopSession.Other -> UnsupportedScreenCaptureService(CaptureFailure.PlatformUnsupported, "no display server")
            }
            HostOs.Other -> UnsupportedScreenCaptureService(CaptureFailure.PlatformUnsupported, "no backend for this OS")
        }

        /**
         * Wayland lets no client watch the keyboard, so there the desktop is asked to bind the shortcuts
         * (portal); everywhere else the system-wide hook sees the keys itself.
         */
        private fun hotkeysFor(session: DesktopSession, libraryDir: java.nio.file.Path): GlobalHotkeys =
            if (session == DesktopSession.Wayland) PortalGlobalHotkeys() else NativeHookHotkeys(libraryDir = libraryDir)

        /** Linux desktops speak StatusNotifierItem over D-Bus, not the AWT tray protocol; everywhere else AWT works. */
        private fun trayFor(os: HostOs): TrayPresenter =
            if (os == HostOs.Linux) FallbackTray(StatusNotifierTray(), AwtTray()) else AwtTray()

        private fun readVersion(): String {
            val packaged = System.getProperty("jpackage.app-version")
            if (!packaged.isNullOrBlank()) return packaged
            val stream = AppRuntime::class.java.getResourceAsStream("/app.properties") ?: return ""
            return stream.use { Properties().apply { load(it) }.getProperty("version").orEmpty() }
        }
    }
}
