package com.foxdevelops.foxscreenshots.features.home

import com.foxdevelops.foxscreenshots.fakeCapture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionControllerTest {
    @Test
    fun `newest capture comes first`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        session.add(fakeCapture("b"))
        assertEquals(listOf("b", "a"), session.state.value.map { it.id })
    }

    @Test
    fun `the session is capped and drops the oldest`() {
        val session = SessionController()
        repeat(SessionController.MAX_SESSION_CAPTURES + 5) { session.add(fakeCapture("s$it")) }
        assertEquals(SessionController.MAX_SESSION_CAPTURES, session.state.value.size)
        assertEquals("s${SessionController.MAX_SESSION_CAPTURES + 4}", session.state.value.first().id)
    }

    @Test
    fun `replace swaps the capture in place`() {
        val session = SessionController()
        session.add(fakeCapture("a", width = 10))
        session.add(fakeCapture("b"))
        session.replace(fakeCapture("a", width = 99))
        assertEquals(listOf("b", "a"), session.state.value.map { it.id })
        assertEquals(99, session.state.value.last().width)
    }

    @Test
    fun `remove and clear`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        session.add(fakeCapture("b"))
        session.remove("a")
        assertEquals(listOf("b"), session.state.value.map { it.id })
        session.clear()
        assertTrue(session.state.value.isEmpty())
    }
}
