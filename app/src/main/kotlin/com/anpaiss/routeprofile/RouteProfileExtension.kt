package com.anpaiss.routeprofile

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads the route the Karoo is navigating (its elevation polyline already carries the distance
 * along the route), where the rider is on it, and the climbs; keeps one [ProfileState] for the field.
 *
 * Position: routeDistance - DISTANCE_TO_DESTINATION when the Karoo publishes it (as boschRange),
 * otherwise the GPS fix projected onto the route line.
 */
class RouteProfileExtension : KarooExtension("routeprofile", BuildConfig.VERSION_NAME) {

    companion object {
        @Volatile var instance: RouteProfileExtension? = null
            private set

        /** BetterETA's "Time to Top" stream: seconds, and the metres to the top under [TOP_M_FIELD]. */
        const val BETTER_ETA_TOP = "TYPE_EXT::better-eta::eta-time-to-top"
        const val TOP_M_FIELD = "top_m"
    }

    private lateinit var karoo: KarooSystemService
    private val consumers = mutableListOf<String>()
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> get() = _state

    @Volatile private var routeKey = ""
    @Volatile private var routeLengthM: Double? = null
    @Volatile private var projection: Projection? = null
    @Volatile private var karooPosM: Double? = null
    @Volatile private var gpsPosM: Double? = null
    /** Set by a tap; forgotten when the automatic choice changes (a climb begins or ends). */
    @Volatile private var manual: Zoom? = null
    @Volatile private var lastAuto: Zoom = Zoom.WHOLE

    override val types by lazy { listOf(ProfileDataType(extension, state)) + ClimbFieldDataType.all(extension, state) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        karoo = KarooSystemService(applicationContext)
        karoo.connect { ok -> if (ok) subscribe() }
    }

