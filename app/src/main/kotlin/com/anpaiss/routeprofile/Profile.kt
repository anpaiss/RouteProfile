package com.anpaiss.routeprofile

import kotlin.math.max
import kotlin.math.min

/** Gradient classes of the fill, the same thresholds as the mockup Andrea approved (13/11/2026). */
enum class Shade { DESCENT, EASY, MODERATE, HARD, STEEP, VERY_STEEP }

/**
 * Climb categories by score = length (m) × average grade (%), Strava/UCI style
 * (Andrea, 13/11/2026). The Karoo's climbs carry no category, so we compute it.
 */
enum class ClimbCategory(val label: String, val minScore: Double) {
    UNCATEGORIZED("Uncategorized", 0.0),
    CAT4("Cat 4", 8_000.0),
    CAT3("Cat 3", 16_000.0),
    CAT2("Cat 2", 32_000.0),
    CAT1("Cat 1", 64_000.0),
    HC("HC", 80_000.0);

    companion object {
        fun of(lengthM: Double, gradePct: Double): ClimbCategory {
            val score = lengthM * gradePct
            return entries.lastOrNull { score >= it.minScore } ?: UNCATEGORIZED
        }
    }
}

/** A climb as the Karoo lists it on the route (start along the route and length, metres; average grade, %). */
data class ClimbSpan(val startM: Double, val lengthM: Double, val gradePct: Double = 0.0) {
    val endM get() = startM + lengthM
    val category get() = ClimbCategory.of(lengthM, gradePct)
}

/**
 * The route's elevation along its distance, resampled every [STEP_M] metres and lightly
 * smoothed, so that the colours and the climb still to do are not driven by GPS-elevation noise.
 */
class Profile private constructor(val stepM: Double, val ele: DoubleArray, private val raw: DoubleArray) {

    val totalM: Double get() = (ele.size - 1) * stepM

    companion object {
        /** Colour bands per window: about this many across the cell, never shorter than [GRADE_M]. */
        const val BANDS_PER_VIEW = 90
        const val STEP_M = 50.0
        /** Gradients are judged over this distance: one colour band per 100 m. */
        const val GRADE_M = 100.0
        private const val SMOOTH_HALF = 2   // moving average over 5 samples = 250 m

        fun shadeOf(gradePct: Double): Shade = when {
            gradePct < -2.0 -> Shade.DESCENT
            gradePct < 3.0 -> Shade.EASY
            gradePct < 6.0 -> Shade.MODERATE
            gradePct < 9.0 -> Shade.HARD
            gradePct < 12.0 -> Shade.STEEP
            else -> Shade.VERY_STEEP
        }

        /** From the Karoo's (distance, elevation) samples; null when there are not enough. */
        fun of(samples: List<Pair<Double, Double>>): Profile? {
            val s = samples.filter { it.first.isFinite() && it.second.isFinite() }.sortedBy { it.first }
            if (s.size < 2) return null
            val start = s.first().first
            val total = s.last().first - start
            if (total < STEP_M) return null
            val n = (total / STEP_M).toInt() + 1
            val raw = DoubleArray(n)
            var j = 0
            for (i in 0 until n) {
                val x = start + i * STEP_M
                while (j < s.size - 2 && s[j + 1].first < x) j++
                val (d0, e0) = s[j]
                val (d1, e1) = s[j + 1]
                raw[i] = if (d1 <= d0) e0 else e0 + (e1 - e0) * ((x - d0) / (d1 - d0)).coerceIn(0.0, 1.0)
            }
            val sm = DoubleArray(n) { i ->
                val a = max(0, i - SMOOTH_HALF)
                val b = min(n - 1, i + SMOOTH_HALF)
                var sum = 0.0
                for (k in a..b) sum += raw[k]
                sum / (b - a + 1)
            }
            return Profile(STEP_M, sm, raw)
        }
    }

    fun elevationAt(m: Double): Double {
        val x = (m / stepM).coerceIn(0.0, (ele.size - 1).toDouble())
        val i = x.toInt().coerceAtMost(ele.size - 2)
        val t = x - i
        return ele[i] + (ele[i + 1] - ele[i]) * t
    }

    /**
     * Ascent from [fromM] to the end, metres. Counted on the Karoo's own elevation resampled
     * every 50 m, not on the smoothed drawing: the smoothing rounded summits off and gave
     * 886 m on a route Komoot puts at 963 m (Valmaron, 13/11/2026).
     */
    fun ascentFrom(fromM: Double): Double {
        val x = (fromM / stepM).coerceIn(0.0, (raw.size - 1).toDouble())
        val i0 = x.toInt().coerceAtMost(raw.size - 2)
        val here = raw[i0] + (raw[i0 + 1] - raw[i0]) * (x - i0)
        var up = max(0.0, raw[i0 + 1] - here)
        for (i in i0 + 1 until raw.size - 1) up += max(0.0, raw[i + 1] - raw[i])
        return up
    }

    /** One band per [bandM] (at least [GRADE_M]) in [fromM, toM]: start, end and gradient class. */
    fun bands(fromM: Double, toM: Double, bandM: Double = GRADE_M): List<Band> {
        val len = max(GRADE_M, bandM)
        val out = ArrayList<Band>()
        var a = (fromM / len).toInt() * len
        while (a < toM && a < totalM) {
            val b = min(a + len, totalM)
            val g = (elevationAt(b) - elevationAt(a)) / (b - a) * 100.0
            out.add(Band(max(a, fromM), min(b, toM), shadeOf(g)))
            a = b
        }
        return out
    }

    data class Band(val fromM: Double, val toM: Double, val shade: Shade)
}

enum class Zoom { WHOLE, AHEAD }

object ZoomLogic {
    /** The zoomed view starts this far behind the rider and shows [AHEAD_M] in all. */
    const val BEHIND_M = 1_000.0
    const val AHEAD_M = 10_000.0
    /** Auto-zoom: a climb that starts within this distance, or one we are on. */
    const val CLIMB_NEAR_M = 2_000.0

    /** Zoom on a climb near or under the rider whose category is in [enabled]. */
    fun auto(posM: Double?, climbs: List<ClimbSpan>, enabled: Set<ClimbCategory> = ClimbCategory.entries.toSet()): Zoom {
        if (posM == null) return Zoom.WHOLE
        return if (climbs.any { it.category in enabled && posM >= it.startM - CLIMB_NEAR_M && posM <= it.endM })
            Zoom.AHEAD else Zoom.WHOLE
    }

    /** Distance window [from, to] shown for [zoom]. */
    /** Before the first position the zoomed view shows the first 10 km. */
    fun window(zoom: Zoom, posM: Double?, totalM: Double): Pair<Double, Double> {
        if (zoom == Zoom.WHOLE || totalM <= AHEAD_M) return 0.0 to totalM
        var from = (posM ?: BEHIND_M) - BEHIND_M
        var to = from + AHEAD_M
        if (from < 0) { to -= from; from = 0.0 }
        if (to > totalM) { from -= to - totalM; to = totalM }
        return max(0.0, from) to to
    }
}
