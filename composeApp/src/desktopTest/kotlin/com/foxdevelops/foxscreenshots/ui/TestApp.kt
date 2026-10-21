package com.foxdevelops.foxscreenshots.ui

import com.foxdevelops.foxscreenshots.FakeCaptureService
import com.foxdevelops.foxscreenshots.FakeClipboard
import com.foxdevelops.foxscreenshots.FakeOutput
import com.foxdevelops.foxscreenshots.FakeSurface
import com.foxdevelops.foxscreenshots.core.desktop.NoDesktopIntegration
import com.foxdevelops.foxscreenshots.core.desktop.SystemIssues
import com.foxdevelops.foxscreenshots.core.settings.MemorySettingsStore
import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.features.capture.CaptureController
import com.foxdevelops.foxscreenshots.features.home.SessionController
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.platform.image.Png
import com.foxdevelops.foxscreenshots.platform.image.SkiaImageProcessor

/** A real PNG (so the real Skia processor can decode it) filled with one color. */
fun solidPng(width: Int, height: Int, blue: Int = 0xC0, green: Int = 0xC0, red: Int = 0xC0): ByteArray {
    val bytes = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
        bytes[i * 4] = blue.toByte()
        bytes[i * 4 + 1] = green.toByte()
        bytes[i * 4 + 2] = red.toByte()
    }
    return Png.encodeOpaque(bytes, width, height)
}

fun testCapture(id: String = "shot-1", width: Int = 200, height: Int = 120) =
    CaptureResult(id, solidPng(width, height), width, height, takenAtMillis = 0)

/** The real screens over fakes for the operating system's edges. */
class TestApp(language: String = "pt") {
    val settings = SettingsController(MemorySettingsStore())
    val session = SessionController()
    val clipboard = FakeClipboard()
    val output = FakeOutput()
    val surface = FakeSurface()
    val issues = SystemIssues()
    val processor = SkiaImageProcessor()
    val service = FakeCaptureService(png = solidPng(200, 100))

    val container = AppContainer(
        settings = settings,
        session = session,
        processor = processor,
        clipboard = clipboard,
        output = output,
        issues = issues,
        capture = CaptureController(service, surface, processor, session, settings, clipboard, output),
        desktop = NoDesktopIntegration,
        systemLanguage = language,
        appVersion = "9.9.9",
    )
}
