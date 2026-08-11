package com.jesse.nen.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Petit dépôt d'état partagé générique : le moule répété par VpnStateHolder,
 * PomodoroStateHolder et AmbienceStateHolder, factorisé une fois.
 */
internal class SimpleStateHolder<T>(initial: T) {
    private val _flow = MutableStateFlow(initial)
    val flow: StateFlow<T> = _flow.asStateFlow()

    fun set(value: T) {
        _flow.value = value
    }

    fun update(transform: (T) -> T) = _flow.update(transform)
}
