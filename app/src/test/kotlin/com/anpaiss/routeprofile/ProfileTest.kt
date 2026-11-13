package com.anpaiss.routeprofile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {

    @Test fun `polyline round trip, lat lon and distance elevation`() {
        val ll = listOf(46.0151 to 11.5926, 46.0133 to 11.5882, 45.9796 to 11.5948)
        val back = Polyline.latLng(Polyline.encode(ll, 1e5))
        assertEquals(3, back.size)
        ll.zip(back).forEach { (a, b) -> assertEquals(a.first, b.first, 1e-5); assertEquals(a.second, b.second, 1e-5) }
        val de = listOf(0.0 to 319.0, 2400.0 to 530.5, 14800.0 to 1383.2)
        val back2 = Polyline.distanceElevation(Polyline.encode(de, 10.0))
        de.zip(back2).forEach { (a, b) -> assertEquals(a.first, b.first, 0.1); assertEquals(a.second, b.second, 0.1) }
    }

    @Test fun `known Google polyline decodes`() {
        // The example of the Google documentation
        val p = Polyline.latLng("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, p.size)
        assertEquals(38.5, p[0].first, 1e-5); assertEquals(-120.2, p[0].second, 1e-5)
        assertEquals(43.252, p[2].first, 1e-5); assertEquals(-126.453, p[2].second, 1e-5)
    }

    @Test fun `blank or broken polyline does not crash`() {
        assertTrue(Polyline.latLng("").isEmpty())
        Polyline.latLng("_p~iF~ps|U_ulL")   // truncated
        assertNull(Profile.of(emptyList()))
        assertNull(Profile.of(listOf(0.0 to 100.0)))
    }

    @Test fun `gradient classes`() {
        assertEquals(Shade.DESCENT, Profile.shadeOf(-5.0))
        assertEquals(Shade.EASY, Profile.shadeOf(-1.0))
        assertEquals(Shade.EASY, Profile.shadeOf(2.9))
        assertEquals(Shade.MODERATE, Profile.shadeOf(3.0))
        assertEquals(Shade.HARD, Profile.shadeOf(7.0))
        assertEquals(Shade.STEEP, Profile.shadeOf(10.0))
        assertEquals(Shade.VERY_STEEP, Profile.shadeOf(15.0))
    }

    @Test fun `bands follow the slope`() {
        // 2 km at 8 %, then 2 km flat
        val p = Profile.of(listOf(0.0 to 100.0, 2000.0 to 260.0, 4000.0 to 260.0))!!
        val b = p.bands(0.0, 4000.0)
        assertEquals(40, b.size)
        assertEquals(Shade.HARD, b[5].shade)
        assertEquals(Shade.EASY, b[35].shade)
        // longer bands for a long window
        val wide = p.bands(0.0, 4000.0, 500.0)
        assertEquals(8, wide.size)
        assertEquals(Shade.HARD, wide[1].shade)
    }

    @Test fun `remaining ascent counts only what is ahead`() {
        // up 300 m, down 200 m, up 100 m
        val p = Profile.of(listOf(0.0 to 0.0, 3000.0 to 300.0, 5000.0 to 100.0, 6000.0 to 200.0))!!
        assertEquals(400.0, p.ascentFrom(0.0), 30.0)   // the 250 m smoothing rounds each summit off by ~10 m
        assertEquals(100.0, p.ascentFrom(5000.0), 30.0)
        assertEquals(0.0, p.ascentFrom(6000.0), 1.0)
    }

    @Test fun `auto zoom near and on a climb, whole route otherwise`() {
        val climbs = listOf(ClimbSpan(5000.0, 3000.0))
        assertEquals(Zoom.WHOLE, ZoomLogic.auto(1000.0, climbs))
        assertEquals(Zoom.AHEAD, ZoomLogic.auto(3500.0, climbs))
        assertEquals(Zoom.AHEAD, ZoomLogic.auto(7000.0, climbs))
        assertEquals(Zoom.WHOLE, ZoomLogic.auto(8500.0, climbs))
        assertEquals(Zoom.WHOLE, ZoomLogic.auto(null, climbs))
    }

    @Test fun `climb categories from length times grade`() {
        assertEquals(ClimbCategory.UNCATEGORIZED, ClimbCategory.of(300.0, 6.0))      // 1,800: an overpass
        assertEquals(ClimbCategory.UNCATEGORIZED, ClimbCategory.of(1_000.0, 7.99))
        assertEquals(ClimbCategory.CAT4, ClimbCategory.of(1_000.0, 8.0))             // 8,000
        assertEquals(ClimbCategory.CAT3, ClimbCategory.of(2_000.0, 8.0))             // 16,000
        assertEquals(ClimbCategory.CAT2, ClimbCategory.of(5_000.0, 7.0))             // 35,000
        assertEquals(ClimbCategory.CAT1, ClimbCategory.of(8_000.0, 8.0))             // 64,000
        assertEquals(ClimbCategory.HC, ClimbCategory.of(12_000.0, 8.0))              // 96,000
        assertEquals(ClimbCategory.UNCATEGORIZED, ClimbCategory.of(5_000.0, -3.0))   // negative: never a climb
    }

    @Test fun `auto zoom only for enabled categories`() {
        val overpass = ClimbSpan(5_000.0, 300.0, 6.0)
        val cat2 = ClimbSpan(20_000.0, 5_000.0, 7.0)
        val defaults = ClimbCategory.entries.toSet() - ClimbCategory.UNCATEGORIZED
        assertEquals(Zoom.WHOLE, ZoomLogic.auto(4_500.0, listOf(overpass, cat2), defaults))
        assertEquals(Zoom.AHEAD, ZoomLogic.auto(4_500.0, listOf(overpass, cat2), ClimbCategory.entries.toSet()))
        assertEquals(Zoom.AHEAD, ZoomLogic.auto(19_000.0, listOf(overpass, cat2), defaults))
        assertEquals(Zoom.WHOLE, ZoomLogic.auto(19_000.0, listOf(overpass, cat2), defaults - ClimbCategory.CAT2))
    }

    @Test fun `zoom window stays inside the route`() {
        assertEquals(0.0 to 40_000.0, ZoomLogic.window(Zoom.WHOLE, 18_000.0, 40_000.0))
        assertEquals(17_000.0 to 27_000.0, ZoomLogic.window(Zoom.AHEAD, 18_000.0, 40_000.0))
        assertEquals(0.0 to 10_000.0, ZoomLogic.window(Zoom.AHEAD, 300.0, 40_000.0))
        assertEquals(30_000.0 to 40_000.0, ZoomLogic.window(Zoom.AHEAD, 39_500.0, 40_000.0))
        assertEquals(0.0 to 8_000.0, ZoomLogic.window(Zoom.AHEAD, 4_000.0, 8_000.0))
        assertEquals(0.0 to 10_000.0, ZoomLogic.window(Zoom.AHEAD, null, 40_000.0))   // no position yet
    }

    @Test fun `projection finds the distance along the route`() {
        // straight line north, ~1.11 km per 0.01 deg
        val pts = (0..10).map { 46.0 + it * 0.001 to 11.5 }
        val pr = Projection(pts)
        val a = pr.along(46.005, 11.5001)
        assertNotNull(a)
        assertEquals(556.6, a!!, 5.0)
        assertNull(pr.along(46.005, 11.51))   // ~770 m off the route
    }

    @Test fun `projection on a loop prefers the stretch near the last position`() {
        // out and back on the same line: without a hint the first pass wins
        val out = (0..10).map { 46.0 + it * 0.001 to 11.5 }
        val pts = out + out.reversed().drop(1)
        val pr = Projection(pts)
        val first = pr.along(46.002, 11.5)!!
        val second = pr.along(46.002, 11.5, hintM = 1800.0)!!
        assertTrue(first < 300)
        assertTrue(second > 1800)
    }

    @Test fun `sample state for the editor is complete`() {
        val s = ProfileState.sample()
        assertNotNull(s.profile)
        assertTrue(s.remainingM!! > 10_000)
        assertTrue(s.remainingAscentM!! > 50)
    }
}
