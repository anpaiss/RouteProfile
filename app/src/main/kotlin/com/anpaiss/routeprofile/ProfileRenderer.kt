package com.anpaiss.routeprofile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

    private val condensed: Typeface by lazy { Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL) }
    private val condensedBold: Typeface by lazy { Typeface.create(condensed, Typeface.BOLD) }

    /**
     * The instant grade beside the dot, in the profile's colour on a dark pill: to the right of the
     * dot, or to its left near the right edge; above it, but never over the top line nor below
     * the cell. Dropped when the profile area is too low for it.
     */
    private fun drawGrade(c: Canvas, g: Double, rx: Float, ry: Float, left: Float, right: Float,
                          top: Float, bottom: Float, lineH: Float, px: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensedBold; textSize = lineH * 0.8f; color = colorOf(InstantGrade.shade(g)) }
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

        // Top line, as high as the cell allows but never more than a fifth of it
        val lineH = (bmp.height * 0.24f).coerceIn(14 * px, 24 * px)
        val pad = 4 * px
        val climb = state.climb
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
            Zoom.AHEAD -> "NEXT 10 KM"
            Zoom.CLIMB -> climb?.category?.takeIf { it != ClimbCategory.UNCATEGORIZED }?.let { "CLIMB · ${it.label}" } ?: "CLIMB"
            Zoom.WHOLE -> "ROUTE"
        }
        // narrow cell: the numbers matter more than the label
        if (2 * pad + text.measureText(label) + 8 * px + numbersW <= bmp.width - 2 * pad) c.drawText(label, 2 * pad, base, text)

        // Profile area
        val left = 2 * pad
        val right = bmp.width - 2 * pad
        val top = pad + lineH + 3 * px
        val bottom = bmp.height - pad
        if (bottom - top < 8 * px) return bmp
        val (from, to) = ZoomLogic.window(state.zoom, state.posM, p.totalM, state.climb)
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
        for (b in p.bands(from, to, bandM)) {
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
            state.gradePct?.let { g -> drawGrade(c, g, rx, ry, left, right, top, bottom, lineH, px) }
        }
        return bmp
    }
}
