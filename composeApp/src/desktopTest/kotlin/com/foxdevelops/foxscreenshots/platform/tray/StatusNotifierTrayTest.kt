package com.foxdevelops.foxscreenshots.platform.tray

import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Assume.assumeTrue
import java.awt.image.BufferedImage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tray against a stand-in StatusNotifierWatcher on a throwaway session bus
 * (`dbus-run-session`). Never against the developer's own bus: the stand-in
 * would fight the real watcher for its name.
 */
class StatusNotifierTrayTest {
    private val labels = TrayAction.entries.associateWith { "label-${it.name}" }
    private val opened = CountDownLatch(1)
    private val actions = CopyOnWriteArrayList<TrayAction>()
    private val registered = CopyOnWriteArrayList<String>()
    private var watcherBus: DBusConnection? = null
    private var clientBus: DBusConnection? = null
    private var tray: StatusNotifierTray? = null

    private class FakeWatcher(private val registered: MutableList<String>) : StatusNotifierWatcher {
        override fun isRemote() = false
        override fun getObjectPath() = "/StatusNotifierWatcher"
        override fun RegisterStatusNotifierItem(service: String) {
            registered += service
        }
    }

    private fun requireIsolatedBus() =
        assumeTrue("set FOX_ISOLATED_DBUS=1 under dbus-run-session", System.getenv("FOX_ISOLATED_DBUS") == "1")

    private fun startWatcher() {
        val bus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        bus.exportObject("/StatusNotifierWatcher", FakeWatcher(registered))
        bus.requestBusName("org.kde.StatusNotifierWatcher")
        watcherBus = bus
    }

    /** A child of the layout as a client sees it: a variant holding an untyped struct, `(id, properties, children)`. */
    private class Node(val id: Int, val properties: Map<String, Variant<*>>) {
        val label get() = properties["label"]?.value as String?
    }

    @Suppress("UNCHECKED_CAST")
    private fun nodes(layout: MenuLayout): List<Node> = layout.children.map {
        val fields = it.value as Array<*>
        Node(fields[0] as Int, fields[1] as Map<String, Variant<*>>)
    }

    private fun client() = DBusConnectionBuilder.forSessionBus().withShared(false).build().also { clientBus = it }

    private fun newTray() =
        StatusNotifierTray(iconSource = { BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB) }).also { tray = it }

    private fun show(tray: StatusNotifierTray) =
        tray.show("FoxScreenShots", labels, onOpenWindow = { opened.countDown() }, onAction = { actions += it })

    @AfterTest
    fun tearDown() {
        tray?.dispose()
        clientBus?.close()
        watcherBus?.close()
    }

    @Test
    fun `registers an item with the watcher and answers its properties`() {
        requireIsolatedBus()
        startWatcher()
        assertTrue(show(newTray()))
        assertEquals(1, registered.size)
        assertTrue(registered.single().startsWith("org.kde.StatusNotifierItem-"))

        val props = client().getRemoteObject(registered.single(), "/StatusNotifierItem", Properties::class.java)
        val all = props.GetAll("org.kde.StatusNotifierItem")
        assertEquals("ApplicationStatus", all["Category"]?.value)
        assertEquals("Active", all["Status"]?.value)
        assertEquals("FoxScreenShots", all["Title"]?.value)
        assertEquals(false, all["ItemIsMenu"]?.value)
        assertEquals("/MenuBar", all["Menu"]?.value?.toString())
        assertTrue(all.containsKey("IconPixmap"))
    }

    @Test
    fun `left click opens the window`() {
        requireIsolatedBus()
        startWatcher()
        show(newTray())
        client().getRemoteObject(registered.single(), "/StatusNotifierItem", StatusNotifierItem::class.java).Activate(10, 10)
        assertTrue(opened.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `the menu lists the actions with separators and clicking one raises it`() {
        requireIsolatedBus()
        startWatcher()
        show(newTray())
        val menu = client().getRemoteObject(registered.single(), "/MenuBar", DBusMenu::class.java)

        val children = nodes(menu.GetLayout(0, -1, emptyList()).layout)
        assertEquals(
            listOf("label-Show", null, "label-Instant", "label-Timer", "label-Repeat", null, "label-Settings", "label-Quit"),
            children.map { it.label },
        )
        assertEquals("separator", children[1].properties["type"]?.value)

        val instant = children.first { it.label == "label-Instant" }
        menu.Event(instant.id, "clicked", Variant(0), UInt32(0))
        val deadline = System.currentTimeMillis() + 5_000
        while (actions.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf(TrayAction.Instant), actions.toList())
    }

    @Test
    fun `showing again refreshes the labels without a second registration`() {
        requireIsolatedBus()
        startWatcher()
        val tray = newTray()
        show(tray)
        tray.show("Novo", labels.mapValues { "novo-${it.key.name}" }, {}, {})
        assertEquals(1, registered.size)
        val menu = client().getRemoteObject(registered.single(), "/MenuBar", DBusMenu::class.java)
        assertEquals("novo-Show", nodes(menu.GetLayout(0, -1, emptyList()).layout).first().label)
    }

    @Test
    fun `without a watcher on the bus the tray reports failure`() {
        requireIsolatedBus()
        assertFalse(show(newTray()))
    }

    @Test
    fun `icon pixmaps come out as big-endian ARGB at each size`() {
        val source = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 8) for (x in 0 until 8) source.setRGB(x, y, 0x80FF0000.toInt())
        val pixmaps = StatusNotifierTray.pixmapsOf(source)
        assertEquals(listOf(22, 32, 48, 64), pixmaps.map { it.width })
        for (pixmap in pixmaps) {
            assertEquals(pixmap.width * pixmap.height * 4, pixmap.data.size)
            val middle = (pixmap.height / 2 * pixmap.width + pixmap.width / 2) * 4
            assertEquals(0x80, pixmap.data[middle].toInt() and 0xFF, "alpha first")
            assertEquals(0xFF, pixmap.data[middle + 1].toInt() and 0xFF, "then red")
        }
    }
}
