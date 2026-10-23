package com.foxdevelops.foxscreenshots.platform.portal

import com.foxdevelops.foxscreenshots.platform.image.Png
import kotlinx.coroutines.runBlocking
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.base.AbstractConnectionBase
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The Wayland screenshot request against a stand-in `org.freedesktop.portal.Screenshot` on a throwaway
 * session bus (`dbus-run-session`). This is the whole protocol dance — subscribe, call, wait for `Response`,
 * read the file, delete it — that no other test reaches.
 */
class XdgScreenshotPortalTest {
    private var portalBus: DBusConnection? = null
    private val options = CopyOnWriteArrayList<Map<String, Any?>>()
    private val files = CopyOnWriteArrayList<File>()

    private fun requireIsolatedBus() =
        assumeTrue("set FOX_ISOLATED_DBUS=1 under dbus-run-session", System.getenv("FOX_ISOLATED_DBUS") == "1")

    private inner class FakePortal(
        private val code: Int = 0,
        private val withUri: Boolean = true,
    ) : ScreenshotApi {
        override fun isRemote() = false
        override fun getObjectPath() = "/org/freedesktop/portal/desktop"

        override fun Screenshot(parentWindow: String, options: Map<String, Variant<*>>): DBusPath {
            this@XdgScreenshotPortalTest.options += options.mapValues { it.value.value }
            val sender = AbstractConnectionBase.getCallInfo().source.removePrefix(":").replace('.', '_')
            val path = "/org/freedesktop/portal/desktop/request/$sender/${options.getValue("handle_token").value}"
            val file = Files.createTempFile("fox-portal", ".png").toFile().also {
                it.writeBytes(Png.encodeOpaque(ByteArray(8 * 6 * 4), 8, 6))
                files += it
            }
            val results: Map<String, Variant<*>> = if (withUri) mapOf("uri" to Variant(file.toURI().toString())) else emptyMap()
            thread {
                Thread.sleep(20)
                portalBus!!.sendMessage(RequestApi.Response(path, UInt32(code.toLong()), results))
            }
            return DBusPath(path)
        }
    }

    private fun startPortal(portal: FakePortal = FakePortal()) {
        val bus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        bus.exportObject("/org/freedesktop/portal/desktop", portal)
        bus.requestBusName("org.freedesktop.portal.Desktop")
        portalBus = bus
    }

    @AfterTest
    fun tearDown() {
        portalBus?.close()
        files.forEach { it.delete() }
    }

    @Test
    fun `a silent capture returns the PNG and removes the file the portal wrote`() = runBlocking {
        requireIsolatedBus()
        startPortal()
        val png = XdgScreenshotPortal(10.seconds).capture()
        assertEquals(8 to 6, Png.size(png))
        assertEquals(false, options.single()["interactive"])
        assertFalse(files.single().exists(), "a screenshot must not outlive the request in a shared folder")
    }

    @Test
    fun `an interactive capture asks the desktop for its picker`() = runBlocking {
        requireIsolatedBus()
        startPortal()
        XdgScreenshotPortal(10.seconds).captureInteractive()
        assertEquals(true, options.single()["interactive"])
    }

    @Test
    fun `dismissing the permission dialog of a silent capture is a refusal`() = runBlocking {
        requireIsolatedBus()
        startPortal(FakePortal(code = 1))
        val error = assertFailsWith<PortalException> { XdgScreenshotPortal(10.seconds).capture() }
        assertEquals(PortalFailure.Denied, error.failure)
    }

    @Test
    fun `closing the picker is a cancellation, not a refusal`() = runBlocking {
        requireIsolatedBus()
        startPortal(FakePortal(code = 1))
        val error = assertFailsWith<PortalException> { XdgScreenshotPortal(10.seconds).captureInteractive() }
        assertEquals(PortalFailure.Cancelled, error.failure)
    }

    @Test
    fun `any other answer is reported as unavailable`() = runBlocking {
        requireIsolatedBus()
        startPortal(FakePortal(code = 2))
        assertEquals(PortalFailure.Unavailable, assertFailsWith<PortalException> { XdgScreenshotPortal(10.seconds).capture() }.failure)
    }

    @Test
    fun `a success without an image is reported as unavailable`() = runBlocking {
        requireIsolatedBus()
        startPortal(FakePortal(withUri = false))
        assertEquals(PortalFailure.Unavailable, assertFailsWith<PortalException> { XdgScreenshotPortal(10.seconds).capture() }.failure)
    }

    @Test
    fun `no portal on the bus is unavailable`() = runBlocking {
        requireIsolatedBus()
        assertEquals(PortalFailure.Unavailable, assertFailsWith<PortalException> { XdgScreenshotPortal(5.seconds).capture() }.failure)
        assertTrue(options.isEmpty())
    }
}
