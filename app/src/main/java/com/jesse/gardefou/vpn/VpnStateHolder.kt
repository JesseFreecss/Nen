package com.jesse.gardefou.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Petit dépôt d'état partagé (objet singleton) pour savoir si le VPN tourne.
 * Le service met à jour cet état ; l'UI (MainActivity) l'observe via `running`.
 * Découple l'UI de l'instance du service (qui va et vient).
 *
 * Note : l'état vit en mémoire du process. Si le service tournait et que le process
 * est recréé, on ré-affichera "désactivé" — suffisant pour cette étape ; on fiabilisera
 * plus tard (ex. relecture réelle de l'état du VPN).
 */
object VpnStateHolder {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}
