package com.anpaiss.routeprofile

import kotlin.math.max
import kotlin.math.min

/** Gradient classes of the fill, the same thresholds as the mockup Andrea approved (13/11/2026). */
enum class Shade { DESCENT, EASY, MODERATE, HARD, STEEP, VERY_STEEP }

/**
 * Climb categories (Andrea, 13/11/2026). The usual score is length (m) × average grade (%),
 * which is 100 × the climb's elevation gain: so each category is an ascent threshold.
 * The Karoo's climbs carry no category, so we compute it.
 */
enum class ClimbCategory(val label: String, val minAscentM: Double, val example: String) {
    UNCATEGORIZED("Uncategorized", 0.0, "overpasses, short ramps"),
    CAT4("Cat 4", 80.0, "1 km at 8 %"),
    CAT3("Cat 3", 160.0, "2 km at 8 %"),
    CAT2("Cat 2", 320.0, "4 km at 8 %"),
    CAT1("Cat 1", 640.0, "8 km at 8 %"),
    HC("HC", 800.0, "10 km at 8 %");

    /** The plain-words line of the app: "Cat 4 — climbs from 80 m of ascent (e.g. 1 km at 8 %)". */
    val description: String get() =
        if (this == UNCATEGORIZED) "$label — under ${CAT4.minAscentM.toInt()} m ($example)"
        else "$label — climbs from ${minAscentM.toInt()} m of ascent (e.g. $example)"

    companion object {
        fun ofAscent(ascentM: Double): ClimbCategory =
            entries.lastOrNull { ascentM >= it.minAscentM } ?: UNCATEGORIZED

        /** From length and grade: score length × grade = 100 × ascent. */
        fun of(lengthM: Double, gradePct: Double): ClimbCategory = ofAscent(lengthM * gradePct / 100.0)
    }
}

/**
 * A climb as the Karoo lists it on the route: start along the route and length (m), average
 * grade (%) and, when the Karoo gives it, its total elevation gain (m).
 */
data class ClimbSpan(val startM: Double, val lengthM: Double, val gradePct: Double = 0.0, val totalElevationM: Double = 0.0) {
    val endM get() = startM + lengthM
    /** The Karoo's total elevation when it has one, else length × grade / 100. */
    val ascentM get() = if (totalElevationM > 0.0) totalElevationM else lengthM * gradePct / 100.0
    val category get() = ClimbCategory.ofAscent(ascentM)
}

/** The Karoo's instant grade next to the rider dot: "8 %", "-5 %" downhill, the profile's colour class. */
object InstantGrade {
    fun text(gradePct: Double): String {
        val r = Math.round(gradePct).toInt()
        return if (r < 0) "−${-r} %" else "$r %"
    }
    fun shade(gradePct: Double): Shade = Profile.shadeOf(gradePct)
}

/**
 * The single climb fields (and BigFields): distance to the start of the next climb not yet begun,
 * and distance, ascent and average grade from the rider to the top of the climb under the rider
 * (or, between climbs, of the next one). Null = no such climb ahead.
 */
data class ClimbFields(
    val toStartM: Double? = null,
    val toTopM: Double? = null,
    val upToTopM: Double? = null,
    val gradeToTopPct: Double? = null,
) {
    companion object {
        /** Only climbs of the [enabled] categories (the same as the auto zoom). Before the first position: from the route start. */
        fun of(profile: Profile?, climbs: List<ClimbSpan>, posM: Double?, enabled: Set<ClimbCategory>): ClimbFields {
            if (profile == null) return ClimbFields()
            val pos = posM ?: 0.0
            val ahead = climbs.filter { it.category in enabled && it.endM > pos }.sortedBy { it.startM }
            val next = ahead.firstOrNull { it.startM > pos }
            val top = ahead.firstOrNull() ?: return ClimbFields(toStartM = next?.let { it.startM - pos })
            val toTop = top.endM - pos
            val grade = if (pos < top.startM) {
                if (top.gradePct > 0) top.gradePct else top.ascentM / top.lengthM * 100.0
            } else (profile.elevationAt(top.endM) - profile.elevationAt(pos)) / toTop * 100.0
            return ClimbFields(next?.let { it.startM - pos }, toTop, profile.ascentBetween(pos, top.endM), grade)
        }
    }
}

/** The numbers of the CLIMB view: distance, ascent and average grade (of what is left, once on it). */
data class ClimbNumbers(val distanceM: Double, val ascentM: Double, val gradePct: Double, val onClimb: Boolean) {
    companion object {
        /**
         * Approaching (before the start): the whole climb as the Karoo lists it.
         * On the climb: from the rider to the top, on the route's own elevation.
         */
        fun of(profile: Profile, climb: ClimbSpan, posM: Double?): ClimbNumbers {
            if (posM == null || posM < climb.startM) {
                val grade = if (climb.gradePct > 0) climb.gradePct else climb.ascentM / climb.lengthM * 100.0
                return ClimbNumbers(climb.lengthM, climb.ascentM, grade, onClimb = false)
            }
            val left = (climb.endM - posM).coerceAtLeast(0.0)
            val up = profile.ascentBetween(posM, climb.endM)
            val grade = if (left > 0) (profile.elevationAt(climb.endM) - profile.elevationAt(posM)) / left * 100.0 else 0.0
            return ClimbNumbers(left, up, grade, onClimb = true)
        }
    }
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
    fun ascentFrom(fromM: Double): Double = ascentBetween(fromM, totalM)

