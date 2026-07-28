package com.jesse.gardefou.blocklist

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.ui.rememberElapsedMillis
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Un vœu scellé, rendu illisible : une sphère marbrée noir / violet / bleu qui
 * tourbillonne, façon nébuleuse. Le contenu n'apparaît qu'après déverrouillage par
 * empreinte.
 *
 * Le tourbillon organique (bruit fractal déformé, [MARBLE_AGSL]) demande un
 * `RuntimeShader`, disponible seulement à partir d'Android 13 — l'app vise minSdk 26.
 * En dessous, on retombe sur [VowOrbPainted], une version dessinée à la main qui
 * reprend la même palette avec des primitives Canvas ordinaires.
 */
@Composable
fun VowOrb(
    modifier: Modifier = Modifier,
    // La sphère n'occupe que 60 % de la boîte : le reste est la marge dans laquelle le
    // halo s'éteint.
    diameter: Dp = 31.dp,
    seed: Int = 0
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VowOrbFluid(modifier, diameter, seed)
    } else {
        VowOrbPainted(modifier, diameter, seed)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VowOrbFluid(
    modifier: Modifier,
    diameter: Dp,
    seed: Int
) {
    val elapsedMillis = rememberElapsedMillis()
    // Une RuntimeShader par orbe, remember-ée une fois puis réutilisée à chaque frame :
    // seuls les uniforms changent, pas le programme — à vérifier sur device si la
    // grille de SealedVows tient la cadence avec plusieurs dizaines d'orbes.
    val shader = remember { RuntimeShader(MARBLE_AGSL) }
    val timeOffset = remember(seed) { seed * 7.3f }

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Vœu scellé, toucher pour révéler" }
    ) {
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_time", elapsedMillis / 1000f + timeOffset)
        drawRect(brush = ShaderBrush(shader), size = size)
    }
}

/**
 * Bruit fractal (fbm) déformé par son propre champ (domain warp), coloré en dégradé
 * noir → indigo → bleu → violet, avec des paillettes dorées et un halo qui se fond dans
 * le fond. `main` reçoit fragCoord en pixels du Canvas, pas de coordonnées normalisées.
 */
