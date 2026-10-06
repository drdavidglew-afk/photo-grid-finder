package com.photogridfinder.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** WGS84 latitude/longitude to Ordnance Survey National Grid (OSGB36). */
object OsGrid {

    data class Position(val easting: Double, val northing: Double)

    fun toEastingNorthing(lat: Double, lon: Double): Position {
        val rad = Math.PI / 180.0

        // WGS84 lat/long -> cartesian
        var a = 6378137.0
        var b = 6356752.3142
        var e2 = 1 - (b * b) / (a * a)
        var phi = lat * rad
        var lam = lon * rad
        var nu = a / sqrt(1 - e2 * sin(phi).pow(2))
        val x = nu * cos(phi) * cos(lam)
        val y = nu * cos(phi) * sin(lam)
        val z = (1 - e2) * nu * sin(phi)

        // Helmert transform WGS84 -> OSGB36
        val tx = -446.448; val ty = 125.157; val tz = -542.060
        val s = 20.4894e-6
        val rx = (-0.1502 / 3600) * rad
        val ry = (-0.2470 / 3600) * rad
        val rz = (-0.8421 / 3600) * rad
        val x2 = tx + (1 + s) * x - rz * y + ry * z
        val y2 = ty + rz * x + (1 + s) * y - rx * z
        val z2 = tz - ry * x + rx * y + (1 + s) * z

        // Cartesian -> Airy 1830 lat/long
        a = 6377563.396
        b = 6356256.909
        e2 = 1 - (b * b) / (a * a)
        val p = sqrt(x2 * x2 + y2 * y2)
        phi = atan2(z2, p * (1 - e2))
        repeat(10) {
            nu = a / sqrt(1 - e2 * sin(phi).pow(2))
            phi = atan2(z2 + e2 * nu * sin(phi), p)
        }
        lam = atan2(y2, x2)

        // Transverse Mercator projection
        val f0 = 0.9996012717
        val phi0 = 49 * rad
        val lam0 = -2 * rad
        val n0 = -100000.0
        val e0 = 400000.0
        val n = (a - b) / (a + b)
        val sinP = sin(phi)
        val cosP = cos(phi)
        val tanP = tan(phi)
        nu = a * f0 / sqrt(1 - e2 * sinP * sinP)
        val rho = a * f0 * (1 - e2) / (1 - e2 * sinP * sinP).pow(1.5)
        val eta2 = nu / rho - 1
        val dp = phi - phi0
        val sp = phi + phi0
        val m = b * f0 * (
            (1 + n + 1.25 * n * n + 1.25 * n.pow(3)) * dp
                - (3 * n + 3 * n * n + 2.625 * n.pow(3)) * sin(dp) * cos(sp)
                + (1.875 * n * n + 1.875 * n.pow(3)) * sin(2 * dp) * cos(2 * sp)
                - (35.0 / 24.0) * n.pow(3) * sin(3 * dp) * cos(3 * sp)
            )
        val i = m + n0
        val ii = nu / 2 * sinP * cosP
        val iii = nu / 24 * sinP * cosP.pow(3) * (5 - tanP.pow(2) + 9 * eta2)
        val iiiA = nu / 720 * sinP * cosP.pow(5) * (61 - 58 * tanP.pow(2) + tanP.pow(4))
        val iv = nu * cosP
        val v = nu / 6 * cosP.pow(3) * (nu / rho - tanP.pow(2))
        val vi = nu / 120 * cosP.pow(5) *
            (5 - 18 * tanP.pow(2) + tanP.pow(4) + 14 * eta2 - 58 * tanP.pow(2) * eta2)
        val dl = lam - lam0
        val north = i + ii * dl.pow(2) + iii * dl.pow(4) + iiiA * dl.pow(6)
        val east = e0 + iv * dl + v * dl.pow(3) + vi * dl.pow(5)
        return Position(east, north)
    }

    /** 10-figure grid reference such as "SH 60985 54377", or null outside the GB grid. */
    fun gridRef10(e: Double, n: Double): String? {
        if (e < 0 || e >= 700000 || n < 0 || n >= 1300000) return null
        val e100k = floor(e / 100000).toInt()
        val n100k = floor(n / 100000).toInt()
        var l1 = (19 - n100k) - (19 - n100k) % 5 + (e100k + 10) / 5
        var l2 = ((19 - n100k) * 5) % 25 + e100k % 5
        if (l1 > 7) l1++
        if (l2 > 7) l2++
        val letters = "${Char(65 + l1)}${Char(65 + l2)}"
        val ee = floor(e % 100000).toInt().toString().padStart(5, '0')
        val nn = floor(n % 100000).toInt().toString().padStart(5, '0')
        return "$letters $ee $nn"
    }
}