    /** Ascent between two points of the route, metres, on the unsmoothed samples. */
    fun ascentBetween(fromM: Double, toM: Double): Double {
        if (toM <= fromM) return 0.0
        fun at(m: Double): Double {
            val x = (m / stepM).coerceIn(0.0, (raw.size - 1).toDouble())
            val i = x.toInt().coerceAtMost(raw.size - 2)
            return raw[i] + (raw[i + 1] - raw[i]) * (x - i)
        }
        val i0 = (fromM / stepM).toInt().coerceIn(0, raw.size - 1)
        val i1 = (toM / stepM).toInt().coerceIn(0, raw.size - 1)
        var prev = at(fromM)
        var up = 0.0
        for (i in i0 + 1..i1) { up += max(0.0, raw[i] - prev); prev = raw[i] }
        up += max(0.0, at(toM) - prev)
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
            out.add(Band(max(a, fromM), min(b, toM), shadeOf(g), g))
            a = b
        }
        return out
    }

    data class Band(val fromM: Double, val toM: Double, val shade: Shade, val gradePct: Double = 0.0)
}

/** WHOLE route, the climb fitted to the cell (CLIMB), or a window of [spanM] around the rider (AHEAD_*). */
enum class Zoom(val spanM: Double = 0.0, val label: String = "") {
    WHOLE, CLIMB, AHEAD_10(10_000.0, "NEXT 10 KM"), AHEAD_5(5_000.0, "NEXT 5 KM"), AHEAD_2(2_000.0, "NEXT 2 KM")
}

object ZoomLogic {
    /** A window view starts this share of its span behind the rider (1 km of the 10 km view). */
    const val BEHIND_SHARE = 0.1
    /** The tap order: whole route, the climb (when one is near), 10, 5, 2 km, whole route again. */
    private val CYCLE = listOf(Zoom.WHOLE, Zoom.CLIMB, Zoom.AHEAD_10, Zoom.AHEAD_5, Zoom.AHEAD_2)

    /**
     * The view after [current] on a tap. The climb only when one is near; a window only when the
     * route is longer than it (shorter, it would draw the same as the whole route).
     */
    fun next(current: Zoom, hasClimb: Boolean, totalM: Double): Zoom {
        val usable = CYCLE.filter { it == Zoom.WHOLE || (it == Zoom.CLIMB && hasClimb) || (it.spanM > 0 && totalM > it.spanM) }
        val i = usable.indexOf(current)
        return if (i < 0) Zoom.WHOLE else usable[(i + 1) % usable.size]
    }
    /** Auto-zoom: a climb that starts within this distance, or one we are on (default; the app offers 2 km, 1 km, 500 m). */
    const val CLIMB_NEAR_M = 1_000.0
    /** Margin on each side of a fitted climb: 3 % of its length, at least 50 m. */
    const val CLIMB_MARGIN = 0.03
    const val CLIMB_MARGIN_MIN_M = 50.0

    /** The climb near (within [nearM] of its start) or under the rider, among the [enabled] categories. */
    fun activeClimb(posM: Double?, climbs: List<ClimbSpan>, enabled: Set<ClimbCategory> = ClimbCategory.entries.toSet(),
                    nearM: Double = CLIMB_NEAR_M): ClimbSpan? {
        if (posM == null) return null
        return climbs.filter { it.category in enabled && posM >= it.startM - nearM && posM <= it.endM }
            .minByOrNull { it.startM }
    }

    /** Zoom on a climb near or under the rider whose category is in [enabled]. */
    fun auto(posM: Double?, climbs: List<ClimbSpan>, enabled: Set<ClimbCategory> = ClimbCategory.entries.toSet(),
             nearM: Double = CLIMB_NEAR_M): Zoom =
        if (activeClimb(posM, climbs, enabled, nearM) != null) Zoom.CLIMB else Zoom.WHOLE

    /**
     * Distance window [from, to] shown for [zoom]. CLIMB fits [climb] to the cell, from its start
     * (or from the rider, while still approaching it) to its end, with a small margin.
     * With [climbSpanM] > 0 (the auto zoom set to 10, 5 or 2 km) CLIMB shows that many km around the
     * rider, never past the top: a climb shorter than that is shown whole.
     * Before the first position a window view shows the start of the route.
     */
    fun window(zoom: Zoom, posM: Double?, totalM: Double, climb: ClimbSpan? = null, climbSpanM: Double = 0.0): Pair<Double, Double> {
        if (zoom == Zoom.CLIMB && climb != null) {
            val margin = max(CLIMB_MARGIN_MIN_M, climb.lengthM * CLIMB_MARGIN)
            val start = if (posM != null && posM < climb.startM) posM else climb.startM
            val cf = max(0.0, start - margin)
            val ct = min(totalM, climb.endM + margin)
            if (climbSpanM <= 0.0 || posM == null || ct - cf <= climbSpanM) return cf to ct
            val from = max(cf, posM - climbSpanM * BEHIND_SHARE)
            val to = min(ct, from + climbSpanM)
            return max(cf, to - climbSpanM) to to
        }
        val span = zoom.spanM
        if (span <= 0.0 || totalM <= span) return 0.0 to totalM
        val behind = span * BEHIND_SHARE
        var from = (posM ?: behind) - behind
        var to = from + span
        if (from < 0) { to -= from; from = 0.0 }
        if (to > totalM) { from -= to - totalM; to = totalM }
        return max(0.0, from) to to
    }
}
