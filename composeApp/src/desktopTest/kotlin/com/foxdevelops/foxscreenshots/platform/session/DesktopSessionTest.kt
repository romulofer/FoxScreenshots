package com.foxdevelops.foxscreenshots.platform.session

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSessionTest {
    private fun detect(env: Map<String, String>, os: String = "Linux") = DesktopSession.detect(os, env)

    @Test
    fun `x11 session type`() = assertEquals(DesktopSession.X11, detect(mapOf("XDG_SESSION_TYPE" to "x11")))

    @Test
    fun `wayland session type`() = assertEquals(DesktopSession.Wayland, detect(mapOf("XDG_SESSION_TYPE" to "wayland")))

    @Test
    fun `a wayland display wins over an x display (XWayland)`() = assertEquals(
        DesktopSession.Wayland,
        detect(mapOf("DISPLAY" to ":0", "WAYLAND_DISPLAY" to "wayland-0")),
    )

    @Test
    fun `a bare DISPLAY is x11`() = assertEquals(DesktopSession.X11, detect(mapOf("DISPLAY" to ":0")))

    @Test
    fun `no display server at all`() = assertEquals(DesktopSession.Other, detect(emptyMap()))

    @Test
    fun `case is ignored`() = assertEquals(DesktopSession.Wayland, detect(mapOf("XDG_SESSION_TYPE" to "Wayland")))

    @Test
    fun `other operating systems are never a linux session`() {
        assertEquals(DesktopSession.Other, detect(mapOf("DISPLAY" to ":0"), os = "Windows 11"))
        assertEquals(DesktopSession.Other, detect(mapOf("DISPLAY" to ":0"), os = "Mac OS X"))
    }

    @Test
    fun `host os detection`() {
        assertEquals(HostOs.Linux, HostOs.detect("Linux"))
        assertEquals(HostOs.Windows, HostOs.detect("Windows 11"))
        assertEquals(HostOs.MacOs, HostOs.detect("Mac OS X"))
        assertEquals(HostOs.Other, HostOs.detect("FreeBSD"))
    }
}
