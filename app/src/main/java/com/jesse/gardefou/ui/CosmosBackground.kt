package com.jesse.gardefou.ui

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jesse.gardefou.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le fond de l'app : l'anneau iridescent, animé de façon organique.
 *
 * L'image est déformée par un `RuntimeShader` (Android 13+) : les coordonnées échantillonnées
 * ondulent le long de l'anneau, si bien que le fil de lumière serpente et respire au lieu de
 * rester figé. La déformation est modulée par la distance au centre — forte sur l'anneau,
 * presque nulle sur les volutes du fond, qui se contentent de tourner très lentement.
 *
 * Trois sinus déphasés plutôt qu'un bruit fractal : sur toute la surface de l'écran, six
 * octaves de bruit par pixel et par frame coûteraient cher pour un résultat à peine différent.
 */
@Composable
fun CosmosBackground(modifier: Modifier = Modifier) {
    val image = ImageBitmap.imageResource(R.drawable.nen_cosmos)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        CosmosFluid(image, modifier)
    } else {
        CosmosStill(image, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun CosmosFluid(image: ImageBitmap, modifier: Modifier) {
    val elapsed = rememberElapsedMillis()
    val shader = remember { RuntimeShader(COSMOS_AGSL) }
    val bitmap = remember(image) { image.asAndroidBitmap() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // L'image est carrée, l'écran ne l'est pas : on la cadre en « cover » (elle couvre
        // toute la surface, les débords sont rognés) et l'anneau reste centré.
        val scale = max(size.width / bitmap.width, size.height / bitmap.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (size.width - bitmap.width * scale) / 2f,
                (size.height - bitmap.height * scale) / 2f
            )
        }
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }

        shader.setInputShader("u_image", bitmapShader)
        shader.setFloatUniform("u_center", size.width / 2f, size.height / 2f)
        // Rayon du fil de l'anneau et bord extérieur de son halo, relevés sur le PNG. Le
        // premier centre l'ondulation sur le fil, le second dit où la poussière peut
        // commencer sans empiéter sur la sphère noire.
        shader.setFloatUniform("u_ringRadius", bitmap.width * 0.19f * scale)
        shader.setFloatUniform("u_ringOuter", bitmap.width * 0.24f * scale)
        shader.setFloatUniform("u_time", elapsed / 1000f)

        drawRect(brush = ShaderBrush(shader), size = size)
    }
}

