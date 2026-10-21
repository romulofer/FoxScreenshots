package com.foxdevelops.foxscreenshots.platform.tray

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** `org.kde.StatusNotifierWatcher`: the desktop shell's registry of tray items. */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

/**
 * `org.kde.StatusNotifierItem`: what the shell tells us when the icon is
 * clicked. Its properties are not declared here: the exporting object serves
 * `org.freedesktop.DBus.Properties` itself, with explicit signatures, because
 * dbus-java loses a list's element type and would send `IconPixmap` as `av`.
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    class NewToolTip(path: String) : DBusSignal(path)
    class NewIcon(path: String) : DBusSignal(path)
}

/** `com.canonical.dbusmenu`: the tray's context menu, drawn by the shell. Properties are served by the exporting object, like the item's. */
@JvmSuppressWildcards
@DBusInterfaceName("com.canonical.dbusmenu")
interface DBusMenu : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): MenuLayoutReply<UInt32, MenuLayout>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProperties>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: List<MenuEvent>): List<Int>
    fun AboutToShow(id: Int): Boolean
    fun AboutToShowGroup(ids: List<Int>): AboutToShowGroupReply<List<Int>, List<Int>>

    class LayoutUpdated(path: String, revision: UInt32, parent: Int) : DBusSignal(path, revision, parent)
}
