package com.foxdevelops.foxscreenshots.platform.session

/**
 * Which display server the app is talking to. Nearly everything platform-specific
 * hangs off this: how pixels are grabbed, whether the app may place its own
 * overlay window, and whether global hotkeys can be registered at all.
 */
enum class DesktopSession {
    /** X11 (or XWayland with a real X root window). */
    X11,

    /** Wayland, where clients cannot read the screen and everything goes through xdg-desktop-portal. */
    Wayland,

    /** Windows, macOS, or a Linux session with no display server at all. */
    Other;

    companion object {
        /**
         * Reads the session type from the environment. `XDG_SESSION_TYPE` is the
         * authority; `WAYLAND_DISPLAY` catches sessions that leave it unset. A JVM
         * app can itself run on XWayland inside a Wayland session, so the check
         * errs towards Wayland — the more restricted path, which never grabs a
         * black frame from an X root window that is not the real desktop.
         */
        fun detect(
            osName: String = System.getProperty("os.name").orEmpty(),
            env: Map<String, String> = System.getenv(),
        ): DesktopSession {
            if (!osName.lowercase().contains("linux")) return Other
            val type = env["XDG_SESSION_TYPE"]?.lowercase()
            if (type == "wayland" || !env["WAYLAND_DISPLAY"].isNullOrEmpty()) return Wayland
            if (type == "x11" || !env["DISPLAY"].isNullOrEmpty()) return X11
            return Other
        }
    }
}

/** The operating systems the app ships for. */
enum class HostOs {
    Linux, Windows, MacOs, Other;

    companion object {
        fun detect(osName: String = System.getProperty("os.name").orEmpty()): HostOs {
            val name = osName.lowercase()
            return when {
                name.contains("linux") -> Linux
                name.contains("windows") -> Windows
                name.contains("mac") || name.contains("darwin") -> MacOs
                else -> Other
            }
        }
    }
}
