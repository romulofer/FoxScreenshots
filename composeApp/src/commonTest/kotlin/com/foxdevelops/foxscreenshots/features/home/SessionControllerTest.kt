package com.foxdevelops.foxscreenshots.features.home

import com.foxdevelops.foxscreenshots.fakeCapture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

    @Test
    fun `remove hands back what is needed to restore the capture in place`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        session.add(fakeCapture("b"))
        session.add(fakeCapture("c"))
        val removed = session.remove("b")!!
        assertEquals(listOf("c", "a"), session.state.value.map { it.id })
        session.restore(removed)
        assertEquals(listOf("c", "b", "a"), session.state.value.map { it.id })
    }

    @Test
    fun `removing something that is not there is a no-op`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        assertNull(session.remove("zzz"))
        assertEquals(1, session.state.value.size)
    }

    @Test
    fun `restoring twice does not duplicate`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        val removed = session.remove("a")!!
        session.restore(removed)
        session.restore(removed)
        assertEquals(1, session.state.value.size)
    }

    @Test
    fun `saved paths follow their captures`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        session.markSaved("a", "/shots/a.png")
        session.markSaved("ghost", "/shots/ghost.png")
        assertEquals(mapOf("a" to "/shots/a.png"), session.savedPaths.value)

        val removed = session.remove("a")!!
        assertTrue(session.savedPaths.value.isEmpty())
        session.restore(removed)
        assertEquals("/shots/a.png", session.savedPaths.value["a"])
    }

    @Test
    fun `an evicted capture loses its saved path and the session knows it is full`() {
        val session = SessionController()
        session.add(fakeCapture("old"))
        session.markSaved("old", "/shots/old.png")
        repeat(SessionController.MAX_SESSION_CAPTURES) {
            assertFalse(session.isFull && it == 0)
            session.add(fakeCapture("n$it"))
        }
        assertTrue(session.isFull)
        assertNull(session.savedPaths.value["old"])
    }

    @Test
    fun `clear forgets the paths too`() {
        val session = SessionController()
        session.add(fakeCapture("a"))
        session.markSaved("a", "/x.png")
        session.clear()
        assertTrue(session.savedPaths.value.isEmpty())
    }
}
