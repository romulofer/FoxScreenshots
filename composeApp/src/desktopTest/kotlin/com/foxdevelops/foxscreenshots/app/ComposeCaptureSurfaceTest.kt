package com.foxdevelops.foxscreenshots.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeCaptureSurfaceTest {
    private val surface = ComposeCaptureSurface(repaintDelay = 1.milliseconds, compositorDelay = 1.milliseconds)

    @Test
    fun `a countdown that runs out resolves true and takes its badge down`() = runTest {
        assertTrue(surface.countdown(3.seconds))
        assertNull(surface.countdown)
    }

    @Test
    fun `the badge counts down second by second`() = runTest {
        val job = async { surface.countdown(3.seconds) }
        runCurrent()
        assertEquals(3, surface.countdown)
        advanceTimeBy(1_001)
        assertEquals(2, surface.countdown)
        advanceTimeBy(1_000)
        assertEquals(1, surface.countdown)
        assertTrue(job.await())
    }

    @Test
    fun `cancelling resolves false at once and takes the badge down`() = runTest {
        val job = async { surface.countdown(10.seconds) }
        runCurrent()
        assertEquals(10, surface.countdown)
        surface.cancelCountdown()
        assertFalse(job.await())
        assertNull(surface.countdown)
    }

    @Test
    fun `cancelling with no countdown running does nothing`() = runTest {
        surface.cancelCountdown()
        assertTrue(surface.countdown(1.seconds))
    }

    @Test
    fun `a cancel does not leak into the next countdown`() = runTest {
        val first = async { surface.countdown(5.seconds) }
        runCurrent()
        surface.cancelCountdown()
        assertFalse(first.await())
        assertTrue(surface.countdown(2.seconds))
    }

    @Test
    fun `a sub-second wait can be cancelled too`() = runTest {
        val job = async { surface.countdown(500.milliseconds) }
        runCurrent()
        surface.cancelCountdown()
        assertFalse(job.await())
    }

    @Test
    fun `a toast shows until cleared, and hiding the hub clears a leftover one`() = runTest {
        surface.showToast("hello")
        assertEquals("hello", surface.toast?.text)
        val first = surface.toast
        surface.showToast("hello")
        assertTrue(surface.toast !== first, "the same text again is a new toast, so its timer restarts")

        surface.hideHub()
        assertNull(surface.toast, "a toast must never end up in the shot being taken")
    }

    @Test
    fun `clearing removes the toast`() {
        surface.showToast("x")
        surface.clearToast()
        assertNull(surface.toast)
    }
}
