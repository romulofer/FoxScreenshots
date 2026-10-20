package com.foxdevelops.foxscreenshots.platform.x11

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * The handful of Xlib calls the capture backend needs, bound through JNA.
 * `libX11.so.6` ships with every X11 session, so nothing is bundled.
 *
 * `Window`, `Atom` and the masks are C `unsigned long`, hence [NativeLong].
 */
internal interface Xlib : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XCloseDisplay(display: Pointer): Int
    fun XDefaultScreen(display: Pointer): Int
    fun XDefaultRootWindow(display: Pointer): NativeLong
    fun XDisplayWidth(display: Pointer, screen: Int): Int
    fun XDisplayHeight(display: Pointer, screen: Int): Int
    fun XGetImage(
        display: Pointer,
        drawable: NativeLong,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        planeMask: NativeLong,
        format: Int,
    ): Pointer?

    fun XInternAtom(display: Pointer, name: String, onlyIfExists: Int): NativeLong
    fun XGetWindowProperty(
        display: Pointer,
        window: NativeLong,
        property: NativeLong,
        offset: NativeLong,
        length: NativeLong,
        delete: Int,
        requestedType: NativeLong,
        actualType: NativeLongByReference,
        actualFormat: IntByReference,
        itemCount: NativeLongByReference,
        bytesAfter: NativeLongByReference,
        data: PointerByReference,
    ): Int

    fun XFree(data: Pointer): Int
    fun XGetInputFocus(display: Pointer, focus: NativeLongByReference, revertTo: IntByReference): Int
    fun XGetGeometry(
        display: Pointer,
        drawable: NativeLong,
        root: NativeLongByReference,
        x: IntByReference,
        y: IntByReference,
        width: IntByReference,
        height: IntByReference,
        borderWidth: IntByReference,
        depth: IntByReference,
    ): Int

    fun XTranslateCoordinates(
        display: Pointer,
        source: NativeLong,
        dest: NativeLong,
        sourceX: Int,
        sourceY: Int,
        destX: IntByReference,
        destY: IntByReference,
        child: NativeLongByReference,
    ): Int

    companion object {
        /** `ZPixmap` image format. */
        const val Z_PIXMAP = 2

        /** `AllPlanes`, i.e. `~0UL`. */
        val ALL_PLANES = NativeLong(-1)

        /** Loads the library, or throws [X11Exception] when it is not installed. */
        fun load(): Xlib {
            var failure: Throwable? = null
            for (name in listOf("X11", "libX11.so.6")) {
                try {
                    return Native.load(name, Xlib::class.java)
                } catch (e: UnsatisfiedLinkError) {
                    failure = e
                }
            }
            throw X11Exception("libX11.so.6 could not be loaded: ${failure?.message}")
        }
    }
}

internal class X11Exception(message: String) : Exception(message)

/** Mirror of Xlib's `XImage`; field order must match `X11/Xlib.h` exactly. */
@Structure.FieldOrder(
    "width", "height", "xoffset", "format", "data", "byteOrder", "bitmapUnit", "bitmapBitOrder", "bitmapPad",
    "depth", "bytesPerLine", "bitsPerPixel", "redMask", "greenMask", "blueMask", "obdata",
    "createImage", "destroyImage", "getPixel", "putPixel", "subImage", "addPixel",
)
internal class XImage(pointer: Pointer) : Structure(pointer) {
    @JvmField var width = 0
    @JvmField var height = 0
    @JvmField var xoffset = 0
    @JvmField var format = 0
    @JvmField var data: Pointer? = null
    @JvmField var byteOrder = 0
    @JvmField var bitmapUnit = 0
    @JvmField var bitmapBitOrder = 0
    @JvmField var bitmapPad = 0
    @JvmField var depth = 0
    @JvmField var bytesPerLine = 0
    @JvmField var bitsPerPixel = 0
    @JvmField var redMask = NativeLong(0)
    @JvmField var greenMask = NativeLong(0)
    @JvmField var blueMask = NativeLong(0)
    @JvmField var obdata: Pointer? = null
    @JvmField var createImage: Pointer? = null
    @JvmField var destroyImage: Pointer? = null
    @JvmField var getPixel: Pointer? = null
    @JvmField var putPixel: Pointer? = null
    @JvmField var subImage: Pointer? = null
    @JvmField var addPixel: Pointer? = null

    init {
        read()
    }

    /** `XDestroyImage` is a macro that calls this function pointer. */
    fun destroy() {
        val fn = destroyImage ?: return
        Function.getFunction(fn).invokeInt(arrayOf(pointer))
    }
}
