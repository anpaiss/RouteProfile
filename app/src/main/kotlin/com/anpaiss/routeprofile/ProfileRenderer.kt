package com.anpaiss.routeprofile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.max

/**
 * The field as one bitmap of the cell's size. Dark like the native fields on the map page,
 * so the gradient colours read the same with the Karoo in light or dark mode.
 * Top line: small view label on the left, "km · +m" still to go on the right; the rest is the profile.
 */
object ProfileRenderer {

    const val EASY = 0xFF4CAF50.toInt()
    const val MODERATE = 0xFFD7CF2C.toInt()
    const val HARD = 0xFFF5961E.toInt()
    const val STEEP = 0xFFE13C28.toInt()
    const val VERY_STEEP = 0xFF9C1C2A.toInt()
    const val DESCENT = 0xFF6E96C8.toInt()
    const val DONE = 0xFF5A5A5A.toInt()
    const val BG = 0xFF000000.toInt()
    const val LABEL = 0xFFB0B0B0.toInt()
    const val RIDER = 0xFF1E88FF.toInt()

    fun colorOf(s: Shade) = when (s) {
        Shade.DESCENT -> DESCENT; Shade.EASY -> EASY; Shade.MODERATE -> MODERATE
        Shade.HARD -> HARD; Shade.STEEP -> STEEP; Shade.VERY_STEEP -> VERY_STEEP
    }

    /** Smooth colours: each class colour sits at the middle of its range, the grades between blend. */
    private val ANCHORS = listOf(-6.0 to DESCENT, -2.5 to DESCENT, -1.0 to EASY, 1.5 to EASY, 4.5 to MODERATE,
        7.5 to HARD, 10.5 to STEEP, 13.5 to VERY_STEEP)

    fun smoothColorOf(g: Double): Int {
        if (g <= ANCHORS.first().first) return ANCHORS.first().second
        for (i in 1 until ANCHORS.size) {
            val (g1, c1) = ANCHORS[i]
            if (g <= g1) {
                val (g0, c0) = ANCHORS[i - 1]
                val t = ((g - g0) / (g1 - g0)).toFloat()
                fun ch(sh: Int) = (((c0 shr sh) and 0xFF) + (((c1 shr sh) and 0xFF) - ((c0 shr sh) and 0xFF)) * t).toInt()
                return Color.rgb(ch(16), ch(8), ch(0))
            }
        }
        return ANCHORS.last().second
    }

