package com.foxdevelops.foxscreenshots.platform.macos

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference

/** `CGRect`, passed and returned by value. */
@Structure.FieldOrder("x", "y", "width", "height")
internal open class CGRect(
    @JvmField var x: Double = 0.0,
    @JvmField var y: Double = 0.0,
    @JvmField var width: Double = 0.0,
    @JvmField var height: Double = 0.0,
) : Structure() {
    class ByValue(x: Double = 0.0, y: Double = 0.0, width: Double = 0.0, height: Double = 0.0) :
        CGRect(x, y, width, height), Structure.ByValue
}

/**
 * The CoreGraphics / CoreFoundation calls the capture backend needs, bound
 * through JNA. Both frameworks ship with every macOS install.
 *
 * C `bool` results are read as [Byte]: JNA would otherwise read a whole `int`
 * and trust garbage in the upper bytes of the return register.
 */
internal interface CoreGraphics : Library {
    fun CGMainDisplayID(): Int
    fun CGGetActiveDisplayList(maxDisplays: Int, displays: IntArray, count: IntByReference): Int
    fun CGDisplayBounds(display: Int): CGRect.ByValue
    fun CGDisplayCopyDisplayMode(display: Int): Pointer?
    fun CGDisplayModeGetWidth(mode: Pointer): NativeLong
    fun CGDisplayModeGetPixelWidth(mode: Pointer): NativeLong
    fun CGDisplayModeRelease(mode: Pointer)
    fun CGWindowListCreateImage(bounds: CGRect.ByValue, listOption: Int, windowId: Int, imageOption: Int): Pointer?
    fun CGImageGetWidth(image: Pointer): NativeLong
    fun CGImageGetHeight(image: Pointer): NativeLong
    fun CGImageGetBytesPerRow(image: Pointer): NativeLong
    fun CGImageGetBitsPerPixel(image: Pointer): NativeLong
    fun CGImageGetDataProvider(image: Pointer): Pointer?
    fun CGImageCreateWithImageInRect(image: Pointer, rect: CGRect.ByValue): Pointer?
    fun CGImageRelease(image: Pointer)
    fun CGDataProviderCopyData(provider: Pointer): Pointer?
    fun CGWindowListCopyWindowInfo(option: Int, windowId: Int): Pointer?
    fun CGRectMakeWithDictionaryRepresentation(dict: Pointer, rect: CGRect): Byte
    fun CGPreflightScreenCaptureAccess(): Byte
    fun CGRequestScreenCaptureAccess(): Byte

    companion object {
        const val WINDOW_LIST_ON_SCREEN_ONLY = 1
        const val WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS = 16
        const val NULL_WINDOW_ID = 0
        const val WINDOW_IMAGE_DEFAULT = 0

        private const val PATH = "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics"

        fun load(): CoreGraphics = Native.load(PATH, CoreGraphics::class.java)

        /** A `CFStringRef` constant such as `kCGWindowBounds`: the library exports the variable, whose value is the key. */
        fun key(name: String): Pointer =
            NativeLibrary.getInstance(PATH).getGlobalVariableAddress(name).getPointer(0)
    }
}

internal interface CoreFoundation : Library {
    fun CFRelease(ref: Pointer)
    fun CFDataGetBytePtr(data: Pointer): Pointer?
    fun CFDataGetLength(data: Pointer): NativeLong
    fun CFArrayGetCount(array: Pointer): NativeLong
    fun CFArrayGetValueAtIndex(array: Pointer, index: NativeLong): Pointer?
    fun CFDictionaryGetValue(dict: Pointer, key: Pointer): Pointer?
    fun CFNumberGetValue(number: Pointer, type: Int, out: IntByReference): Byte

    companion object {
        const val NUMBER_INT_TYPE = 9

        fun load(): CoreFoundation =
            Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation::class.java)
    }
}
