package com.jesse.nen.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Écran de blocage affiché par-dessus le navigateur quand un vœu scellé est reconnu.
 *
 * Fenêtre de type TYPE_ACCESSIBILITY_OVERLAY : réservée aux services d'accessibilité, elle
 * s'affiche au-dessus des autres apps SANS la permission « Affichage par-dessus les autres
 * applications ». Le système la retire d'office si le service meurt.
 *
 * L'overlay est volontairement TACTILE (pas de FLAG_NOT_TOUCHABLE) : il reste à l'écran
 * jusqu'à ce que l'utilisateur le touche, et ce toucher déclenche le retour à l'accueil.
 */
class AuraOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: AuraView? = null

    val isShowing: Boolean get() = view != null

    /** @param onDismiss appelé quand l'utilisateur touche l'écran (retour à l'accueil). */
    fun show(onDismiss: () -> Unit) {
        if (view != null || windowManager == null) return

        val overlay = AuraView(context) {
            hide()
            onDismiss()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE : on ne vole pas le focus clavier. On NE met PAS NOT_TOUCHABLE,
            // sinon l'overlay ne recevrait jamais le toucher qui doit le congédier.
            // LAYOUT_NO_LIMITS + LAYOUT_IN_SCREEN : couvrir aussi les barres système.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Sans ça, l'overlay s'arrête sous la barre d'état et laisse une bande claire.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        overlay.fitsSystemWindows = false

        // Le système peut retirer la fenêtre sans passer par hide() (service reconnecté,
        // jeton invalidé). Sans ce guet, la référence resterait non nulle et PLUS AUCUN
        // blocage ne s'afficherait ensuite.
        overlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                if (view === v) view = null
            }
        })

        try {
            windowManager.addView(overlay, params)
            view = overlay
        } catch (e: Exception) {
            Log.w(TAG, "Affichage de l'overlay impossible : ${e.message}")
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager?.removeView(current)
        } catch (e: Exception) {
            Log.w(TAG, "Retrait de l'overlay impossible : ${e.message}")
        }
    }

    private companion object {
        const val TAG = "NenAura"
    }
}

/**
 * Le rendu : coulures d'aura sombres sur fond froid, dans l'esprit des plans de Gon enragé.
 *
 * Tout est dessiné à la main dans onDraw plutôt qu'en Compose : monter Compose dans une
 * fenêtre possédée par un Service impose de câbler à la main un LifecycleOwner, un
 * SavedStateRegistryOwner et un ViewModelStoreOwner, pour une animation de quelques secondes.
 */
