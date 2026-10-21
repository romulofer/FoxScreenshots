package com.foxdevelops.foxscreenshots.platform.tray

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// dbus-java reads these types by reflection and chokes on the `? extends` wildcards Kotlin adds to List/Map parameters.

/** `(iiay)`: one size of the tray icon, ARGB32 in network byte order. */
@JvmSuppressWildcards
class IconPixmap(
    @field:Position(0) val width: Int,
    @field:Position(1) val height: Int,
    @field:Position(2) val data: ByteArray,
) : Struct()

/** `(sa(iiay)ss)`: icon name, icon pixmaps, title and body of the hover tooltip. */
@JvmSuppressWildcards
class ToolTip(
    @field:Position(0) val iconName: String,
    @field:Position(1) val iconPixmaps: List<IconPixmap>,
    @field:Position(2) val title: String,
    @field:Position(3) val description: String,
) : Struct()

/** `(ia{sv}av)`: a dbusmenu node; each child is a [Variant] holding another layout. */
@JvmSuppressWildcards
class MenuLayout(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<*>>,
    @field:Position(2) val children: List<Variant<*>>,
) : Struct()

/** `(ia{sv})`: the properties of one dbusmenu item. */
@JvmSuppressWildcards
class MenuItemProperties(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<*>>,
) : Struct()

/** `(isvu)`: one event of a batched `EventGroup` call. */
@JvmSuppressWildcards
class MenuEvent(
    @field:Position(0) val id: Int,
    @field:Position(1) val eventId: String,
    @field:Position(2) val data: Variant<*>,
    @field:Position(3) val timestamp: UInt32,
) : Struct()

/**
 * Reply of `GetLayout`: the menu revision and the tree. Generic on purpose:
 * dbus-java introspects a multi-value reply from the type arguments of the
 * `Tuple` subclass, so a non-generic one is rejected as non-exportable.
 */
@JvmSuppressWildcards
class MenuLayoutReply<A, B>(
    @field:Position(0) val revision: A,
    @field:Position(1) val layout: B,
) : Tuple()

/** Reply of `AboutToShowGroup`: ids whose layout needs an update, and ids that failed. */
@JvmSuppressWildcards
class AboutToShowGroupReply<A, B>(
    @field:Position(0) val updatesNeeded: A,
    @field:Position(1) val idErrors: B,
) : Tuple()
