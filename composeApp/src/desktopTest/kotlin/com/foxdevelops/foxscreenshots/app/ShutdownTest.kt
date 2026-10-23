package com.foxdevelops.foxscreenshots.app

import com.foxdevelops.foxscreenshots.core.desktop.DesktopEvents
import com.foxdevelops.foxscreenshots.core.desktop.DesktopIntegration
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.core.settings.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShutdownTest {
    private class Desktop(private val onClose: () -> Unit) : DesktopIntegration {
        override fun attach(strings: Strings, settings: Settings, events: DesktopEvents) = Unit
        override fun close() = onClose()
    }

    @Test
    fun `cleanup runs off the calling thread and the process then exits`() {
        val caller = Thread.currentThread()
        var closedOn: Thread? = null
        val code = AtomicInteger(-1)
        val exited = CountDownLatch(1)

        Shutdown.finish(Desktop { closedOn = Thread.currentThread() }, exit = { code.set(it); exited.countDown() })

        assertTrue(exited.await(5, TimeUnit.SECONDS))
        assertEquals(0, code.get())
        assertTrue(closedOn !== caller)
    }

    @Test
    fun `a failing cleanup still exits`() {
        val exited = CountDownLatch(1)

        Shutdown.finish(Desktop { error("dbus gone") }, exit = { exited.countDown() })

        assertTrue(exited.await(5, TimeUnit.SECONDS))
    }
}
