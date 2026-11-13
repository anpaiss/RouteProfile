package com.anpaiss.routeprofile

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
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

    override val types by lazy { listOf(ProfileDataType(extension, state)) }

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

    private fun subscribe() {
        consumers += karoo.addConsumer(OnNavigationState.Params, { _ -> }, {}) { e: OnNavigationState -> onNav(e.state) }
        consumers += karoo.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.DISTANCE_TO_DESTINATION), { _ -> }, {}
        ) { e: OnStreamState ->
            val v = (e.state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION)
            val len = routeLengthM
            karooPosM = if (v != null && len != null && v >= 0) (len - v).coerceIn(0.0, len) else null
            publish()
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
        val climbSpans = climbs.map { ClimbSpan(it.startDistance, it.length, it.grade) }
        if (line.isBlank() || elev.isBlank()) {
            if (routeKey.isNotEmpty() && nav !is OnNavigationState.NavigationState.NavigatingRoute &&
                nav !is OnNavigationState.NavigationState.NavigatingToDestination) clearRoute()
            return   // a transient empty polyline while navigating keeps the route we have
        }
        val key = "${line.hashCode()}/${elev.hashCode()}/$reversed"
        if (key == routeKey) {
            _state.value = _state.value.copy(climbs = climbSpans)
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
        _state.value = ProfileState(profile = profile, climbs = climbSpans)
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
        _state.value = ProfileState()
    }

    private fun publish() {
        val s = _state.value
        val pos = karooPosM ?: gpsPosM
        // the climb near or under the rider, among the categories switched on in the app
        val climb = ZoomLogic.activeClimb(pos, s.climbs, Prefs.zoomCategories(this))
        val auto = if (Prefs.autoZoom(this) && climb != null) Zoom.CLIMB else Zoom.WHOLE
        if (auto != lastAuto) { manual = null; lastAuto = auto }
        // a manual CLIMB with no climb near any more becomes the 10 km view
        val m = manual?.let { if (it == Zoom.CLIMB && climb == null) Zoom.AHEAD else it }
        _state.value = s.copy(posM = pos, zoom = m ?: auto, climb = climb)
    }

    /** Whole route <-> the climb when one is near or under the rider, otherwise <-> the next 10 km. */
    fun onTap() {
        val s = _state.value
        manual = if (s.zoom != Zoom.WHOLE) Zoom.WHOLE else if (s.climb != null) Zoom.CLIMB else Zoom.AHEAD
        _state.value = s.copy(zoom = manual!!)
    }

    /** The app page changed the auto-zoom switch. */
    fun settingsChanged() { manual = null; publish() }
}