@SuppressLint("ViewConstructor")
private class AuraView(
    context: Context,
    private val onTouched: () -> Unit
) : View(context) {

    private val startedAt = SystemClock.uptimeMillis()
    private val random = Random(SystemClock.uptimeMillis())

    private val dripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        alpha = 90
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var drips: List<Drip> = emptyList()
    private var smears: List<Smear> = emptyList()

    /**
     * Une coulure. Son dégradé est construit une seule fois, en coordonnées locales partant
     * de 0 : on le repositionne ensuite avec canvas.translate, qui déplace la géométrie ET
     * le shader ensemble. Muter un shader partagé entre deux draws du même frame donnerait
     * un résultat faux, l'affichage matériel enregistrant les commandes avant de les rejouer.
     */
    private class Drip(
        val x: Float,
        val width: Float,
        val length: Float,
        val speed: Float,
        val offset: Float,
        val alpha: Int,
        val wobble: Float,
        val wobblePhase: Float,
        val shader: LinearGradient
    )

    private class Smear(val x: Float, val width: Float, val alpha: Int, val shader: LinearGradient)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        textPaint.textSize = w * 0.085f
        hintPaint.textSize = w * 0.032f

        // Coulures : longueurs et vitesses très dispersées, pour un ruissellement irrégulier
        // plutôt qu'une pluie régulière.
        drips = List(DRIP_COUNT) {
            val pale = random.nextFloat() < 0.12f
            val length = h * (0.10f + random.nextFloat() * 0.55f)
            val width = w * (0.002f + random.nextFloat() * 0.012f)
            val core = if (pale) PALE_CORE else DRIP_CORE
            val mid = if (pale) PALE_MID else DRIP_MID
            Drip(
                x = random.nextFloat() * w,
                width = width,
                length = length,
                speed = h * (0.06f + random.nextFloat() * 0.22f) / 1000f, // px par ms
                offset = random.nextFloat() * (h + length),
                alpha = if (pale) 70 + random.nextInt(60) else 130 + random.nextInt(125),
                wobble = w * (0.001f + random.nextFloat() * 0.006f),
                wobblePhase = random.nextFloat() * 6.283f,
                shader = LinearGradient(
                    0f, 0f, 0f, length,
                    intArrayOf(Color.TRANSPARENT, mid, core, core),
                    floatArrayOf(0f, 0.55f, 0.93f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
        }

        // Nappes larges et floues en arrière-plan : la « paroi » sur laquelle l'aura coule.
        smears = List(SMEAR_COUNT) {
            val width = w * (0.12f + random.nextFloat() * 0.3f)
            val x = random.nextFloat() * w
            Smear(
                x = x,
                width = width,
                alpha = 40 + random.nextInt(70),
                shader = LinearGradient(
                    x - width / 2f, 0f, x + width / 2f, 0f,
                    intArrayOf(Color.TRANSPARENT, SMEAR_COLOR, Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
            )
        }

        vignettePaint.shader = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, VIGNETTE_COLOR),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val elapsed = (SystemClock.uptimeMillis() - startedAt).toFloat()
        // Fondu d'entrée : l'apparition doit être brutale, pas douce.
        val fade = (elapsed / FADE_IN_MS).coerceIn(0f, 1f)

        // Fond OPAQUE une fois le fondu terminé : un écran de blocage qui laisse deviner la
        // page bloquée (suggestions du navigateur, titres de résultats) manque sa cible.
        scrimPaint.color = SCRIM_COLOR
        scrimPaint.alpha = 255.scaleBy(fade)
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // La respiration porte désormais sur les nappes, le fond ne pouvant plus varier.
        val breathe = 0.5f + 0.5f * sin(elapsed / 900f)
        for ((i, smear) in smears.withIndex()) {
            smearPaint.shader = smear.shader
            smearPaint.alpha = (smear.alpha * (0.75f + 0.25f * breathe)).toInt().scaleBy(fade)
            val drift = sin(elapsed / (2200f + i * 260f)) * w * 0.02f
            canvas.save()
            canvas.translate(drift, 0f)
            canvas.drawRect(smear.x - smear.width, 0f, smear.x + smear.width, h, smearPaint)
            canvas.restore()
        }

        for (drip in drips) {
            // Descente continue, rebouclée au-dessus de l'écran.
            val travel = (drip.offset + elapsed * drip.speed) % (h + drip.length)
            val top = travel - drip.length
            val sway = sin(elapsed / 700f + drip.wobblePhase) * drip.wobble

            dripPaint.shader = drip.shader
            dripPaint.alpha = drip.alpha.scaleBy(fade)

            canvas.save()
            canvas.translate(drip.x + sway, top)
            canvas.drawRect(0f, 0f, drip.width, drip.length, dripPaint)
            // Goutte de tête : rend la coulure lourde plutôt que filiforme.
            canvas.drawCircle(drip.width / 2f, drip.length, drip.width * 0.9f, dripPaint)
            canvas.restore()
        }

        canvas.drawRect(0f, 0f, w, h, vignettePaint)

        drawMessage(canvas, w, h, elapsed, fade)

        postInvalidateOnAnimation()
    }

    private fun drawMessage(canvas: Canvas, w: Float, h: Float, elapsed: Float, fade: Float) {
        // Tremblement : deux sinusoïdes de périodes non multiples, pour éviter un va-et-vient
        // mécaniquement régulier.
        val jitterX = sin(elapsed / 47f) * w * 0.0035f + sin(elapsed / 111f) * w * 0.002f
        val jitterY = sin(elapsed / 63f) * w * 0.0030f

        val lineGap = textPaint.textSize * 1.12f
        val baseY = h / 2f

        textPaint.alpha = 255.scaleBy(fade)
        textPaint.setShadowLayer(w * 0.05f, 0f, 0f, Color.BLACK)
        canvas.drawText(LINE_1, w / 2f + jitterX, baseY - lineGap * 0.35f + jitterY, textPaint)
        canvas.drawText(LINE_2, w / 2f + jitterX, baseY + lineGap * 0.75f + jitterY, textPaint)
        textPaint.clearShadowLayer()

        // L'overlay ne se referme qu'au toucher : sans cette ligne, rien ne l'indique.
        hintPaint.alpha = 90.scaleBy(fade)
        canvas.drawText(HINT, w / 2f, h * 0.78f, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onTouched()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTouched()
        return true
    }

    private fun Int.scaleBy(factor: Float): Int = (this * factor).toInt().coerceIn(0, 255)

    private companion object {
        const val DRIP_COUNT = 80
        const val SMEAR_COUNT = 7
        const val FADE_IN_MS = 220f

        const val SCRIM_COLOR = 0xFF080B10.toInt()
        const val SMEAR_COLOR = 0xFF1B2836.toInt()
        const val VIGNETTE_COLOR = 0xF203050A.toInt()
        const val DRIP_CORE = 0xFF03050A.toInt()
        const val DRIP_MID = 0xFF0C1622.toInt()
        const val PALE_CORE = 0xFFB9CEDD.toInt()
        const val PALE_MID = 0xFF44586B.toInt()
        const val TEXT_COLOR = 0xFFEFF4F7.toInt()

        const val LINE_1 = "Concentre-toi"
        const val LINE_2 = "sur ton Nen"
        const val HINT = "touche l'écran pour revenir"
    }
}
