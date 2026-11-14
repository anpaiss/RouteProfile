package com.anpaiss.routeprofile

import org.junit.Assert.assertEquals
import org.junit.Test

/** The detector on two real routes (lagorai.bike GPX, smoothed every 50 m), same results as the Python replay of 14/11/2026. */
class ClimbDetectorTest {

    private fun ele(name: String): DoubleArray =
        javaClass.classLoader!!.getResourceAsStream("$name.ele")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { it.toDouble() }.toDoubleArray()

    private fun check(name: String, expected: List<Triple<Double, Double, Double>>) {
        val found = ClimbDetector.find(ele(name), 50.0)
        assertEquals(expected.size, found.size)
        for ((e, f) in expected.zip(found)) {
            assertEquals(e.first, f.startM, 1.0)
            assertEquals(e.second, f.lengthM, 1.0)
            assertEquals(e.third, f.totalElevationM, 1.5)
        }
    }

    @Test fun `Menador, the climb from km 14 and two smaller ones up to the Vezzena`() =
        check("menador-drago-vaia-passo-vezzena", listOf(
            Triple(13_650.0, 7_650.0, 725.6), Triple(24_250.0, 1_400.0, 96.6), Triple(30_750.0, 3_750.0, 179.2)))

    @Test fun `Manghen in three pieces split by its false flats`() =
        check("passo-manghen-telve", listOf(
            Triple(0.0, 1_100.0, 96.8), Triple(3_550.0, 4_250.0, 372.0), Triple(7_900.0, 9_550.0, 747.8)))

    @Test fun `flat route, no climb`() = assertEquals(0, ClimbDetector.find(DoubleArray(200) { 500.0 }, 50.0).size)
}
