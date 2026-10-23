package com.foxdevelops.foxscreenshots.platform.portal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.io.File
import java.io.IOException
import java.net.URI
import kotlin.time.Duration

// Kotlin adds `? extends` to Map/List parameters of open types; dbus-java reads the signature by reflection and rejects that.
@JvmSuppressWildcards
@DBusInterfaceName("org.freedesktop.portal.Screenshot")
internal interface ScreenshotApi : DBusInterface {
    fun Screenshot(parentWindow: String, options: Map<String, Variant<*>>): DBusPath
}

@JvmSuppressWildcards
@DBusInterfaceName("org.freedesktop.portal.Request")
interface RequestApi : DBusInterface {
    class Response(path: String, val response: UInt32, val results: Map<String, Variant<*>>) :
        DBusSignal(path, response, results)
}

/**
 * `org.freedesktop.portal.Screenshot` over the session bus.
 *
 * The portal answers asynchronously: the method call only hands back the object
 * path of a request, and the picture arrives later on that request's `Response`
 * signal. The subscription is therefore opened *before* the call, so a fast
 * portal cannot answer into the void.
 */
class XdgScreenshotPortal(private val timeout: Duration = DEFAULT_PORTAL_TIMEOUT) : ScreenshotPortal {

    override suspend fun capture(): ByteArray = ask(interactive = false)

    override suspend fun captureInteractive(): ByteArray = ask(interactive = true)

    private suspend fun ask(interactive: Boolean): ByteArray = withContext(Dispatchers.IO) {
        try {
            DBusConnectionBuilder.forSessionBus().withShared(false).build().use { bus -> request(bus, interactive) }
        } catch (e: PortalException) {
            throw e
        } catch (e: DBusExecutionException) {
            throw PortalException(PortalFailure.Unavailable, e.message)
        } catch (e: DBusException) {
            throw PortalException(PortalFailure.Unavailable, e.message)
        } catch (e: IOException) {
            throw PortalException(PortalFailure.Unavailable, e.message)
        }
    }

    private suspend fun request(bus: DBusConnection, interactive: Boolean): ByteArray {
        // The portal derives the request's object path from the caller's bus name and this token,
        // so the reply can be subscribed to before it exists.
        val token = "foxscreenshots_${System.nanoTime()}"
        val sender = bus.uniqueName.removePrefix(":").replace('.', '_')
        val requestPath = "$PORTAL_PATH/request/$sender/$token"

        val answer = CompletableDeferred<RequestApi.Response>()
        val requestObject = bus.getRemoteObject(PORTAL_NAME, requestPath, RequestApi::class.java)
        // By object path, not by sender: dbus-java refuses a well-known bus name as the source of a signal watch.
        bus.addSigHandler(RequestApi.Response::class.java, requestObject) { answer.complete(it) }.use {
            val portal = bus.getRemoteObject(PORTAL_NAME, PORTAL_PATH, ScreenshotApi::class.java)
            portal.Screenshot(
                // No parent window: the app hides itself before capturing, so there is nothing to sit on top of.
                "",
                mapOf(
                    "handle_token" to Variant(token),
                    "interactive" to Variant(interactive),
                ),
            )
            val response = try {
                withTimeout(timeout) { answer.await() }
            } catch (_: TimeoutCancellationException) {
                throw PortalException(PortalFailure.Denied, "the desktop never answered the screenshot request")
            }
            return readResult(response, interactive)
        }
    }

    /**
     * Turns the reply into pixels, and takes the file the portal wrote with it:
     * leaving screenshots lying around in a shared directory would outlive the
     * session they were taken in.
     */
    private fun readResult(response: RequestApi.Response, interactive: Boolean): ByteArray {
        when (response.response.toInt()) {
            RESPONSE_SUCCESS -> Unit
            // Closing the picker is a choice; dismissing the permission dialog of a silent capture is a refusal.
            RESPONSE_CANCELLED -> throw PortalException(if (interactive) PortalFailure.Cancelled else PortalFailure.Denied)
            else -> throw PortalException(PortalFailure.Unavailable, "portal response ${response.response}")
        }
        val uri = response.results["uri"]?.value as? String
            ?: throw PortalException(PortalFailure.Unavailable, "portal reported success without an image")

        val file = try {
            File(URI(uri))
        } catch (e: IllegalArgumentException) {
            throw PortalException(PortalFailure.Unavailable, "unusable screenshot uri")
        }
        try {
            return file.readBytes()
        } catch (e: IOException) {
            throw PortalException(PortalFailure.Unavailable, e.message)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val PORTAL_NAME = "org.freedesktop.portal.Desktop"
        const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        const val RESPONSE_SUCCESS = 0
        const val RESPONSE_CANCELLED = 1
    }
}
