package com.foxdevelops.foxscreenshots.features.home

import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A capture taken out of the session, with where it sat, so a delete can be undone. */
class RemovedCapture(val capture: CaptureResult, val index: Int, val savedPath: String?)

/** In-memory list of screenshots taken this session, most recent first. Not a permanent library. */
class SessionController {
    private val mutableState = MutableStateFlow<List<CaptureResult>>(emptyList())
    val state: StateFlow<List<CaptureResult>> = mutableState.asStateFlow()

    private val mutablePaths = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Where each capture was last saved to disk, by capture id; the gallery can only reveal those. */
    val savedPaths: StateFlow<Map<String, String>> = mutablePaths.asStateFlow()

    /** Whether the next [add] pushes the oldest capture out. */
    val isFull: Boolean get() = mutableState.value.size >= MAX_SESSION_CAPTURES

    fun add(capture: CaptureResult) {
        mutableState.update { (listOf(capture) + it).take(MAX_SESSION_CAPTURES) }
        prunePaths()
    }

    /** Removes [id] and returns what is needed to put it back, or `null` if it was not there. */
    fun remove(id: String): RemovedCapture? {
        val list = mutableState.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val removed = RemovedCapture(list[index], index, mutablePaths.value[id])
        mutableState.update { current -> current.filterNot { it.id == id } }
        prunePaths()
        return removed
    }

    /** Puts a removed capture back where it was (or at the end if the list shrank), within the cap. */
    fun restore(removed: RemovedCapture) {
        mutableState.update { current ->
            if (current.any { it.id == removed.capture.id }) {
                current
            } else {
                current.toMutableList().apply { add(removed.index.coerceIn(0, size), removed.capture) }.take(MAX_SESSION_CAPTURES)
            }
        }
        removed.savedPath?.let { markSaved(removed.capture.id, it) }
    }

    fun replace(capture: CaptureResult) =
        mutableState.update { list -> list.map { if (it.id == capture.id) capture else it } }

    /** Records that [id] now exists on disk at [path]. */
    fun markSaved(id: String, path: String) {
        if (mutableState.value.none { it.id == id }) return
        mutablePaths.update { it + (id to path) }
    }

    fun clear() {
        mutableState.update { emptyList() }
        mutablePaths.update { emptyMap() }
    }

    /** Forgets paths of captures that left the list (evicted or deleted). */
    private fun prunePaths() {
        val ids = mutableState.value.mapTo(HashSet()) { it.id }
        mutablePaths.update { paths -> paths.filterKeys { it in ids } }
    }

    companion object {
        /** Soft cap so a long session cannot grow unbounded in RAM; screenshots of passwords stay resident otherwise. */
        const val MAX_SESSION_CAPTURES = 30
    }
}
