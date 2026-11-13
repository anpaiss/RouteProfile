package com.anpaiss.routeprofile

/** Everything the field draws. [profile] null = no route. */
data class ProfileState(
    val profile: Profile? = null,
    val posM: Double? = null,
    val climbs: List<ClimbSpan> = emptyList(),
    val zoom: Zoom = Zoom.WHOLE,
    /** The climb the CLIMB view fits, when one is near or under the rider. */
    val climb: ClimbSpan? = null,
    /** Ride paused: a tap opens the climbs popup instead of switching the zoom. */
    val paused: Boolean = false,
    /** The Karoo's own instant grade (%), as its Grade field shows it; null = no value. */
    val gradePct: Double? = null,
) {
    val remainingM: Double? get() = profile?.let { p -> posM?.let { (p.totalM - it).coerceAtLeast(0.0) } ?: p.totalM }
    val remainingAscentM: Double? get() = profile?.ascentFrom(posM ?: 0.0)

    companion object {
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