    private val condensed: Typeface by lazy { Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL) }
    private val condensedBold: Typeface by lazy { Typeface.create(condensed, Typeface.BOLD) }

    /**
     * The instant grade beside the dot, in the profile's colour on a dark pill: to the right of the
     * dot, or to its left near the right edge; above it, but never over the top line nor below
     * the cell. Dropped when the profile area is too low for it.
     */
    private fun drawGrade(c: Canvas, g: Double, rx: Float, ry: Float, left: Float, right: Float,
                          top: Float, bottom: Float, lineH: Float, px: Float, smooth: Boolean) {
        val col = if (smooth) smoothColorOf(g) else colorOf(InstantGrade.shade(g))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensedBold; textSize = lineH * 0.8f; color = col }
        val label = InstantGrade.text(g)
        val tw = p.measureText(label)
        val th = p.textSize
        val padX = 3 * px; val padY = 1.5f * px
        if (bottom - top < th + 2 * padY) return
        val gap = 8 * px
        var x = rx + gap
        if (x + tw + padX > right) x = rx - gap - tw
        x = x.coerceIn(left + padX, right - tw - padX)
        val base = (ry - 6 * px).coerceIn(top + th + padY, bottom - padY)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
        c.drawRoundRect(RectF(x - padX, base - th * 0.85f - padY, x + tw + padX, base + th * 0.2f + padY), 4 * px, 4 * px, bg)
        c.drawText(label, x, base, p)
    }

    fun render(state: ProfileState, w: Int, h: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 40), max(h, 30), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val px = density   // 1 dp
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        c.drawRoundRect(RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), 6 * px, 6 * px, bg)

        val p = state.profile
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensed }
        if (p == null) {
            text.color = LABEL; text.textSize = 19 * px; text.textAlign = Paint.Align.CENTER
            c.drawText("No route", bmp.width / 2f, bmp.height / 2f + 7 * px, text)
            return bmp
        }

        // Top line, as high as the cell allows but never more than a fifth of it (also sizes the grade pill)
        val lineH = (bmp.height * 0.24f).coerceIn(14 * px, 24 * px)
        val pad = 4 * px
        val climb = state.climb
        if (!state.graphOnly) {
        val numbers = if (state.zoom == Zoom.CLIMB && climb != null) {
            // on the climb: what is left to the top; approaching: the whole climb
            val n = ClimbNumbers.of(p, climb, state.posM)
            String.format(Locale.US, "%.1f km · +%d m · %d %%", n.distanceM / 1000.0, Math.round(n.ascentM), Math.round(n.gradePct))
        } else String.format(Locale.US, "%.1f km · +%d m",
            (state.remainingM ?: 0.0) / 1000.0, Math.round(state.remainingAscentM ?: 0.0))
        text.typeface = condensedBold; text.color = Color.WHITE; text.textSize = lineH * 0.92f
        text.textAlign = Paint.Align.RIGHT
        val base = pad + lineH * 0.82f
        c.drawText(numbers, bmp.width - 2 * pad, base, text)
        val numbersW = text.measureText(numbers)
        text.typeface = condensed; text.color = LABEL; text.textSize = lineH * 0.62f
        text.textAlign = Paint.Align.LEFT
        val label = when (state.zoom) {
            Zoom.CLIMB -> climb?.category?.takeIf { it != ClimbCategory.UNCATEGORIZED }?.let { "CLIMB · ${it.label}" } ?: "CLIMB"
            Zoom.WHOLE -> "ROUTE"
            else -> state.zoom.label
        }
        // narrow cell: the numbers matter more than the label
        if (2 * pad + text.measureText(label) + 8 * px + numbersW <= bmp.width - 2 * pad) c.drawText(label, 2 * pad, base, text)
        }

        // Profile area: below the top line, or the whole cell when the graph is alone
        val left = 2 * pad
        val right = bmp.width - 2 * pad
        val top = if (state.graphOnly) pad + 2 * px else pad + lineH + 3 * px
        val bottom = bmp.height - pad
        if (bottom - top < 8 * px) return bmp
        val (from, to) = ZoomLogic.window(state.zoom, state.posM, p.totalM, state.climb, state.climbSpanM)
        val span = max(1.0, to - from)
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        var m = from
        while (m <= to) { val e = p.elevationAt(m); lo = minOf(lo, e); hi = maxOf(hi, e); m += p.stepM }
        if (hi - lo < 60.0) { val mid = (hi + lo) / 2; lo = mid - 30; hi = mid + 30 }
        val padE = (hi - lo) * 0.08
        lo -= padE; hi += padE
        fun x(d: Double) = (left + (d - from) / span * (right - left)).toFloat()
        fun y(e: Double) = (bottom - (e - lo) / (hi - lo) * (bottom - top)).toFloat()

        val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
        val pos = state.posM
        // ~90 bands across the cell: 100 m when zoomed, ~500 m on a 44 km route (a barcode otherwise)
        val bandM = Math.ceil(span / Profile.BANDS_PER_VIEW / 100.0) * 100.0
        val bands = p.bands(from, to, bandM)
        // the area under the line from a to b (a < b), as one closed path
        fun area(a: Double, b: Double): Path {
            val path = Path(); path.moveTo(x(a), bottom)
            var dd = a
            while (dd < b) { path.lineTo(x(dd), y(p.elevationAt(dd))); dd += p.stepM / 2 }
            path.lineTo(x(b), y(p.elevationAt(b))); path.lineTo(x(b), bottom); path.close()
            return path
        }
        if (state.smooth && bands.size >= 2) {
            // one fill whose colour runs through every band's grade, from band middle to band middle
            val xs = FloatArray(bands.size) { i -> ((x((bands[i].fromM + bands[i].toM) / 2) - left) / (right - left)).coerceIn(0f, 1f) }
            for (i in 1 until xs.size) if (xs[i] < xs[i - 1]) xs[i] = xs[i - 1]
            val cs = IntArray(bands.size) { i -> smoothColorOf(bands[i].gradePct) }
            fill.shader = LinearGradient(left, 0f, right, 0f, cs, xs, Shader.TileMode.CLAMP)
            c.drawPath(area(from, to), fill)
            fill.shader = null
            if (pos != null && pos > from) { fill.color = DONE; c.drawPath(area(from, minOf(pos, to)), fill) }
        } else for (b in bands) {
            fill.color = if (pos != null && b.toM <= pos) DONE else colorOf(b.shade)
            val path = Path()
            path.moveTo(x(b.fromM), bottom)
            var d = b.fromM
            while (d < b.toM) { path.lineTo(x(d), y(p.elevationAt(d))); d += p.stepM / 2 }
            path.lineTo(x(b.toM), y(p.elevationAt(b.toM)))
            path.lineTo(x(b.toM), bottom)
            path.close()
            c.drawPath(path, fill)
            // a band split by the rider: its done part grey
            if (pos != null && b.fromM < pos && pos < b.toM) {
                val done = Path(); done.moveTo(x(b.fromM), bottom)
                var dd = b.fromM
                while (dd < pos) { done.lineTo(x(dd), y(p.elevationAt(dd))); dd += p.stepM / 2 }
                done.lineTo(x(pos), y(p.elevationAt(pos))); done.lineTo(x(pos), bottom); done.close()
                fill.color = DONE; c.drawPath(done, fill)
            }
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 1.6f * px }
        val outline = Path()
        var d = from
        outline.moveTo(x(d), y(p.elevationAt(d)))
        while (d < to) { d = minOf(to, d + p.stepM / 2); outline.lineTo(x(d), y(p.elevationAt(d))) }
        c.drawPath(outline, line)

        if (pos != null && pos in from..to) {
            val rx = x(pos)
            line.strokeWidth = 1.3f * px
            c.drawLine(rx, top, rx, bottom, line)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RIDER }
            val r = 4.5f * px
            val ry = y(p.elevationAt(pos))
            c.drawCircle(rx, ry, r + 1.5f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            c.drawCircle(rx, ry, r, dot)
            state.gradePct?.let { g -> drawGrade(c, g, rx, ry, left, right, top, bottom, lineH, px, state.smooth) }
        }
        return bmp
    }
}
