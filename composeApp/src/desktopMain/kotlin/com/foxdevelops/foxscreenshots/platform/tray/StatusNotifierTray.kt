package com.foxdevelops.foxscreenshots.platform.tray

import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The tray icon through the StatusNotifierItem D-Bus protocol: what Cinnamon,
 * KDE and GNOME (with the AppIndicator extension) actually implement. The AWT
 * tray speaks the legacy XEmbed protocol, which those desktops no longer offer,
 * so `SystemTray.isSupported()` is false there and no icon would ever show.
 *
 * Left click (`Activate`) opens the main window; the context menu is exported as
 * a `com.canonical.dbusmenu` that the shell draws.
 */
class StatusNotifierTray(
    private val connect: () -> DBusConnection = { DBusConnectionBuilder.forSessionBus().build() },
    private val iconSource: () -> BufferedImage = ::loadIcon,
) : TrayPresenter {

    private class State(
        val tooltip: String,
        val labels: Map<TrayAction, String>,
        val onOpenWindow: () -> Unit,
        val onAction: (TrayAction) -> Unit,
    )

    @Volatile
    private var state: State? = null
    private var bus: DBusConnection? = null
    private var serviceName: String? = null
    private var revision = 1L

    private val item = Item()
    private val menu = Menu()

    @Synchronized
    override fun show(
        tooltip: String,
        labels: Map<TrayAction, String>,
        onOpenWindow: () -> Unit,
        onAction: (TrayAction) -> Unit,
    ): Boolean {
        state = State(tooltip, labels, onOpenWindow, onAction)
        val connection = bus
        if (connection != null) {
            revision++
            runCatching {
                connection.sendMessage(StatusNotifierItem.NewToolTip(ITEM_PATH))
                connection.sendMessage(DBusMenu.LayoutUpdated(MENU_PATH, UInt32(revision), 0))
            }
            return true
        }
        return try {
            install()
            true
        } catch (e: Exception) {
            // No watcher on the bus (or no bus at all): this desktop has no StatusNotifier tray.
            System.err.println("[foxscreenshots] StatusNotifier tray unavailable: ${e.message}")
            teardown()
            false
        }
    }

    private fun install() {
        val connection = connect()
        bus = connection
        val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
        serviceName = name
        connection.exportObject(ITEM_PATH, item)
        connection.exportObject(MENU_PATH, menu)
        connection.requestBusName(name)
        register(connection)
        // A shell restart drops every icon: register again when a new watcher appears.
        connection.addSigHandler(DBus.NameOwnerChanged::class.java) { change ->
            if (change.name == WATCHER_NAME && change.newOwner.isNotEmpty()) runCatching { register(connection) }
        }
    }

    private fun register(connection: DBusConnection) {
        val name = serviceName ?: return
        connection.getRemoteObject(WATCHER_NAME, WATCHER_PATH, StatusNotifierWatcher::class.java)
            .RegisterStatusNotifierItem(name)
    }

    @Synchronized
    override fun dispose() {
        teardown()
        state = null
    }

    private fun teardown() {
        val connection = bus ?: return
        bus = null
        serviceName = null
        runCatching { connection.close() }
    }

    private inner class Item : StatusNotifierItem, Properties {
        @Volatile
        private var cachedPixmaps: List<IconPixmap>? = null

        override fun isRemote() = false
        override fun getObjectPath() = ITEM_PATH

        private fun pixmaps(): List<IconPixmap> =
            cachedPixmaps ?: runCatching { pixmapsOf(iconSource()) }.getOrDefault(emptyList()).also { cachedPixmaps = it }

        /** Every property with its wire signature spelled out. */
        private fun properties(): Map<String, Variant<*>> {
            val title = state?.tooltip.orEmpty()
            return mapOf(
                "Category" to Variant("ApplicationStatus", "s"),
                "Id" to Variant("foxscreenshots", "s"),
                "Title" to Variant(title, "s"),
                "Status" to Variant("Active", "s"),
                "WindowId" to Variant(0, "i"),
                "IconName" to Variant("", "s"),
                "IconPixmap" to Variant(pixmaps(), "a(iiay)"),
                "OverlayIconName" to Variant("", "s"),
                "AttentionIconName" to Variant("", "s"),
                "ToolTip" to Variant(ToolTip("", emptyList(), title, ""), "(sa(iiay)ss)"),
                "ItemIsMenu" to Variant(false, "b"),
                "Menu" to Variant(DBusPath(MENU_PATH), "o"),
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interfaceName: String, propertyName: String): A =
            (properties()[propertyName] ?: throw UnknownProperty("no property $propertyName")) as A

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) =
            throw PropertyReadOnly("$propertyName is read-only")

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()

        override fun Activate(x: Int, y: Int) {
            state?.onOpenWindow?.invoke()
        }

        override fun SecondaryActivate(x: Int, y: Int) = Unit
        override fun ContextMenu(x: Int, y: Int) = Unit
        override fun Scroll(delta: Int, orientation: String) = Unit
    }

    private inner class Menu : DBusMenu, Properties {
        /** Menu entries in display order; `null` is a separator. Ids are positions, so they are stable across refreshes. */
        private val entries: List<TrayAction?> = buildList {
            for (action in TrayAction.entries) {
                if (action == TrayAction.Instant || action == TrayAction.Settings) add(null)
                add(action)
            }
        }

        override fun isRemote() = false
        override fun getObjectPath() = MENU_PATH

        private fun properties(): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3), "u"),
            "TextDirection" to Variant("ltr", "s"),
            "Status" to Variant("normal", "s"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interfaceName: String, propertyName: String): A =
            (properties()[propertyName] ?: throw UnknownProperty("no property $propertyName")) as A

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) =
            throw PropertyReadOnly("$propertyName is read-only")

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()

        private fun propertiesOf(index: Int): Map<String, Variant<*>> {
            val action = entries[index]
            return if (action == null) {
                mapOf("type" to Variant("separator"))
            } else {
                mapOf(
                    "label" to Variant(state?.labels?.get(action).orEmpty()),
                    "enabled" to Variant(true),
                    "visible" to Variant(true),
                )
            }
        }

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): MenuLayoutReply<UInt32, MenuLayout> {
            val children = if (parentId == 0 && recursionDepth != 0) {
                entries.indices.map { Variant(MenuLayout(it + 1, propertiesOf(it), emptyList())) }
            } else {
                emptyList()
            }
            val root = if (parentId == 0) {
                MenuLayout(0, mapOf("children-display" to Variant("submenu")), children)
            } else {
                entries.getOrNull(parentId - 1)?.let { MenuLayout(parentId, propertiesOf(parentId - 1), emptyList()) }
                    ?: MenuLayout(parentId, emptyMap(), emptyList())
            }
            return MenuLayoutReply(UInt32(revision), root)
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProperties> =
            (ids.ifEmpty { entries.indices.map { it + 1 } })
                .filter { it in 1..entries.size }
                .map { MenuItemProperties(it, propertiesOf(it - 1)) }

        override fun GetProperty(id: Int, name: String): Variant<*> =
            (if (id in 1..entries.size) propertiesOf(id - 1)[name] else null) ?: Variant("")

        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId != "clicked") return
            val action = entries.getOrNull(id - 1) ?: return
            state?.onAction?.invoke(action)
        }

        override fun EventGroup(events: List<MenuEvent>): List<Int> {
            for (event in events) Event(event.id, event.eventId, event.data, event.timestamp)
            return emptyList()
        }

        override fun AboutToShow(id: Int) = false

        override fun AboutToShowGroup(ids: List<Int>) = AboutToShowGroupReply<List<Int>, List<Int>>(emptyList(), emptyList())
    }

    companion object {
        private const val WATCHER_NAME = "org.kde.StatusNotifierWatcher"
        private const val WATCHER_PATH = "/StatusNotifierWatcher"
        private const val ITEM_PATH = "/StatusNotifierItem"
        private const val MENU_PATH = "/MenuBar"
        private val ICON_SIZES = listOf(22, 32, 48, 64)

        fun loadIcon(): BufferedImage {
            val stream = StatusNotifierTray::class.java.getResourceAsStream("/icon.png")
                ?: throw IllegalStateException("icon.png is missing from the app resources")
            return stream.use { ImageIO.read(it) }
        }

        /** The icon at each tray size as ARGB32, big-endian, as the protocol wants it. */
        internal fun pixmapsOf(source: BufferedImage): List<IconPixmap> = ICON_SIZES.map { size ->
            val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            scaled.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(source, 0, 0, size, size, null)
                dispose()
            }
            val bytes = ByteArray(size * size * 4)
            for (y in 0 until size) for (x in 0 until size) {
                val argb = scaled.getRGB(x, y)
                val at = (y * size + x) * 4
                bytes[at] = (argb ushr 24).toByte()
                bytes[at + 1] = (argb ushr 16).toByte()
                bytes[at + 2] = (argb ushr 8).toByte()
                bytes[at + 3] = argb.toByte()
            }
            IconPixmap(size, size, bytes)
        }
    }
}

/**
 * Tries each presenter in order and keeps the first that works: the D-Bus
 * StatusNotifier tray where the desktop has one, the AWT tray elsewhere.
 */
class FallbackTray(private vararg val presenters: TrayPresenter) : TrayPresenter {
    private var active: TrayPresenter? = null

    @Synchronized
    override fun show(
        tooltip: String,
        labels: Map<TrayAction, String>,
        onOpenWindow: () -> Unit,
        onAction: (TrayAction) -> Unit,
    ): Boolean {
        active?.let { return it.show(tooltip, labels, onOpenWindow, onAction) }
        for (presenter in presenters) {
            if (presenter.show(tooltip, labels, onOpenWindow, onAction)) {
                active = presenter
                return true
            }
        }
        return false
    }

    @Synchronized
    override fun dispose() {
        presenters.forEach { it.dispose() }
        active = null
    }
}
