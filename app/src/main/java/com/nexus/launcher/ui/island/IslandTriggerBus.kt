package com.nexus.launcher.ui.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IslandTriggerBus {
    private val slots = linkedMapOf<IslandKind, IslandPayload?>()
    private val _active = MutableStateFlow<List<IslandPayload>>(emptyList())
    val active: StateFlow<List<IslandPayload>> = _active.asStateFlow()

    fun publish(kind: IslandKind, payload: IslandPayload?) {
        slots[kind] = payload
        rebuild()
    }

    fun clear() {
        slots.clear()
        _active.value = emptyList()
    }

    private fun rebuild() {
        _active.value = IslandPrefs.priority.mapNotNull { slots[it] }
    }
}
