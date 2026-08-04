package com.jesse.nen.orbs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Ce que représente une orbe ; détermine son dessin et ce que fait un toucher. */
sealed interface OrbKind {
    /** Activation du Nen. */
    data object Ten : OrbKind

    /** Minuteur Pomodoro. */
    data object Pomodoro : OrbKind

    /** Ambiance sonore : lecture en boucle du morceau choisi. */
    data object Sound : OrbKind

    /** Un réglage manquant qui fragilise la protection. Disparaît une fois réglé. */
    data class Fault(val fault: FaultKind) : OrbKind

    /** Le Serment de Nen : tous les mots bloqués, réunis dans une orbe unique. */
    data object Serment : OrbKind

    /** Budgets quotidiens des Reels Instagram et des Shorts YouTube. */
    data object ShortForm : OrbKind
}

enum class FaultKind { ACCESSIBILITY_OFF, ACCESSIBILITY_DEAD, BATTERY }

/**
 * Une orbe du champ. Position et vitesse sont en pixels ; [x] et [y] désignent le CENTRE.
 *
 * La position est un état Compose, lu dans le `Modifier.offset` de l'orbe : à chaque frame
 * seul le placement est recalculé, les orbes elles-mêmes ne se recomposent pas.
 */
class Orb(
    val key: String,
    val kind: OrbKind,
    /** Rayon de collision : la sphère visible, halo exclu. */
    val radius: Float,
    /** Côté de la boîte de dessin, halo compris. */
    val boxSize: Float,
    val seed: Int,
    x: Float,
    y: Float,
    var vx: Float,
    var vy: Float
) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)

    /** Tenue au doigt : elle ne subit ni dérive ni impulsion, mais bouscule les autres. */
    var grabbed: Boolean = false
}

/** Description d'une orbe à faire exister ; l'engin s'occupe de la créer ou de la retirer. */
data class OrbSpec(val key: String, val kind: OrbKind, val radius: Float, val boxSize: Float)

/**
 * Le champ d'orbes : population, mouvement, collisions.
 *
 * Les orbes ne s'immobilisent jamais — une petite force oscillante propre à chacune entretient
 * la dérive. Elles ne se traversent pas non plus : tout chevauchement est séparé puis résolu
 * par un échange d'impulsion. Une orbe tenue au doigt compte comme une masse infinie : elle
 * pousse les autres sans être poussée.
 */
class OrbEngine {
    private val _orbs = mutableStateListOf<Orb>()
    val orbs: List<Orb> get() = _orbs

    private var width = 0f
    private var height = 0f
    private val random = Random(0x4E454E)

    fun setBounds(width: Float, height: Float) {
        this.width = width
        this.height = height
    }

    /** Aligne la population sur [specs], en conservant la position des orbes déjà là. */
    fun sync(specs: List<OrbSpec>) {
        if (width <= 0f || height <= 0f) return

        val wanted = specs.associateBy { it.key }
        _orbs.removeAll { it.key !in wanted }

        val present = _orbs.map { it.key }.toSet()
        specs.filter { it.key !in present }.forEach { spec ->
            _orbs.add(spawn(spec))
        }
    }

    private fun spawn(spec: OrbSpec): Orb {
        // Apparition n'importe où dans l'écran, mais jamais collée à un bord.
        val margin = spec.boxSize / 2f
        return Orb(
            key = spec.key,
            kind = spec.kind,
            radius = spec.radius,
            boxSize = spec.boxSize,
            seed = _orbs.size + random.nextInt(1000),
            x = margin + random.nextFloat() * (width - 2 * margin).coerceAtLeast(1f),
            y = margin + random.nextFloat() * (height - 2 * margin).coerceAtLeast(1f),
            vx = (random.nextFloat() - 0.5f) * 90f,
            vy = (random.nextFloat() - 0.5f) * 90f
        )
    }

    /** L'orbe sous le doigt, la plus haute d'abord (dernier dessiné = au-dessus). */
    fun orbAt(position: Offset): Orb? = _orbs.lastOrNull { orb ->
        hypot(position.x - orb.x, position.y - orb.y) <= orb.radius * TOUCH_MARGIN
    }

    fun grab(orb: Orb) {
        orb.grabbed = true
        orb.vx = 0f
        orb.vy = 0f
    }

    /**
     * Déplace une orbe tenue au doigt. La vitesse est mise à jour au passage : c'est elle qui
     * décide de la force avec laquelle l'orbe écarte celles qu'elle croise.
     */
    fun dragTo(orb: Orb, position: Offset, dtSeconds: Float) {
        if (dtSeconds > 0f) {
            orb.vx = (position.x - orb.x) / dtSeconds
            orb.vy = (position.y - orb.y) / dtSeconds
        }
        orb.x = position.x
        orb.y = position.y
    }

    /** Relâche l'orbe avec la vitesse du geste : c'est le lancer. */
    fun release(orb: Orb, velocityX: Float, velocityY: Float) {
        orb.grabbed = false
        orb.vx = velocityX * THROW_SCALE
        orb.vy = velocityY * THROW_SCALE
        clampSpeed(orb)
    }

