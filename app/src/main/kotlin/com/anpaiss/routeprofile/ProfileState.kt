package com.anpaiss.routeprofile

/** Everything the field draws. [profile] null = no route. */
data class ProfileState(
    val profile: Profile? = null,
    val posM: Double? = null,
    val climbs: List<ClimbSpan> = emptyList(),
    val zoom: Zoom = Zoom.WHOLE,
    /** The climb the CLIMB view fits, when one is near or under the rider. */
    val climb: ClimbSpan? = null,
    /** Auto zoom on a climb: 0 = the whole climb, else the km shown (10, 5 or 2 km, at most to the top). */
    val climbSpanM: Double = 0.0,
    /** Colours: false = one colour per gradient class (bands), true = a continuous blend along the grade. */
    val smooth: Boolean = false,
    /** Only the graph and the instant grade on the dot: no top line, the profile takes the whole cell. */
    val graphOnly: Boolean = false,
    /** The single climb fields: to the next climb, to the top, ascent and grade to the top. */
    val fields: ClimbFields = ClimbFields(),
    /**
     * BetterETA's time to the next top (seconds) and the metres to that top, when BetterETA is installed;
     * null without it. Never the Karoo's own times: always wrong (Andrea, 14/11/2026).
     */
    val betterTop: Pair<Double, Double>? = null,
    /** Not recording (paused, or before the start): a tap opens the climbs popup instead of switching the zoom. */
    val paused: Boolean = false,
    /** The Karoo's own instant grade (%), as its Grade field shows it; null = no value. */
    val gradePct: Double? = null,
) {
    /** "0:24" for the CLIMB line when BetterETA's top is this climb's top (within [TOP_MATCH_M]); else null. */
    fun climbTimeText(): String? {
        val (sec, topM) = betterTop ?: return null
        val c = climb ?: return null
        if (sec < 0 || zoom != Zoom.CLIMB) return null
        val ownTopM = c.endM - (posM ?: 0.0)
        if (topM >= 0 && kotlin.math.abs(ownTopM - topM) > TOP_MATCH_M) return null
        val min = Math.round(sec / 60.0)
        return "%d:%02d".format(min / 60, min % 60)
    }

    val remainingM: Double? get() = profile?.let { p -> posM?.let { (p.totalM - it).coerceAtLeast(0.0) } ?: p.totalM }
    val remainingAscentM: Double? get() = profile?.ascentFrom(posM ?: 0.0)

    companion object {
        /** BetterETA and RouteProfile find the same climbs; a top this close is the same top. */
        const val TOP_MATCH_M = 300.0

        /**
         * Sample for the profile editor and the FIELD PREVIEW: a 40 km loop like the
         * Barricata (long climb, short climb on the top, long descent, flat back), rider at km 14.5.
         */
        fun sample(zoom: Zoom = Zoom.WHOLE, atM: Double? = null): ProfileState {
            // CLIMB: the rider 1.2 km before the second climb (km 16, 2.5 km), so the fitted view shows the approach too
            val pos = atM ?: if (zoom == Zoom.CLIMB) 14_800.0 else 14_500.0
            val pts = ArrayList<Pair<Double, Double>>()
            var e = 320.0
            var d = 0.0
            fun go(lenM: Double, gradePct: Double, wobble: Double = 0.0) {
                val steps = (lenM / 100).toInt()
                repeat(steps) { i ->
                    d += 100.0
                    e += gradePct + wobble * kotlin.math.sin(i / 3.0)
                    pts.add(d to e)
                }
            }
            pts.add(0.0 to e)
            go(3_000.0, 4.0); go(4_000.0, 9.0, 2.0); go(3_000.0, 7.0, 3.0); go(2_000.0, 3.0)
            go(4_000.0, 0.0, 2.0); go(1_500.0, 6.0, 2.0); go(1_000.0, 10.0); go(500.0, -3.0)
            go(10_000.0, -10.0, 2.0); go(2_000.0, -3.0); go(7_000.0, 0.3, 0.5)
            // a long first climb (Cat 1), a short ramp on the plateau (uncategorized), the summit climb (Cat 3)
            val climbs = listOf(ClimbSpan(0.0, 12_000.0, 6.5), ClimbSpan(13_200.0, 400.0, 5.0), ClimbSpan(16_000.0, 2_500.0, 7.0))
            return ProfileState(
                profile = Profile.of(pts),
                posM = pos,
                climbs = climbs,
                zoom = zoom,
                climb = if (zoom == Zoom.CLIMB) climbs[2] else null,
                gradePct = 6.0,
            )
        }
    }
}