    override fun onDestroy() {
        consumers.forEach { runCatching { karoo.removeConsumer(it) } }
        consumers.clear()
        karoo.disconnect()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Recording, paused or idle, for the climbs popup (it closes itself when recording resumes). */
    private val _rideState = MutableStateFlow<RideState>(RideState.Idle)
    val rideState: StateFlow<RideState> get() = _rideState

    private fun subscribe() {
        consumers += karoo.addConsumer(RideState.Params, { _ -> }, {}) { r: RideState ->
            _rideState.value = r
            _state.value = _state.value.copy(paused = r !is RideState.Recording)
        }
        consumers += karoo.addConsumer(OnNavigationState.Params, { _ -> }, {}) { e: OnNavigationState -> onNav(e.state) }
        consumers += karoo.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.DISTANCE_TO_DESTINATION), { _ -> }, {}
        ) { e: OnStreamState ->
            val v = (e.state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION)
            val len = routeLengthM
            karooPosM = if (v != null && len != null && v >= 0) (len - v).coerceIn(0.0, len) else null
            publish()
        }
        // BetterETA's time to the next top, when it is installed (its own model, never the Karoo's times)
        consumers += karoo.addConsumer(
            OnStreamState.StartStreaming(BETTER_ETA_TOP), { _ -> }, {}
        ) { e: OnStreamState ->
            val v = (e.state as? StreamState.Streaming)?.dataPoint?.values
            val sec = v?.get(DataType.Field.SINGLE)
            val m = v?.get(TOP_M_FIELD)
            _state.value = _state.value.copy(betterTop = if (sec != null && m != null && sec >= 0 && m >= 0) sec to m else null)
        }
        // the Karoo's instant grade, the value of its native Grade field (already smoothed by the Karoo)
        consumers += karoo.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.ELEVATION_GRADE), { _ -> }, {}
        ) { e: OnStreamState ->
            val g = (e.state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.ELEVATION_GRADE)
            _state.value = _state.value.copy(gradePct = g?.takeIf { it.isFinite() })
        }
        consumers += karoo.addConsumer(OnLocationChanged.Params, { _ -> }, {}) { e: OnLocationChanged ->
            val p = projection ?: return@addConsumer
            gpsPosM = p.along(e.lat, e.lng, hintM = gpsPosM ?: karooPosM)
            publish()
        }
    }

    private fun onNav(nav: OnNavigationState.NavigationState) {
        val (line, elev, climbs, reversed, length) = when (nav) {
            is OnNavigationState.NavigationState.NavigatingRoute ->
                RouteData(nav.routePolyline.orEmpty(), nav.routeElevationPolyline.orEmpty(), nav.climbs, nav.reversed, nav.routeDistance)
            is OnNavigationState.NavigationState.NavigatingToDestination ->
                RouteData(nav.polyline.orEmpty(), nav.elevationPolyline.orEmpty(), nav.climbs, false, null)
            else -> RouteData("", "", emptyList(), false, null)
        }
        val climbSpans = climbs.map { ClimbSpan(it.startDistance, it.length, it.grade, it.totalElevation) }
        if (line.isBlank() || elev.isBlank()) {
            if (routeKey.isNotEmpty() && nav !is OnNavigationState.NavigationState.NavigatingRoute &&
                nav !is OnNavigationState.NavigationState.NavigatingToDestination) clearRoute()
            return   // a transient empty polyline while navigating keeps the route we have
        }
        val key = "${line.hashCode()}/${elev.hashCode()}/$reversed"
        if (key == routeKey) {
            // the Karoo's climbs when it sends any; none (imported routes, always so far): ours stay
            if (climbSpans.isNotEmpty()) _state.value = _state.value.copy(climbs = climbSpans)
            return
        }
        routeKey = key
        var samples = Polyline.distanceElevation(elev)
        var pts = Polyline.latLng(line)
        if (reversed && samples.isNotEmpty()) {
            val total = samples.last().first
            samples = samples.reversed().map { (total - it.first) to it.second }
            pts = pts.reversed()
        }
        val profile = Profile.of(samples)
        routeLengthM = length ?: profile?.totalM
        projection = if (pts.size >= 2) Projection(pts, routeLengthM) else null
        karooPosM = null; gpsPosM = null; manual = null
        // The Karoo sends no climbs for imported routes: find them on the route's own elevation
        val routeClimbs = climbSpans.ifEmpty { profile?.let { ClimbDetector.find(it.ele, it.stepM) }.orEmpty() }
        _state.value = ProfileState(profile = profile, climbs = routeClimbs, paused = _state.value.paused, gradePct = _state.value.gradePct)
        publish()
    }

    private data class RouteData(
        val line: String, val elev: String,
        val climbs: List<OnNavigationState.NavigationState.Climb>,
        val reversed: Boolean, val length: Double?,
    )

    private fun clearRoute() {
        routeKey = ""; routeLengthM = null; projection = null
        karooPosM = null; gpsPosM = null; manual = null
        _state.value = ProfileState(paused = _state.value.paused, gradePct = _state.value.gradePct)
    }

    private fun publish() {
        val s = _state.value
        val pos = karooPosM ?: gpsPosM
        // the climb near or under the rider, among the categories switched on in the app
        val climb = ZoomLogic.activeClimb(pos, s.climbs, Prefs.zoomCategories(this), Prefs.autoNearM(this))
        val auto = if (Prefs.autoZoom(this) && climb != null) Zoom.CLIMB else Zoom.WHOLE
        if (auto != lastAuto) { manual = null; lastAuto = auto }
        // a manual CLIMB with no climb near any more goes back to the whole route
        val m = manual?.let { if (it == Zoom.CLIMB && climb == null) Zoom.WHOLE else it }
        // the 10/5/2 km choice applies to the automatic climb view; a tapped CLIMB shows the whole climb
        _state.value = s.copy(posM = pos, zoom = m ?: auto, climb = climb, climbSpanM = if (m == null) Prefs.autoSpanM(this) else 0.0,
            smooth = Prefs.smooth(this), graphOnly = Prefs.graphOnly(this),
            fields = ClimbFields.of(s.profile, s.climbs, pos, Prefs.zoomCategories(this)))
    }

    private fun total(s: ProfileState) = s.profile?.totalM ?: 0.0

    /** Whole route -> the climb (when one is near) -> 10 -> 5 -> 2 km -> whole route, skipping views longer than the route. */
    fun onTap() {
        val s = _state.value
        manual = ZoomLogic.next(s.zoom, s.climb != null, total(s))
        _state.value = s.copy(zoom = manual!!, climbSpanM = 0.0)
    }

    /** The app page changed the auto-zoom switch. */
    fun settingsChanged() { manual = null; publish() }
}
