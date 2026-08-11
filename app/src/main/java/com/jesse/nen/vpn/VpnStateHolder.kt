package com.jesse.nen.vpn

import com.jesse.nen.common.SimpleStateHolder
import kotlinx.coroutines.flow.StateFlow

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
    private val holder = SimpleStateHolder(false)
    val running: StateFlow<Boolean> = holder.flow

    fun setRunning(value: Boolean) = holder.set(value)
}
