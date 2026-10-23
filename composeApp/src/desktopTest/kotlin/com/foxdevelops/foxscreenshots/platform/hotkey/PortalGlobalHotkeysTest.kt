package com.foxdevelops.foxscreenshots.platform.hotkey

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.platform.portal.GlobalShortcutsApi
import com.foxdevelops.foxscreenshots.platform.portal.PortalSessionApi
import com.foxdevelops.foxscreenshots.platform.portal.RequestApi
import com.foxdevelops.foxscreenshots.platform.portal.ShortcutSpec
import com.foxdevelops.foxscreenshots.platform.portal.toPortalTrigger
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.base.AbstractConnectionBase
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.UInt64
import org.freedesktop.dbus.types.Variant
import org.junit.Assume.assumeTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Wayland hotkeys against a stand-in `GlobalShortcuts` portal on a throwaway session bus
 * (`dbus-run-session`): no compositor is needed to check the protocol, only to check the compositor.
 */
class PortalGlobalHotkeysTest {
    private val sessions = "/org/freedesktop/portal/desktop/session"
    private var portalBus: DBusConnection? = null
    private var hotkeys: PortalGlobalHotkeys? = null

    private val calls = CopyOnWriteArrayList<String>()
    private val boundTriggers = CopyOnWriteArrayList<Map<String, String>>()
    private val closedSessions = CopyOnWriteArrayList<String>()

    @Volatile
    private var sessionPath = ""

    private fun requireIsolatedBus() =
        assumeTrue("set FOX_ISOLATED_DBUS=1 under dbus-run-session", System.getenv("FOX_ISOLATED_DBUS") == "1")

