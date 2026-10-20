package com.foxdevelops.foxscreenshots.core.settings

/** Minimal persistent key/value storage; the desktop implementation writes a local file. */
interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** In-memory store for tests and previews. */
class MemorySettingsStore(initial: Map<String, String> = emptyMap()) : SettingsStore {
    private val values = initial.toMutableMap()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
