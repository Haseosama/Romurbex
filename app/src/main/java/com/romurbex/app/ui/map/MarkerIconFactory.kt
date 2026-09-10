package com.romurbex.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.romurbex.app.data.LocationCategory
import kotlin.math.cos
import kotlin.math.sin

/**
 * Génère les icônes de marqueur de la carte — une épingle façon Google Maps (tête ronde, pointe
 * en bas) avec un pictogramme dedans, dessinée au Canvas plutôt qu'avec les ImageVector Compose
 * (les marqueurs osmdroid attendent un android.graphics.drawable.Drawable classique, pas un
 * composable). Forme ET couleur du fond dépendent toutes les deux de la catégorie du lieu, donc
 * un type de lieu se reconnaît d'un coup d'œil même dézoomé.
 */
object MarkerIconFactory {
    // Espace logique dans lequel la forme de l'épingle est dessinée ; mis à l'échelle vers la
    // taille finale en pixels à la génération, donc indépendant de la densité d'écran.
    private const val UNIT = 64f
    private const val HEAD_CX = UNIT / 2f
    private const val HEAD_CY = 24f
    private const val HEAD_RADIUS = 18f
    private const val TIP_Y = UNIT - 1f

    // Teintes réparties uniformément sur la roue chromatique (pas par famille thématique) pour
    // garantir 13 couleurs vraiment distinctes d'un coup d'œil — l'ancienne palette regroupait
    // plusieurs catégories dans la même zone (vert olive, jaune moutarde...) et devenait illisible
    // une fois beaucoup de pins affichés côte à côte.
    private val categoryColors = mapOf(
        LocationCategory.HOPITAL to Color.parseColor("#CC3333"),
        LocationCategory.USINE to Color.parseColor("#C1652F"),
        LocationCategory.CHATEAU to Color.parseColor("#C9A227"),
        LocationCategory.FERME to Color.parseColor("#8FA83A"),
        LocationCategory.MAISON to Color.parseColor("#4C8C4A"),
        LocationCategory.MILITAIRE to Color.parseColor("#3D7A5C"),
        LocationCategory.LOISIR to Color.parseColor("#2F9E8F"),
        LocationCategory.PISCINE to Color.parseColor("#2496B5"),
        LocationCategory.ECOLE to Color.parseColor("#3C6FBF"),
        LocationCategory.GARE to Color.parseColor("#4A55A8"),
        LocationCategory.EGLISE to Color.parseColor("#7A5FB0"),
        LocationCategory.THEATRE to Color.parseColor("#9C4B6B"),
        LocationCategory.CARRIERE to Color.parseColor("#A85A6B"),
        // Brun bois foncé plutôt qu'une 15e teinte vive sur la roue chromatique déjà pleine — se
        // distingue par la clarté (très sombre) plutôt que par la teinte seule.
        LocationCategory.MOULIN to Color.parseColor("#5C3A21"),
        // Comme sur Google Maps, un lieu sans catégorie précise garde une teinte neutre.
        LocationCategory.AUTRE to Color.parseColor("#7A7268"),
    )

    private val cache = mutableMapOf<Pair<LocationCategory, Boolean>, Drawable>()
    private val clusterCache = mutableMapOf<Int, Drawable>()

    /** [large] agrandit le pin — utilisé pour les lieux proches de la position actuelle. */
    fun iconFor(context: Context, category: LocationCategory, large: Boolean = false): Drawable =
        cache.getOrPut(category to large) { buildIcon(context, category, large) }

    /** Bulle de regroupement (plusieurs lieux proches à l'écran) — cercle plein avec le nombre. */
    fun clusterIcon(context: Context, count: Int): Drawable =
        clusterCache.getOrPut(count) { buildClusterIcon(context, count) }

