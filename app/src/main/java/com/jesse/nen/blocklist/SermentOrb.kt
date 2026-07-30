package com.jesse.nen.blocklist

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
import com.jesse.nen.ui.rememberElapsedMillis
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Le Serment de Nen : une orbe UNIQUE, rouge, marbrée et tourbillonnante, qui réunit tous les
 * mots bloqués. Reprend le tourbillon organique de l'ancien vœu scellé (bruit fractal déformé),
 * mais dans une palette rouge/braise au lieu de noir/violet — même famille que [FaultOrb], en
 * plus profond : celle-ci ne signale pas un problème, elle protège.
 *
 * Une seule instance de cette orbe existe à l'écran (contrairement à l'ancien système où
 * chaque mot avait sa propre sphère) : le détail des mots n'apparaît plus au toucher direct,
 * mais dans la liste ouverte après authentification.
 */
@Composable
fun SermentOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 34.dp
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SermentOrbFluid(modifier, diameter)
    } else {
        SermentOrbPainted(modifier, diameter)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun SermentOrbFluid(modifier: Modifier, diameter: Dp) {
    val elapsedMillis = rememberElapsedMillis()
    val shader = remember { RuntimeShader(EMBER_AGSL) }

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Serment de Nen, toucher pour gérer les mots bloqués" }
    ) {
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_time", elapsedMillis / 1000f)
        drawRect(brush = ShaderBrush(shader), size = size)
    }
}

/**
 * Même bruit fractal que l'ancien vœu scellé, mais coloré en dégradé noir → brun braise →
 * rouge → orangé, filament et paillettes assortis.
 */
private const val EMBER_AGSL = """
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

float3 palette(float f, float2 q, float2 r) {
    float3 blackC = float3(0.020, 0.008, 0.006);
    float3 ember = float3(0.34, 0.08, 0.04);
    float3 redC = float3(0.62, 0.12, 0.10);
    float3 orange = float3(0.86, 0.34, 0.10);
    float3 col = mix(blackC, ember, clamp(f * 1.4, 0.0, 1.0));
    col = mix(col, redC, clamp(length(q) * 0.9, 0.0, 1.0));
    col = mix(col, orange, clamp(pow(max(r.x * 0.5 + 0.5, 0.0), 3.0), 0.0, 1.0));
    return col;
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * u_resolution) / min(u_resolution.x, u_resolution.y);
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
    marble *= mix(1.05, 0.58, rimDarken);

    float filament = exp(-pow((dist - 0.93) / 0.085, 2.0));
    float iridescence = sin(u_time * 0.35 + f * 7.0 + q.x * 3.0) * 0.5 + 0.5;
    float3 rimColor = mix(float3(1.00, 0.62, 0.28), float3(1.00, 0.30, 0.20), iridescence);
    float travel = sin(atan(uv.y, uv.x) * 2.0 - u_time * 0.55) * 0.5 + 0.5;
    marble += filament * rimColor * (0.46 + travel * 0.50);

    float2 sparkGrid = p * 4.5 + t * 1.5;
    float2 cell = floor(sparkGrid);
    float sparkleSeed = random(cell);
    float2 spot = float2(0.3, 0.3) + 0.4 * float2(random(cell + 3.1), random(cell + 7.7));
    float spotDist = length(fract(sparkGrid) - spot);
    float point = 1.0 - smoothstep(0.0, 0.2, spotDist);
    float twinkle = sin(u_time * (2.0 + sparkleSeed * 6.0) + sparkleSeed * 40.0) * 0.5 + 0.5;
    float sparkle = smoothstep(0.94, 1.0, sparkleSeed) * twinkle * point * point;
    marble += sparkle * float3(1.0, 0.75, 0.35) * 1.3;

    float inside = 1.0 - smoothstep(0.88, 1.03, dist);

    float haloD = max(dist - 0.92, 0.0) * 3.1;
    float halo = exp(-haloD * haloD);
    float2 auraWarp = p * 0.55 + t * 0.35;
    float auraNoise = fbm(auraWarp);
    float3 haloColor = mix(float3(0.55, 0.10, 0.08), float3(0.90, 0.30, 0.08), auraNoise);

    float edgeCut = 1.0 - smoothstep(1.38, 1.62, dist);

    float3 color = mix(haloColor, marble, inside);
    float alpha = clamp(inside + halo * 0.55 * (1.0 - inside), 0.0, 1.0) * edgeCut;

    return half4(color * alpha, alpha);
}
"""

/** Repli sous Android 13 : même palette, primitives Canvas ordinaires. */
@Composable
private fun SermentOrbPainted(modifier: Modifier, diameter: Dp) {
    val elapsed = rememberElapsedMillis()
    val pulse = (sin(elapsed / 380f) + 1f) / 2f
    val heartbeat = (sin(elapsed / 90f) + 1f) / 2f
    val angle = (elapsed * 0.05f) % 360f
    val sparkCount = 6

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Serment de Nen, toucher pour gérer les mots bloqués" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f

        val glowRadius = radius * 1.62f
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to Color.Transparent,
                0.45f to EMBER_HOT.copy(alpha = 0.28f + pulse * 0.12f),
                0.70f to EDGE_ORANGE.copy(alpha = 0.10f + pulse * 0.04f),
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
                0.30f to EMBER_HOT.copy(alpha = 0.7f),
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
                val sparkFlicker = (sin(elapsed / 140f + i * 2f) + 1f) / 2f
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
            fadeAt * 0.5f to EMBER_HOT.copy(alpha = alpha),
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

private val CORE_FLASH = Color(0xFFFFE8D6)
private val EMBER_HOT = Color(0xFFFF5A2E)
private val EDGE_ORANGE = Color(0xFFFF8C3B)
private val SPARK_COLOR = Color(0xFFFFD3A8)
