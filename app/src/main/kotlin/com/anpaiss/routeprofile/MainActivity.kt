package com.anpaiss.routeprofile

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * The app page: what the field does, the auto-zoom switch and the FIELD PREVIEW drawn by the
 * real renderer at the Karoo 3 cell sizes (full-width top row of the map page, a taller
 * cell, and a half cell).
 */
class MainActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFF1A1A1A.toInt()); if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }
        col.addView(text("RouteProfile", 26f, bold = true))
        col.addView(text("The whole route's elevation, coloured by gradient, with where you are and the distance and climb still to go. " +
            "Put \"Route profile\" in the top row of the map page, full width (add it as the third field, or alone): at the bottom it would cover your position arrow. " +
            "Tap the field: whole route, the climb (when one is near), next 10, 5, 2 km, whole route again; before the start or with the ride paused, the list of the climbs.", 15f))

        val sw = Switch(this).apply {
            text = "Auto zoom on climbs"; textSize = 17f
            isChecked = Prefs.autoZoom(this@MainActivity)
            setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, on ->
                Prefs.setAutoZoom(this@MainActivity, on)
                RouteProfileExtension.instance?.settingsChanged()
            }
        }
        col.addView(sw)
        col.addView(text("On: when a climb is near, and while you are on it, the field zooms on that climb, then shows the whole route again. " +
            "A tap always switches; the automatic choice takes over again at the next climb.", 13f))

        col.addView(text("Zoom in before the climb", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        val nears = listOf(2_000.0 to "2 km", 1_000.0 to "1 km", 500.0 to "500 m")
        val nearGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        nears.forEachIndexed { i, (m, label) ->
            nearGroup.addView(RadioButton(this).apply {
                id = 200 + i; text = label; textSize = 15f
                isChecked = Prefs.autoNearM(this@MainActivity) == m
                isEnabled = sw.isChecked
            })
        }
        nearGroup.setOnCheckedChangeListener { _, id ->
            Prefs.setAutoNearM(this@MainActivity, nears[id - 200].first)
            RouteProfileExtension.instance?.settingsChanged()
        }
        col.addView(nearGroup)
        col.addView(text("On a route that goes up and down, 500 m keeps the field from staying zoomed between one climb and the next.", 13f))

        col.addView(text("On a climb, show", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        val spans = listOf(0.0 to "Whole climb", 10_000.0 to "10 km", 5_000.0 to "5 km", 2_000.0 to "2 km")
        val spanGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        spans.forEachIndexed { i, (m, label) ->
            spanGroup.addView(RadioButton(this).apply {
                id = 100 + i; text = label; textSize = 15f
                isChecked = Prefs.autoSpanM(this@MainActivity) == m
                isEnabled = sw.isChecked
            })
        }
        spanGroup.setOnCheckedChangeListener { _, id ->
            Prefs.setAutoSpanM(this@MainActivity, spans[id - 100].first)
            RouteProfileExtension.instance?.settingsChanged()
        }
        col.addView(spanGroup)
        col.addView(text("10, 5 or 2 km: on a long climb (a 30 km pass) that much of the road ahead, never past the top. A climb shorter than that is shown whole.", 13f))

        col.addView(text("Zoom on these climbs", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        col.addView(text("A climb's category goes by how much it climbs.", 13f))
        val catSwitches = ClimbCategory.entries.map { cat ->
            Switch(this).apply {
                text = cat.description; textSize = 16f
                isChecked = Prefs.zoomOn(this@MainActivity, cat)
                isEnabled = sw.isChecked
                setPadding(dp(12), dp(6), 0, dp(6))
                setOnCheckedChangeListener { _, on ->
                    Prefs.setZoomOn(this@MainActivity, cat, on)
                    RouteProfileExtension.instance?.settingsChanged()
                }
            }.also { col.addView(it) }
        }
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoZoom(this@MainActivity, on)
            catSwitches.forEach { it.isEnabled = on }
            for (i in 0 until spanGroup.childCount) spanGroup.getChildAt(i).isEnabled = on
            for (i in 0 until nearGroup.childCount) nearGroup.getChildAt(i).isEnabled = on
            RouteProfileExtension.instance?.settingsChanged()
        }

        col.addView(text("Look", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        col.addView(Switch(this).apply {
            text = "Smooth colours"; textSize = 16f
            isChecked = Prefs.smooth(this@MainActivity)
            setPadding(dp(12), dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, on -> Prefs.setSmooth(this@MainActivity, on); RouteProfileExtension.instance?.settingsChanged() }
        })
        col.addView(text("Off: one colour per gradient class, in bands. On: the colour blends along the grade (orange turning red as it steepens).", 13f))
        col.addView(Switch(this).apply {
            text = "Graph only"; textSize = 16f
            isChecked = Prefs.graphOnly(this@MainActivity)
            setPadding(dp(12), dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, on -> Prefs.setGraphOnly(this@MainActivity, on); RouteProfileExtension.instance?.settingsChanged() }
        })
        col.addView(text("No top line (view, km and climb to go): the graph takes the whole field, the instant grade stays on the dot.", 13f))

        col.addView(text("Climbs popup", 15f, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        col.addView(text("Before the start or with the ride paused, a tap on the field lists the route's climbs, coloured by category, the next one highlighted. " +
            "It closes with CLOSE or when the ride records again.", 13f))
        col.addView(android.widget.Button(this).apply {
            text = "TEST POPUP: CLIMBS"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, ClimbsActivity::class.java).putExtra(ClimbsActivity.EXTRA_SAMPLE, true))
            }
        })

        col.addView(text("FIELD PREVIEW", 17f, bold = true).apply { setPadding(0, dp(18), 0, dp(4)) })
        col.addView(text("Sample route, rider at km 14.5. Sizes of the Karoo 3 cells.", 13f))
        fun preview(label: String, s: ProfileState, w: Int, h: Int) {
            col.addView(text(label, 13f))
            // the real cell size, scaled to the column (the Karoo screen is 480 px, the column a bit less)
            val colW = resources.displayMetrics.widthPixels - dp(32)
            val shownW = if (w >= 400) colW else w * colW / 478
            col.addView(ImageView(this).apply {
                setImageBitmap(ProfileRenderer.render(s, w, h, d))
                scaleType = ImageView.ScaleType.FIT_XY
            }, LinearLayout.LayoutParams(shownW, shownW * h / w).apply { bottomMargin = dp(8) })
        }
        preview("Map page, top row (478×126): whole route", ProfileState.sample(Zoom.WHOLE), 478, 126)
        preview("Auto zoom, approaching a climb: the whole climb fitted to the cell", ProfileState.sample(Zoom.CLIMB), 478, 126)
        preview("On the climb: what is left to the top", ProfileState.sample(Zoom.CLIMB, atM = 17_000.0), 478, 126)
        preview("On the long climb, auto zoom set to 2 km", ProfileState.sample(Zoom.CLIMB, atM = 5_000.0).let { it.copy(climb = it.climbs[0], climbSpanM = 2_000.0) }, 478, 126)
        preview("A tap with no climb near: next 10 km", ProfileState.sample(Zoom.AHEAD_10), 478, 126)
        preview("Smooth colours", ProfileState.sample(Zoom.WHOLE).copy(smooth = true), 478, 126)
        preview("Graph only", ProfileState.sample(Zoom.WHOLE).copy(graphOnly = true), 478, 126)
        preview("Graph only, smooth colours", ProfileState.sample(Zoom.WHOLE).copy(graphOnly = true, smooth = true), 478, 126)
        preview("Downhill: the instant grade in blue, with its sign", ProfileState.sample(Zoom.WHOLE, atM = 26_000.0).copy(gradePct = -8.0), 478, 126)
        preview("Taller cell (478×212)", ProfileState.sample(Zoom.WHOLE), 478, 212)
        preview("Half cell (238×126)", ProfileState.sample(Zoom.WHOLE), 238, 126)
        preview("Half page (478×410), on a climb: the grade again, big, where the profile leaves room", ProfileState.sample(Zoom.CLIMB, atM = 17_000.0).copy(gradePct = 11.0), 478, 410)
        preview("Half page, downhill: the big grade on the right", ProfileState.sample(Zoom.WHOLE, atM = 26_000.0).copy(gradePct = -8.0), 478, 410)
        preview("No route", ProfileState(), 478, 126)

        setContentView(ScrollView(this).apply { addView(col, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)); setBackgroundColor(0xFFF4F2EE.toInt()) })
    }
}