private const val MARBLE_AGSL = """
uniform float2 u_resolution;
uniform float u_time;

float random(float2 st) {
    return fract(sin(dot(st, float2(12.9898, 78.233))) * 43758.5453123);
}

float noise(float2 st) {
    float2 i = floor(st);
    float2 f = fract(st);
    float a = random(i);
    float b = random(i + float2(1.0, 0.0));
    float c = random(i + float2(0.0, 1.0));
    float d = random(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(float2 st) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        value += amp * noise(st);
        st *= 2.02;
        amp *= 0.5;
    }
    return value;
}

// Teintes relevées sur l'anneau du fond : un noir profond, un indigo, le bleu des volutes et
// le vert d'eau des reflets. La sphère doit avoir l'air taillée dans la même matière que lui.
float3 palette(float f, float2 q, float2 r) {
    float3 blackC = float3(0.010, 0.012, 0.028);
    float3 indigo = float3(0.10, 0.06, 0.30);
    float3 blueC = float3(0.10, 0.22, 0.52);
    float3 violet = float3(0.40, 0.26, 0.74);
    float3 col = mix(blackC, indigo, clamp(f * 1.4, 0.0, 1.0));
    col = mix(col, blueC, clamp(length(q) * 0.9, 0.0, 1.0));
    col = mix(col, violet, clamp(pow(max(r.x * 0.5 + 0.5, 0.0), 3.0), 0.0, 1.0));
    return col;
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * u_resolution) / min(u_resolution.x, u_resolution.y);
    // La sphère ne prend que 0.30 de la largeur, et non 0.42 : la marge restante est ce
    // qui permet au halo de descendre à zéro avant le bord. Sans elle il était coupé net
    // à 75 % d'opacité et l'orbe apparaissait dans un carré. p est réajusté d'autant pour
    // que la marbrure garde la même densité sur une sphère de même taille à l'écran.
    float dist = length(uv) / 0.30;
    float2 p = uv * 4.2;
    float t = u_time * 0.055;

    float2 q = float2(fbm(p + t), fbm(p + float2(5.2, 1.3) - t * 0.8));
    float2 r = float2(
        fbm(p + 3.5 * q + float2(1.7, 9.2) + t * 1.4),
        fbm(p + 3.5 * q + float2(8.3, 2.8) + t * 1.1)
    );
    float f = fbm(p + 2.5 * r);

    float3 marble = palette(f, q, r);
    float rimDarken = smoothstep(0.5, 1.0, dist);
    // Intérieur assombri : comme les autres orbes, celle-ci doit laisser deviner le fond au
    // lieu de le masquer. Toute sa lumière est dans son fil.
    marble *= mix(0.80, 0.42, rimDarken);

    // Le filament : une bande étroite au bord, pas un dégradé large. C'est le même fil de
    // lumière que celui de l'anneau du fond, ramené à l'échelle de la sphère — et c'est lui
    // qui empêche l'orbe de se lire comme un disque plat.
    float filament = exp(-pow((dist - 0.93) / 0.085, 2.0));
    float iridescence = sin(u_time * 0.35 + f * 7.0 + q.x * 3.0) * 0.5 + 0.5;
    float3 rimColor = mix(float3(0.52, 0.95, 0.88), float3(0.72, 0.60, 1.00), iridescence);
    // Points chauds qui courent le long du fil, comme dans les boucles du fond.
    float travel = sin(atan(uv.y, uv.x) * 2.0 - u_time * 0.55) * 0.5 + 0.5;
    marble += filament * rimColor * (0.34 + travel * 0.42);

    // Paillettes : une cellule sur vingt environ porte un point, place au hasard dans le
    // quart central de sa cellule. Colorer la cellule entiere, comme avant, donnait des
    // carres ; la garder au centre evite qu'un point deborde sur la cellule voisine, qui
    // l'ignore et le trancherait au carre elle aussi.
    float2 sparkGrid = p * 4.5 + t * 1.5;
    float2 cell = floor(sparkGrid);
    float sparkleSeed = random(cell);
    float2 spot = float2(0.3, 0.3) + 0.4 * float2(random(cell + 3.1), random(cell + 7.7));
    float spotDist = length(fract(sparkGrid) - spot);
    float point = 1.0 - smoothstep(0.0, 0.2, spotDist);
    float twinkle = sin(u_time * (2.0 + sparkleSeed * 6.0) + sparkleSeed * 40.0) * 0.5 + 0.5;
    float sparkle = smoothstep(0.94, 1.0, sparkleSeed) * twinkle * point * point;
    marble += sparkle * float3(1.0, 0.85, 0.55) * 1.3;

    float inside = 1.0 - smoothstep(0.88, 1.03, dist);

    // Un seul dégradé pour tout le pourtour, au lieu d'un anneau net doublé d'un halo :
    // il part de la surface de la sphère et vaut déjà moins de 1 % à dist 1.6, alors que
    // le bord de la boîte est à 1.67.
    float haloD = max(dist - 0.92, 0.0) * 3.1;
    float halo = exp(-haloD * haloD);
    float2 auraWarp = p * 0.55 + t * 0.35;
    float auraNoise = fbm(auraWarp);
    float3 haloColor = mix(float3(0.20, 0.28, 0.95), float3(0.55, 0.20, 0.95), auraNoise);

    // Filet de sécurité : force l'alpha à zéro sur un cercle strictement intérieur à la
    // boîte, pour qu'aucune arête ne puisse réapparaître dans les coins.
    float edgeCut = 1.0 - smoothstep(1.38, 1.62, dist);

    // La couleur reste « droite » (non prémultipliée) et c'est l'alpha seul qui porte la
    // couverture : sinon le halo est atténué deux fois et le dégradé casse.
    float3 color = mix(haloColor, marble, inside);
    float alpha = clamp(inside + halo * 0.55 * (1.0 - inside), 0.0, 1.0) * edgeCut;

    return half4(color * alpha, alpha);
}
"""

/**
 * Repli sous Android 13 : même palette noir / violet / bleu, mais composée à partir
 * d'arcs et de dégradés ordinaires (pas de bruit fractal, trop coûteux à recalculer en
 * Kotlin par frame pour une grille de plusieurs dizaines d'orbes).
 */