    /** The portal: answers each request on its Response signal, as the real one does. */
    private inner class FakePortal(private val answerCode: Int = 0, private val offersShortcuts: Boolean = true) :
        GlobalShortcutsApi, Properties {
        override fun isRemote() = false
        override fun getObjectPath() = "/org/freedesktop/portal/desktop"

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interfaceName: String, propertyName: String): A {
            if (!offersShortcuts) throw org.freedesktop.dbus.errors.UnknownInterface("no GlobalShortcuts here")
            return Variant(UInt32(1), "u") as A
        }

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) = Unit
        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = emptyMap()

        private fun respond(options: Map<String, Variant<*>>, results: Map<String, Variant<*>>): DBusPath {
            val sender = AbstractConnectionBase.getCallInfo().source.removePrefix(":").replace('.', '_')
            val token = options.getValue("handle_token").value as String
            val path = "/org/freedesktop/portal/desktop/request/$sender/$token"
            thread {
                Thread.sleep(20)
                portalBus!!.sendMessage(RequestApi.Response(path, UInt32(answerCode.toLong()), results))
            }
            return DBusPath(path)
        }

        override fun CreateSession(options: Map<String, Variant<*>>): DBusPath {
            calls += "CreateSession"
            val sender = AbstractConnectionBase.getCallInfo().source.removePrefix(":").replace('.', '_')
            sessionPath = "$sessions/$sender/${options.getValue("session_handle_token").value}"
            // The real portal exports the session as an object of its own: that is where Close is called.
            portalBus!!.exportObject(sessionPath, FakeSession(sessionPath))
            return respond(options, mapOf("session_handle" to Variant(sessionPath)))
        }

        override fun BindShortcuts(
            sessionHandle: DBusPath,
            shortcuts: List<ShortcutSpec>,
            parentWindow: String,
            options: Map<String, Variant<*>>,
        ): DBusPath {
            calls += "BindShortcuts"
            boundTriggers += shortcuts.associate { it.id to (it.properties.getValue("preferred_trigger").value as String) }
            return respond(options, emptyMap())
        }

    }

    private inner class FakeSession(private val path: String) : PortalSessionApi {
        override fun isRemote() = false
        override fun getObjectPath() = path

        override fun Close() {
            closedSessions += path
        }
    }

    private fun startPortal(portal: FakePortal = FakePortal()) {
        val bus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        bus.exportObject("/org/freedesktop/portal/desktop", portal)
        bus.requestBusName("org.freedesktop.portal.Desktop")
        portalBus = bus
    }

    private fun newHotkeys() = PortalGlobalHotkeys(timeoutSeconds = 10).also { hotkeys = it }

    private val statuses = CopyOnWriteArrayList<Pair<Boolean, String?>>()

    private fun PortalGlobalHotkeys.watch(): CountDownLatch {
        val first = CountDownLatch(1)
        onStatus { ok, detail ->
            statuses += ok to detail
            first.countDown()
        }
        return first
    }

    private val defaults = mapOf(
        HotkeyAction.Instant to HotkeyBinding.parse("PrintScreen"),
        HotkeyAction.Timer to HotkeyBinding.parse("F7"),
        HotkeyAction.Repeat to HotkeyBinding.parse("Ctrl+Shift+S"),
        HotkeyAction.Cancel to HotkeyBinding.parse("Escape"),
    )

    @AfterTest
    fun tearDown() {
        hotkeys?.close()
        Thread.sleep(100)
        portalBus?.close()
    }

    @Test
    fun `binds the three capture actions and never the escape key`() {
        requireIsolatedBus()
        startPortal()
        val subject = newHotkeys()
        val ready = subject.watch()
        subject.register(defaults) {}
        assertTrue(ready.await(10, TimeUnit.SECONDS))

        assertEquals(listOf(true to null), statuses.toList())
        assertEquals(listOf("CreateSession", "BindShortcuts"), calls.toList())
        assertEquals(
            mapOf("instant" to "Print", "timer" to "F7", "repeat" to "CTRL+SHIFT+s"),
            boundTriggers.single(),
            "Escape is a real grab on Wayland: binding it would take it from every other program",
        )
    }

    @Test
    fun `a pressed shortcut reaches the app as its action`() {
        requireIsolatedBus()
        startPortal()
        val subject = newHotkeys()
        val fired = CopyOnWriteArrayList<HotkeyAction>()
        val ready = subject.watch()
        subject.register(defaults) { fired += it }
        assertTrue(ready.await(10, TimeUnit.SECONDS))

        portalBus!!.sendMessage(
            GlobalShortcutsApi.Activated("/org/freedesktop/portal/desktop", DBusPath(sessionPath), "timer", UInt64(1), emptyMap()),
        )
        portalBus!!.sendMessage(
            GlobalShortcutsApi.Activated("/org/freedesktop/portal/desktop", DBusPath(sessionPath), "nonsense", UInt64(2), emptyMap()),
        )
        portalBus!!.sendMessage(
            GlobalShortcutsApi.Activated("/org/freedesktop/portal/desktop", DBusPath(sessionPath), "instant", UInt64(3), emptyMap()),
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (fired.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf(HotkeyAction.Timer, HotkeyAction.Instant), fired.toList())
    }

    @Test
    fun `a shortcut of another session is ignored`() {
        requireIsolatedBus()
        startPortal()
        val subject = newHotkeys()
        val fired = CopyOnWriteArrayList<HotkeyAction>()
        val ready = subject.watch()
        subject.register(defaults) { fired += it }
        assertTrue(ready.await(10, TimeUnit.SECONDS))

        portalBus!!.sendMessage(
            GlobalShortcutsApi.Activated("/org/freedesktop/portal/desktop", DBusPath("$sessions/other/session"), "instant", UInt64(1), emptyMap()),
        )
        Thread.sleep(500)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `rebinding reuses the session and sends the new keys`() {
        requireIsolatedBus()
        startPortal()
        val subject = newHotkeys()
        val first = subject.watch()
        subject.register(defaults) {}
        assertTrue(first.await(10, TimeUnit.SECONDS))

        val second = CountDownLatch(1)
        subject.onStatus { _, _ -> second.countDown() }
        subject.register(defaults + (HotkeyAction.Instant to HotkeyBinding.parse("F9")) - HotkeyAction.Cancel) {}
        assertTrue(second.await(10, TimeUnit.SECONDS))

        assertEquals(listOf("CreateSession", "BindShortcuts", "BindShortcuts"), calls.toList(), "one session, bound twice")
        assertEquals("F9", boundTriggers.last().getValue("instant"))
    }

    @Test
    fun `a desktop without the portal reports it instead of throwing`() {
        requireIsolatedBus()
        val subject = newHotkeys()
        val failed = subject.watch()
        subject.register(defaults) {}
        assertTrue(failed.await(15, TimeUnit.SECONDS))
        assertFalse(statuses.first().first)
        assertTrue(statuses.first().second.orEmpty().contains("no global shortcuts portal"), statuses.first().second)
    }

    @Test
    fun `a backend without the shortcuts interface is reported with its reason`() {
        requireIsolatedBus()
        startPortal(FakePortal(offersShortcuts = false))
        val subject = newHotkeys()
        val failed = subject.watch()
        subject.register(defaults) {}
        assertTrue(failed.await(15, TimeUnit.SECONDS))
        assertFalse(statuses.first().first)
        assertTrue(calls.isEmpty(), "no session is opened on a desktop that cannot bind")
    }

    @Test
    fun `a desktop that refuses the binding says so`() {
        requireIsolatedBus()
        startPortal(FakePortal(answerCode = 2))
        val subject = newHotkeys()
        val failed = subject.watch()
        subject.register(defaults) {}
        assertTrue(failed.await(15, TimeUnit.SECONDS))
        assertFalse(statuses.first().first)
        assertTrue(statuses.first().second.orEmpty().contains("refused"), statuses.first().second)
    }

    @Test
    fun `closing ends the portal session`() {
        requireIsolatedBus()
        startPortal()
        val subject = newHotkeys()
        val ready = subject.watch()
        subject.register(defaults) {}
        assertTrue(ready.await(10, TimeUnit.SECONDS))
        subject.close()
        val deadline = System.currentTimeMillis() + 5_000
        while (closedSessions.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf(sessionPath), closedSessions.toList())
    }

    // ---- no bus needed ----

    @Test
    fun `bindings become portal triggers`() {
        assertEquals("Print", HotkeyBinding.parse("PrintScreen").toPortalTrigger())
        assertEquals("F12", HotkeyBinding.parse("F12").toPortalTrigger())
        assertEquals("CTRL+SHIFT+x", HotkeyBinding.parse("Ctrl+Shift+X").toPortalTrigger())
        assertEquals("CTRL+ALT+LOGO+k", HotkeyBinding.parse("Meta+Alt+Ctrl+K").toPortalTrigger())
        assertEquals("Escape", HotkeyBinding.parse("Escape").toPortalTrigger())
    }
}
