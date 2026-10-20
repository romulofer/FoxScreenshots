package com.foxdevelops.foxscreenshots.features.home

import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory list of screenshots taken this session, most recent first. Not a permanent library. */
class SessionController {
    private val mutableState = MutableStateFlow<List<CaptureResult>>(emptyList())
    val state: StateFlow<List<CaptureResult>> = mutableState.asStateFlow()

    fun add(capture: CaptureResult) = mutableState.update { (listOf(capture) + it).take(MAX_SESSION_CAPTURES) }

    fun remove(id: String) = mutableState.update { list -> list.filterNot { it.id == id } }

    fun replace(capture: CaptureResult) =
        mutableState.update { list -> list.map { if (it.id == capture.id) capture else it } }

    fun clear() = mutableState.update { emptyList() }

    companion object {
        /** Soft cap so a long session cannot grow unbounded in RAM; screenshots of passwords stay resident otherwise. */
        const val MAX_SESSION_CAPTURES = 30
    }
}