@Composable
private fun VowOrbPainted(
    modifier: Modifier,
    diameter: Dp,
    seed: Int
) {
    val elapsed = rememberElapsedMillis()
    val phase = remember(seed) { (seed * 137) % 360 }
    val speed = remember(seed) { 0.05f + (seed % 7) * 0.010f }
    val sparkCount = remember(seed) { 5 + (seed % 3) }

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Vœu scellé, toucher pour révéler" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // Même sphère que la version AGSL (0,30 de la boîte) : les deux rendus doivent occuper
        // exactement la même place, le moteur de collisions ne connaît qu'un rayon.
        val radius = min(size.width, size.height) / 2f * 0.60f
        val angle = (elapsed * speed + phase) % 360f
        val pulse = (sin(elapsed / 380f + seed) + 1f) / 2f
        val heartbeat = (sin(elapsed / 90f + seed * 3f) + 1f) / 2f

        // Halo en un seul dégradé, qui culmine à la surface de la sphère et s'éteint
        // avant le bord de la boîte. Les disques de couleur plate d'avant avaient une
        // arête franche, et débordaient de la boîte : elle les coupait en carré.
        val glowRadius = radius * 1.62f
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to Color.Transparent,
                0.45f to VIOLET_HOT.copy(alpha = 0.28f + pulse * 0.12f),
                0.70f to EDGE_BLUE.copy(alpha = 0.10f + pulse * 0.04f),
                1.00f to Color.Transparent,
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center,
            blendMode = BlendMode.Plus
        )

        rotate(degrees = angle, pivot = center) {
            drawPlasmaArc(center, radius * 0.86f, sweep = 150f, width = radius * 0.34f, alpha = 0.65f)
            rotate(degrees = 180f, pivot = center) {
                drawPlasmaArc(center, radius * 0.86f, sweep = 150f, width = radius * 0.34f, alpha = 0.65f)
            }
        }
        rotate(degrees = -angle * 1.6f, pivot = center) {
            rotate(degrees = 60f, pivot = center) {
                drawPlasmaArc(center, radius * 0.58f, sweep = 110f, width = radius * 0.24f, alpha = 0.55f)
            }
            rotate(degrees = 240f, pivot = center) {
                drawPlasmaArc(center, radius * 0.58f, sweep = 110f, width = radius * 0.24f, alpha = 0.55f)
            }
        }

        drawCircle(
            brush = Brush.radialGradient(
                0.00f to CORE_FLASH.copy(alpha = 0.9f + heartbeat * 0.1f),
                0.30f to VIOLET_HOT.copy(alpha = 0.7f),
                1.00f to Color.Transparent,
                center = center,
                radius = radius * 0.55f
            ),
            radius = radius * 0.55f,
            center = center,
            blendMode = BlendMode.Plus
        )

        drawCircle(color = Color.Black, radius = radius * 0.30f, center = center)

        rotate(degrees = angle * 0.4f, pivot = center) {
            for (i in 0 until sparkCount) {
                val sparkAngle = i * (360f / sparkCount)
                val sparkFlicker = (sin(elapsed / 140f + seed + i * 2f) + 1f) / 2f
                if (sparkFlicker > 0.35f) {
                    drawSpark(center, radius, sparkAngle, sparkFlicker)
                }
            }
        }
    }
}

private fun DrawScope.drawPlasmaArc(
    center: Offset,
    radius: Float,
    sweep: Float,
    width: Float,
    alpha: Float
) {
    val fadeAt = sweep / 360f
    drawArc(
        brush = Brush.sweepGradient(
            0.00f to Color.Transparent,
            fadeAt * 0.5f to VIOLET_HOT.copy(alpha = alpha),
            fadeAt to Color.Transparent,
            1.00f to Color.Transparent,
            center = center
        ),
        startAngle = 0f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round),
        blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawSpark(center: Offset, radius: Float, angleDeg: Float, intensity: Float) {
    val theta = Math.toRadians(angleDeg.toDouble())
    val innerR = radius * 0.95f
    val outerR = radius * (1.15f + intensity * 0.35f)
    val start = Offset(
        center.x + (innerR * cos(theta)).toFloat(),
        center.y + (innerR * sin(theta)).toFloat()
    )
    val end = Offset(
        center.x + (outerR * cos(theta)).toFloat(),
        center.y + (outerR * sin(theta)).toFloat()
    )
    drawLine(
        color = SPARK_COLOR.copy(alpha = intensity * 0.85f),
        start = start,
        end = end,
        strokeWidth = radius * 0.05f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Plus
    )
}

private val CORE_FLASH = Color(0xFFEDE0FF)
private val VIOLET_HOT = Color(0xFFA537FF)
private val EDGE_BLUE = Color(0xFF3B5BFF)
private val SPARK_COLOR = Color(0xFFE9D6FF)