private const val COSMOS_AGSL = """
uniform shader u_image;
uniform float2 u_center;
uniform float u_ringRadius;
uniform float u_ringOuter;
uniform float u_time;

/**
 * Une copie des volutes de l'image, recentrée ailleurs à l'écran.
 *
 * Rien n'est calculé ni déformé : ce sont les nébulosités de l'image d'origine, échantillonnées
 * autour d'un autre centre, tournées et redimensionnées. Leur anneau et leur sphère noire sont
 * effacés, on ne garde que la matière qui flotte autour — c'est elle qu'il s'agit d'étendre au
 * reste de l'écran.
 *
 * @param d      position du pixel, relative au centre de l'écran
 * @param offset centre de cette copie, lui aussi relatif au centre de l'écran
 */
half3 nebulaCopy(float2 d, float2 offset, float angle, float zoom) {
    float2 v = d - offset;
    float ca = cos(angle);
    float sa = sin(angle);
    v = float2(v.x * ca - v.y * sa, v.x * sa + v.y * ca) / zoom;

    // Fondu large : une coupure nette laisserait voir le disque de la copie.
    float keep = smoothstep(u_ringOuter * 0.90, u_ringOuter * 1.55, length(v));
    return u_image.eval(u_center + v).rgb * keep;
}

half4 main(float2 fragCoord) {
    float2 d = fragCoord - u_center;
    float r = length(d);
    float angle = atan(d.y, d.x);
    float2 radial = r > 0.001 ? d / r : float2(1.0, 0.0);

    // Rotation d'ensemble, très lente : la nébuleuse dérive sans qu'on voie tourner l'image.
    float spin = u_time * 0.018;
    float ca = cos(spin);
    float sa = sin(spin);
    float2 turned = float2(d.x * ca - d.y * sa, d.x * sa + d.y * ca);

    // Ondulation le long de l'anneau : trois harmoniques de périodes premières entre elles,
    // qui ne se remettent donc jamais en phase — le fil ne « bat » pas la mesure.
    float wobble =
        sin(angle * 3.0 + u_time * 0.62) * 0.55 +
        sin(angle * 5.0 - u_time * 0.41) * 0.30 +
        sin(angle * 8.0 + u_time * 0.87) * 0.15;

    // Amplitude serrée sur le fil lui-même. Étalée plus largement, elle déformait aussi les
    // volutes autour, et tout l'espace semblait se tordre.
    // L'amplitude est faible (2 %) : au-delà, le fil ne serpente plus, il se déforme en
    // polygone et l'anneau perd sa rondeur.
    float band = exp(-pow((r - u_ringRadius) / (u_ringRadius * 0.32), 2.0));
    float amplitude = u_ringRadius * 0.022 * band;

    float2 tangent = float2(-radial.y, radial.x);
    float2 warped = turned
        + radial * wobble * amplitude
        + tangent * sin(angle * 4.0 - u_time * 0.5) * amplitude * 0.6;

    half4 color = u_image.eval(u_center + warped);

    // Respiration lumineuse, légèrement plus marquée sur l'anneau.
    float breath = 0.93 + 0.07 * sin(u_time * 0.45) + 0.05 * band * sin(u_time * 0.9);

    // Gain multiplicatif : il éclaircit la matière sans jamais lever le noir, qui reste noir.
    half3 rgb = color.rgb * breath * 1.30;

    // Trois copies des volutes, centrées hors écran de part et d'autre, qui dérivent et
    // tournent lentement chacune à son rythme. Elles se recouvrent sans jamais se remettre en
    // phase, ce qui les empêche de se lire comme la même image répétée.
    //
    // Rien n'est ajouté là où l'image ne porte rien : ces copies sont noires entre leurs
    // volutes, donc le fond reste noir, et elles n'introduisent aucun voile.
    float drift = u_time * 0.010;
    half3 extra = half3(0.0);
    extra += nebulaCopy(d, float2(-380.0, -1560.0 + sin(drift) * 90.0), 0.7 + drift, 1.20) * 0.62;
    extra += nebulaCopy(d, float2(420.0, 1620.0 - cos(drift * 0.8) * 80.0), 2.4 - drift * 0.7, 1.05) * 0.58;
    extra += nebulaCopy(d, float2(-1250.0 + cos(drift * 1.3) * 70.0, 620.0), 4.1 + drift * 0.5, 1.35) * 0.50;

    // Les copies ne pénètrent pas la sphère noire : elles commencent au-delà de l'anneau.
    float outside = smoothstep(u_ringOuter * 0.98, u_ringOuter * 1.45, r);
    rgb += extra * outside;

    return half4(rgb, color.a);
}
"""

/**
 * Repli sous Android 13 : pas de `RuntimeShader`, donc pas de déformation par pixel. L'image
 * tourne très lentement et respire en échelle — l'esprit y est, le serpentement en moins.
 */
@Composable
private fun CosmosStill(image: ImageBitmap, modifier: Modifier) {
    val elapsed = rememberElapsedMillis()

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color.Black, size = size)
        val breath = 1f + 0.02f * sin(elapsed / 2600f)
        // Un peu plus large que le strict nécessaire : la rotation ferait sinon apparaître
        // les coins vides de l'image carrée.
        val scale = max(size.width / image.width, size.height / image.height) * 1.45f * breath
        val width = (image.width * scale).roundToInt()
        val height = (image.height * scale).roundToInt()
        rotate(degrees = elapsed / 1000f * 1.03f) {
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    ((size.width - width) / 2f).roundToInt(),
                    ((size.height - height) / 2f).roundToInt()
                ),
                dstSize = IntSize(width, height)
            )
        }
    }
}
