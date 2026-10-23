package com.foxdevelops.foxscreenshots.platform.hotkey

import com.foxdevelops.foxscreenshots.core.hotkey.GlobalHotkeys
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.platform.portal.GlobalShortcutsApi
import com.foxdevelops.foxscreenshots.platform.portal.PortalSessionApi
import com.foxdevelops.foxscreenshots.platform.portal.RequestApi
import com.foxdevelops.foxscreenshots.platform.portal.ShortcutSpec
import com.foxdevelops.foxscreenshots.platform.portal.toPortalTrigger
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Global hotkeys on Wayland, through the `GlobalShortcuts` portal.
 *
 * Wayland lets no client watch the keyboard, so the app asks the *desktop* to bind shortcuts and to tell
 * it when one is pressed. Everything is asynchronous and may involve the user (the desktop can show a
 * confirmation), so [register] never blocks the caller: the work happens on a worker thread and the
 * outcome arrives through [onStatus]. Rebinding after a settings change repeats the bind on the same session.
 *
 * Only the three capture actions are bound. [HotkeyAction.Cancel] is left out on purpose: the portal makes a
 * real grab, and a grabbed Esc would stop working in every other program.
 */
class PortalGlobalHotkeys(
    private val connect: () -> DBusConnection = { DBusConnectionBuilder.forSessionBus().withShared(false).build() },
    private val timeoutSeconds: Long = 120,
) : GlobalHotkeys {

    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "portal-hotkeys").apply { isDaemon = true } }

    @Volatile
    private var onTriggered: (HotkeyAction) -> Unit = {}

    @Volatile
    private var statusListener: (Boolean, String?) -> Unit = { _, _ -> }

    // Touched only on the worker thread.
    private var bus: DBusConnection? = null
    private var session: String? = null
    private var tokenCounter = 0

    private val closing = java.util.concurrent.atomic.AtomicBoolean(false)

    private val closed: Boolean get() = closing.get()

    override fun onStatus(listener: (working: Boolean, detail: String?) -> Unit) {
        statusListener = listener
    }

    override fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit) {
        this.onTriggered = onTriggered
        val wanted = bindings.filterKeys { it != HotkeyAction.Cancel }
        worker.execute {
            if (closed) return@execute
            try {
                val connection = bus ?: open().also { bus = it }
                bind(connection, wanted)
                statusListener(true, null)
            } catch (e: Exception) {
                statusListener(false, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun close() {
        // Once only: a second close would hand work to an executor that is already shut down.
        if (!closing.compareAndSet(false, true)) return
        worker.execute {
            val connection = bus ?: return@execute
            runCatching {
                session?.let { connection.getRemoteObject(PORTAL_NAME, it, PortalSessionApi::class.java).Close() }
            }
            runCatching { connection.close() }
            bus = null
            session = null
        }
        worker.shutdown()
    }

    /** Connects, checks that the desktop offers the portal at all, and opens the shortcuts session. */
    private fun open(): DBusConnection {
        val connection = connect()
        try {
            // A backend without GlobalShortcuts (sway, older GNOME) has no such interface: fail fast with a clear reason.
            connection.getRemoteObject(PORTAL_NAME, PORTAL_PATH, Properties::class.java).Get<Any>(INTERFACE, "version")
        } catch (e: Exception) {
            runCatching { connection.close() }
            throw HotkeyUnavailableException("this desktop offers no global shortcuts portal (${e.message})", e)
        }
        connection.addSigHandler(GlobalShortcutsApi.Activated::class.java) { signal ->
            if (signal.sessionHandle.path == session) ACTION_BY_ID[signal.shortcutId]?.let { onTriggered(it) }
        }
        val results = request(connection) { token ->
            api(connection).CreateSession(
                mapOf(
                    "handle_token" to Variant(token),
                    "session_handle_token" to Variant("foxscreenshots_session"),
                ),
            )
        }
        session = results["session_handle"]?.value as? String
            ?: throw HotkeyUnavailableException("the portal opened no session")
        return connection
    }

    private fun bind(connection: DBusConnection, bindings: Map<HotkeyAction, HotkeyBinding>) {
        val handle = session ?: throw HotkeyUnavailableException("no portal session")
        val specs = bindings.map { (action, binding) ->
            ShortcutSpec(
                idOf(action),
                mapOf(
                    "description" to Variant(DESCRIPTION.getValue(action)),
                    "preferred_trigger" to Variant(binding.toPortalTrigger()),
                ),
            )
        }
        request(connection) { token ->
            api(connection).BindShortcuts(DBusPath(handle), specs, "", mapOf("handle_token" to Variant(token)))
        }
    }

    private fun api(connection: DBusConnection) = connection.getRemoteObject(PORTAL_NAME, PORTAL_PATH, GlobalShortcutsApi::class.java)

    /**
     * Runs a portal call and waits for its `Response` signal. As with every portal request the reply path is
     * derived from our bus name and a token, so the subscription is opened *before* the call.
     */
    private fun request(connection: DBusConnection, call: (token: String) -> DBusPath): Map<String, Variant<*>> {
        val token = "foxscreenshots_${System.nanoTime()}_${tokenCounter++}"
        val sender = connection.uniqueName.removePrefix(":").replace('.', '_')
        val requestPath = "/org/freedesktop/portal/desktop/request/$sender/$token"
        val answer = CompletableFuture<RequestApi.Response>()
        val requestObject = connection.getRemoteObject(PORTAL_NAME, requestPath, RequestApi::class.java)
        connection.addSigHandler(RequestApi.Response::class.java, requestObject) { answer.complete(it) }.use {
            call(token)
            val response = try {
                answer.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                throw HotkeyUnavailableException("the desktop never answered the shortcuts request")
            }
            if (response.response.toInt() != 0) {
                throw HotkeyUnavailableException("the desktop refused the shortcuts request (${response.response})")
            }
            return response.results
        }
    }

    private companion object {
        const val PORTAL_NAME = "org.freedesktop.portal.Desktop"
        const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        const val INTERFACE = "org.freedesktop.portal.GlobalShortcuts"

        val ACTION_BY_ID = mapOf(
            "instant" to HotkeyAction.Instant,
            "timer" to HotkeyAction.Timer,
            "repeat" to HotkeyAction.Repeat,
        )

        // What the desktop shows in its own shortcuts settings, next to the key.
        val DESCRIPTION = mapOf(
            HotkeyAction.Instant to "Capture a region",
            HotkeyAction.Timer to "Capture a region after a delay",
            HotkeyAction.Repeat to "Repeat the last capture",
        )

        fun idOf(action: HotkeyAction): String = ACTION_BY_ID.entries.first { it.value == action }.key
    }
}