    /** Un pas de simulation. [timeSeconds] sert aux forces de dérive, propres à chaque orbe. */
    fun step(dtSeconds: Float, timeSeconds: Float) {
        if (width <= 0f || height <= 0f) return
        val dt = dtSeconds.coerceAtMost(MAX_STEP_SECONDS)

        for (orb in _orbs) {
            if (orb.grabbed) continue

            // Dérive entretenue : deux oscillations lentes déphasées par la graine de l'orbe.
            orb.vx += sin(timeSeconds * 0.31f + orb.seed * 1.7f) * DRIFT_ACCEL * dt
            orb.vy += cos(timeSeconds * 0.23f + orb.seed * 2.3f) * DRIFT_ACCEL * dt

            orb.vx *= DAMPING
            orb.vy *= DAMPING
            clampSpeed(orb)

            orb.x += orb.vx * dt
            orb.y += orb.vy * dt

            bounceOnWalls(orb)
        }

        resolveCollisions()
    }

    /**
     * Le rebond se fait sur la boîte de dessin, pas sur la sphère : c'est le halo qui touche
     * le bord en premier, et le laisser dépasser le ferait trancher au carré par le bord de
     * l'écran.
     */
    private fun bounceOnWalls(orb: Orb) {
        val margin = orb.boxSize / 2f
        if (orb.x - margin < 0f) {
            orb.x = margin
            orb.vx = abs(orb.vx) * WALL_RESTITUTION
        } else if (orb.x + margin > width) {
            orb.x = width - margin
            orb.vx = -abs(orb.vx) * WALL_RESTITUTION
        }
        if (orb.y - margin < 0f) {
            orb.y = margin
            orb.vy = abs(orb.vy) * WALL_RESTITUTION
        } else if (orb.y + margin > height) {
            orb.y = height - margin
            orb.vy = -abs(orb.vy) * WALL_RESTITUTION
        }
    }

    /**
     * Séparation puis rebond, deux à deux. Le nombre d'orbes se compte en dizaines : la boucle
     * quadratique reste largement sous le budget d'une frame, inutile de partitionner l'espace.
     */
    private fun resolveCollisions() {
        for (i in _orbs.indices) {
            val a = _orbs[i]
            for (j in i + 1 until _orbs.size) {
                val b = _orbs[j]
                var dx = b.x - a.x
                var dy = b.y - a.y
                var distance = hypot(dx, dy)
                val minDistance = a.radius + b.radius
                if (distance >= minDistance) continue

                // Superposition parfaite : il n'y a pas de normale, on en invente une.
                if (distance < 0.001f) {
                    dx = 1f
                    dy = 0f
                    distance = 0.001f
                }
                val nx = dx / distance
                val ny = dy / distance
                val overlap = minDistance - distance

                // 1) On les décolle. Une orbe tenue au doigt ne cède pas : c'est le mur.
                when {
                    a.grabbed && b.grabbed -> Unit
                    a.grabbed -> {
                        b.x += nx * overlap
                        b.y += ny * overlap
                    }
                    b.grabbed -> {
                        a.x -= nx * overlap
                        a.y -= ny * overlap
                    }
                    else -> {
                        a.x -= nx * overlap / 2f
                        a.y -= ny * overlap / 2f
                        b.x += nx * overlap / 2f
                        b.y += ny * overlap / 2f
                    }
                }

                // 2) Rebond, sur la seule composante normale des vitesses, et seulement si
                //    elles se rapprochent — sinon deux orbes qui se séparent se recolleraient.
                val relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (relative > 0f) continue

                if (a.grabbed && b.grabbed) continue
                val impulse = -(1f + ORB_RESTITUTION) * relative
                when {
                    a.grabbed -> {
                        b.vx += impulse * nx
                        b.vy += impulse * ny
                    }
                    b.grabbed -> {
                        a.vx -= impulse * nx
                        a.vy -= impulse * ny
                    }
                    // Masses égales : chacune prend la moitié de l'impulsion.
                    else -> {
                        val half = impulse / 2f
                        a.vx -= half * nx
                        a.vy -= half * ny
                        b.vx += half * nx
                        b.vy += half * ny
                    }
                }
                clampSpeed(a)
                clampSpeed(b)
            }
        }
    }

    private fun clampSpeed(orb: Orb) {
        val speed = hypot(orb.vx, orb.vy)
        if (speed > MAX_SPEED) {
            orb.vx = orb.vx / speed * MAX_SPEED
            orb.vy = orb.vy / speed * MAX_SPEED
        }
    }

    private companion object {
        /**
         * Zone de préhension bien plus large que la sphère : les orbes sont petites, et viser
         * un disque de quelques millimètres au doigt serait pénible.
         */
        const val TOUCH_MARGIN = 2.4f

        /** Un lancer trop fidèle au geste enverrait l'orbe d'un bord à l'autre en un éclair. */
        const val THROW_SCALE = 0.45f

        const val MAX_SPEED = 2_600f          // px/s
        const val DRIFT_ACCEL = 26f           // px/s² de dérive entretenue
        const val DAMPING = 0.995f            // frottement du vide, très faible
        const val WALL_RESTITUTION = 0.86f
        const val ORB_RESTITUTION = 0.92f

        /** Bride les pas trop longs (retour d'arrière-plan) : sinon les orbes se téléportent. */
        const val MAX_STEP_SECONDS = 1f / 30f
    }
}