    private fun buildClusterIcon(context: Context, count: Int): Drawable {
        val sizePx = (34 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = sizePx / UNIT
        canvas.scale(scale, scale)

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#12100E")
        }
        canvas.drawCircle(HEAD_CX, HEAD_CY, HEAD_RADIUS + 2f, outlinePaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#C1652F")
        }
        canvas.drawCircle(HEAD_CX, HEAD_CY, HEAD_RADIUS, fillPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (count >= 100) 15f else 18f
            isFakeBoldText = true
        }
        val label = if (count > 999) "999+" else count.toString()
        val textY = HEAD_CY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, HEAD_CX, textY, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun buildIcon(context: Context, category: LocationCategory, large: Boolean): Drawable {
        val sizePx = ((if (large) 46 else 32) * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = sizePx / UNIT
        canvas.scale(scale, scale)

        val backgroundColor = categoryColors.getValue(category)

        // Le contour est une seconde épingle légèrement plus grande dessinée en dessous (donc
        // remplie, jamais tracée au Style.STROKE) : l'épingle combine un cercle et un triangle
        // en un seul Path, et tracer ce Path directement dessinerait aussi le bord interne du
        // triangle comme une ligne parasite en travers de la tête ronde.
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#12100E")
        }
        canvas.drawPath(pinPath(HEAD_RADIUS + 2f, TIP_Y), outlinePaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
        canvas.drawPath(pinPath(HEAD_RADIUS, TIP_Y), fillPaint)

        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.save()
        canvas.translate(HEAD_CX, HEAD_CY)
        canvas.scale(0.42f, 0.42f)
        drawGlyph(canvas, category, glyphPaint)
        canvas.restore()

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun pinPath(radius: Float, tipY: Float): Path = Path().apply {
        addCircle(HEAD_CX, HEAD_CY, radius, Path.Direction.CW)
        moveTo(HEAD_CX - radius, HEAD_CY)
        lineTo(HEAD_CX, tipY)
        lineTo(HEAD_CX + radius, HEAD_CY)
        close()
    }

    /** Pictogrammes dessinés autour de l'origine (0,0), à l'échelle appliquée par l'appelant. */
    private fun drawGlyph(canvas: Canvas, category: LocationCategory, paint: Paint) {
        when (category) {
            LocationCategory.USINE -> {
                canvas.drawRect(-22f, -4f, 22f, 22f, paint)
                canvas.drawRect(-14f, -22f, -6f, -4f, paint)
                canvas.drawRect(2f, -16f, 10f, -4f, paint)
            }
            LocationCategory.HOPITAL -> {
                canvas.drawRect(-6f, -20f, 6f, 20f, paint)
                canvas.drawRect(-20f, -6f, 20f, 6f, paint)
            }
            LocationCategory.MAISON, LocationCategory.AUTRE -> {
                val roof = Path().apply {
                    moveTo(-20f, 0f)
                    lineTo(0f, -22f)
                    lineTo(20f, 0f)
                    close()
                }
                canvas.drawPath(roof, paint)
                canvas.drawRect(-14f, 0f, 14f, 20f, paint)
            }
            LocationCategory.CHATEAU -> {
                canvas.drawRect(-20f, -6f, 20f, 20f, paint)
                canvas.drawRect(-20f, -18f, -12f, -6f, paint)
                canvas.drawRect(-4f, -18f, 4f, -6f, paint)
                canvas.drawRect(12f, -18f, 20f, -6f, paint)
            }
            LocationCategory.EGLISE -> {
                canvas.drawRect(-4f, -22f, 4f, 20f, paint)
                canvas.drawRect(-14f, -8f, 14f, 0f, paint)
            }
            LocationCategory.ECOLE -> {
                val cap = Path().apply {
                    moveTo(-22f, -4f)
                    lineTo(0f, -14f)
                    lineTo(22f, -4f)
                    lineTo(0f, 6f)
                    close()
                }
                canvas.drawPath(cap, paint)
                canvas.drawRect(-1.5f, -4f, 1.5f, 16f, paint)
            }
            LocationCategory.MILITAIRE -> canvas.drawPath(starPath(20f, 8f), paint)
            LocationCategory.LOISIR -> {
                canvas.drawCircle(0f, -8f, 14f, paint)
                canvas.drawRect(-4f, 4f, 4f, 20f, paint)
            }
            LocationCategory.CARRIERE -> {
                val peak1 = Path().apply {
                    moveTo(-20f, 12f); lineTo(-4f, -16f); lineTo(10f, 12f); close()
                }
                val peak2 = Path().apply {
                    moveTo(-2f, 12f); lineTo(11f, -8f); lineTo(20f, 12f); close()
                }
                canvas.drawPath(peak1, paint)
                canvas.drawPath(peak2, paint)
            }
            LocationCategory.FERME -> {
                canvas.drawRoundRect(RectF(-10f, -6f, 10f, 20f), 5f, 5f, paint)
                canvas.drawCircle(0f, -10f, 10f, paint)
            }
            LocationCategory.GARE -> {
                canvas.drawRoundRect(RectF(-18f, -14f, 18f, 10f), 8f, 8f, paint)
                canvas.drawCircle(-10f, 16f, 5f, paint)
                canvas.drawCircle(10f, 16f, 5f, paint)
            }
            LocationCategory.THEATRE -> {
                canvas.drawRect(-18f, -2f, 18f, 20f, paint)
                canvas.drawArc(RectF(-18f, -22f, 18f, 14f), 180f, 180f, true, paint)
            }
            LocationCategory.PISCINE -> {
                canvas.drawRoundRect(RectF(-20f, -12f, 20f, 12f), 10f, 10f, paint)
            }
            LocationCategory.MOULIN -> {
                // Tour
                canvas.drawPath(
                    Path().apply {
                        moveTo(-4f, -6f); lineTo(4f, -6f); lineTo(6f, 20f); lineTo(-6f, 20f); close()
                    },
                    paint,
                )
                // 4 pales en croix autour du moyeu
                canvas.drawPath(
                    Path().apply {
                        moveTo(0f, -8f); lineTo(-3f, -22f); lineTo(7f, -16f); close()
                        moveTo(0f, -8f); lineTo(14f, -10f); lineTo(8f, 0f); close()
                        moveTo(0f, -8f); lineTo(3f, 6f); lineTo(-7f, 0f); close()
                        moveTo(0f, -8f); lineTo(-14f, -6f); lineTo(-8f, -16f); close()
                    },
                    paint,
                )
            }
        }
    }

    private fun starPath(outerRadius: Float, innerRadius: Float): Path {
        val path = Path()
        val points = 5
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = Math.PI / points * i - Math.PI / 2
            val x = (radius * cos(angle)).toFloat()
            val y = (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
