package com.kim.austopo.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.kim.austopo.CoordinateConverter
import com.kim.austopo.MapCamera
import com.kim.austopo.geo.Utm
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a 1 km MGA grid overlay on top of the map.
 *
 * Zone-explicit by design: Australia straddles MGA zones 49–56, and in
 * particular Vic straddles zones 54 and 55 (boundary at 144°E). We never
 * infer a single zone from the camera centre because a viewport spanning
 * 144°E would then render the wrong grid on one side. Instead we render
 * each supported zone separately, clipped to its own 6° longitude band.
 *
 * Cached: the full screen-space Path is rebuilt only when the camera moves
 * by more than REBUILD_FRACTION of the viewport extent, or when zoom
 * changes by more than REBUILD_ZOOM_FRACTION. Between rebuilds a frame
 * only pays for the drawPath + label draws.
 */
class GridRenderer(
    /** Zones to render. Default: the pair that covers Vic. */
    private val zones: List<Int> = Utm.VIC_ZONES
) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 120, 180)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val majorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 100, 160)
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 70, 130)
        textSize = 22f
        setShadowLayer(2f, 0f, 0f, Color.WHITE)
    }

    // Cache state
    private var cachedMinorPath: Path? = null
    private var cachedMajorPath: Path? = null
    private var cachedLabels: List<Label> = emptyList()
    private var cachedCenterX = 0.0
    private var cachedCenterY = 0.0
    private var cachedZoom = 0.0f
    private var cachedW = 0
    private var cachedH = 0

    fun draw(canvas: Canvas, camera: MapCamera) {
        if (camera.viewWidth == 0 || camera.viewHeight == 0) return
        if (needsRebuild(camera)) rebuild(camera)
        cachedMinorPath?.let { canvas.drawPath(it, linePaint) }
        cachedMajorPath?.let { canvas.drawPath(it, majorLinePaint) }
        for (lbl in cachedLabels) {
            canvas.drawText(lbl.text, lbl.x, lbl.y, labelPaint)
        }
    }

    private fun needsRebuild(camera: MapCamera): Boolean {
        if (cachedMinorPath == null) return true
        if (camera.viewWidth != cachedW || camera.viewHeight != cachedH) return true
        val dx = abs(camera.centerX - cachedCenterX) * camera.zoom
        val dy = abs(camera.centerY - cachedCenterY) * camera.zoom
        val thresholdPx = min(camera.viewWidth, camera.viewHeight) * REBUILD_FRACTION
        if (dx > thresholdPx || dy > thresholdPx) return true
        val zRatio = camera.zoom / cachedZoom
        if (zRatio > 1f + REBUILD_ZOOM_FRACTION || zRatio < 1f - REBUILD_ZOOM_FRACTION) return true
        return false
    }

    private fun rebuild(camera: MapCamera) {
        // Viewport bbox in Web Mercator.
        val halfW = camera.halfViewW()
        val halfH = camera.halfViewH()
        val minMx = camera.centerX - halfW
        val maxMx = camera.centerX + halfW
        val minMy = camera.centerY - halfH
        val maxMy = camera.centerY + halfH

        // Approximate visible longitude range from the x extent.
        val (_, wLon) = CoordinateConverter.webMercatorToWgs84(minMx, camera.centerY)
        val (_, eLon) = CoordinateConverter.webMercatorToWgs84(maxMx, camera.centerY)

        // 1 km in screen pixels at the centre latitude. We use this to decide
        // whether to draw labels and at what spacing.
        val (centerLat, _) = CoordinateConverter.webMercatorToWgs84(0.0, camera.centerY)
        val metersPerPixelGround = camera.metersPerPixel() * Math.cos(centerLat * Math.PI / 180.0)
        val kmPx = if (metersPerPixelGround > 0) 1000.0 / metersPerPixelGround else 0.0
        val labelVisible = kmPx >= LABEL_MIN_KM_PIXELS

        val minor = Path()
        val major = Path()
        val labels = mutableListOf<Label>()

        for (zone in zones) {
            val (zoneW, zoneE) = Utm.zoneLonRangeDeg(zone)
            // Skip zones that don't overlap the viewport at all.
            if (eLon < zoneW || wLon > zoneE) continue

            val effectiveWLon = max(wLon, zoneW)
            val effectiveELon = min(eLon, zoneE)

            // MGA bounds from the four corners of the (clipped) viewport in this zone.
            val (_, swLat) = Pair(0.0, 0.0)  // unused placeholder to keep layout
            val sLat = run {
                val (lat, _) = CoordinateConverter.webMercatorToWgs84(0.0, minMy)
                lat
            }
            val nLat = run {
                val (lat, _) = CoordinateConverter.webMercatorToWgs84(0.0, maxMy)
                lat
            }
            val mgaCorners = listOf(
                Utm.wgs84ToMga(sLat, effectiveWLon, zone),
                Utm.wgs84ToMga(sLat, effectiveELon, zone),
                Utm.wgs84ToMga(nLat, effectiveWLon, zone),
                Utm.wgs84ToMga(nLat, effectiveELon, zone)
            )
            val minE = mgaCorners.minOf { it.first }
            val maxE = mgaCorners.maxOf { it.first }
            val minN = mgaCorners.minOf { it.second }
            val maxN = mgaCorners.maxOf { it.second }

            val minKmE = (minE / 1000.0).toLong() - 1
            val maxKmE = (maxE / 1000.0).toLong() + 1
            val minKmN = (minN / 1000.0).toLong() - 1
            val maxKmN = (maxN / 1000.0).toLong() + 1

            val kmCount = (maxKmE - minKmE) + (maxKmN - minKmN)
            if (kmCount > MAX_LINES_PER_ZONE) {
                // Too zoomed out — draw only 10 km lines as "minor", skip 1 km.
                addNorthingLines(major, labels, zone, minKmN * 1000, maxKmN * 1000, 10_000L,
                    minE, maxE, effectiveWLon, effectiveELon, labelVisible, camera)
                addEastingLines(major, labels, zone, minKmE * 1000, maxKmE * 1000, 10_000L,
                    minN, maxN, effectiveWLon, effectiveELon, labelVisible, camera)
            } else {
                addNorthingLines(minor, labels, zone, minKmN * 1000, maxKmN * 1000, 1_000L,
                    minE, maxE, effectiveWLon, effectiveELon, false, camera)
                addEastingLines(minor, labels, zone, minKmE * 1000, maxKmE * 1000, 1_000L,
                    minN, maxN, effectiveWLon, effectiveELon, false, camera)
                // Then major lines on top with labels.
                addNorthingLines(major, labels, zone,
                    ((minKmN + 9) / 10) * 10_000, (maxKmN / 10) * 10_000, 10_000L,
                    minE, maxE, effectiveWLon, effectiveELon, labelVisible, camera)
                addEastingLines(major, labels, zone,
                    ((minKmE + 9) / 10) * 10_000, (maxKmE / 10) * 10_000, 10_000L,
                    minN, maxN, effectiveWLon, effectiveELon, labelVisible, camera)
            }
        }

        cachedMinorPath = minor
        cachedMajorPath = major
        cachedLabels = labels
        cachedCenterX = camera.centerX
        cachedCenterY = camera.centerY
        cachedZoom = camera.zoom
        cachedW = camera.viewWidth
        cachedH = camera.viewHeight
    }

    /**
     * Add east-west grid lines at northings from [startN] to [endN] (inclusive) in
     * steps of [stepN], running across eastings [minE]..[maxE] in the given zone.
     * Each line is sampled at a few eastings and joined as a polyline, so the
     * UTM → Mercator curvature shows correctly.
     */
    private fun addEastingLines(
        path: Path, labels: MutableList<Label>,
        zone: Int, startN: Long, endN: Long, stepN: Long,
        minE: Double, maxE: Double,
        clipWLon: Double, clipELon: Double,
        writeLabels: Boolean, camera: MapCamera
    ) {
        var northing = startN
        while (northing <= endN) {
            drawMgaLine(path, zone,
                fromE = minE, toE = maxE, northing = northing.toDouble(),
                isEasting = false, clipWLon = clipWLon, clipELon = clipELon,
                camera = camera)
            if (writeLabels) {
                // Place a label near the middle easting of the line.
                val midE = (minE + maxE) / 2.0
                val (lat, lon) = Utm.mgaToWgs84(zone, midE, northing.toDouble())
                if (lon in clipWLon..clipELon) {
                    val (mx, my) = CoordinateConverter.wgs84ToWebMercator(lat, lon)
                    val sx = camera.worldToScreenX(mx)
                    val sy = camera.worldToScreenY(my)
                    labels += Label("${northing / 1000} km N z$zone", sx + 6f, sy - 6f)
                }
            }
            northing += stepN
        }
    }

    private fun addNorthingLines(
        path: Path, labels: MutableList<Label>,
        zone: Int, startE: Long, endE: Long, stepE: Long,
        minN: Double, maxN: Double,
        clipWLon: Double, clipELon: Double,
        writeLabels: Boolean, camera: MapCamera
    ) {
        var easting = startE
        while (easting <= endE) {
            drawMgaLine(path, zone,
                fromE = easting.toDouble(), toE = easting.toDouble(),
                northing = minN, isEasting = true, minN = minN, maxN = maxN,
                clipWLon = clipWLon, clipELon = clipELon, camera = camera)
            if (writeLabels) {
                val midN = (minN + maxN) / 2.0
                val (lat, lon) = Utm.mgaToWgs84(zone, easting.toDouble(), midN)
                if (lon in clipWLon..clipELon) {
                    val (mx, my) = CoordinateConverter.wgs84ToWebMercator(lat, lon)
                    val sx = camera.worldToScreenX(mx)
                    val sy = camera.worldToScreenY(my)
                    labels += Label("${easting / 1000} km E z$zone", sx + 6f, sy - 6f)
                }
            }
            easting += stepE
        }
    }

    /**
     * Draw one grid line as a polyline. For a north-south line [isEasting] is
     * true: [fromE]=[toE]=easting, varying northing from [minN] to [maxN]. For
     * an east-west line [isEasting] is false: northing is fixed, easting sweeps
     * [fromE]..[toE].
     *
     * Samples SAMPLES_PER_LINE points so the line follows the UTM curvature in
     * Mercator. Each point is projected MGA → WGS84 → Mercator → screen.
     */
    private fun drawMgaLine(
        path: Path, zone: Int,
        fromE: Double, toE: Double, northing: Double,
        isEasting: Boolean,
        minN: Double = 0.0, maxN: Double = 0.0,
        clipWLon: Double, clipELon: Double,
        camera: MapCamera
    ) {
        var started = false
        for (i in 0..SAMPLES_PER_LINE) {
            val t = i.toDouble() / SAMPLES_PER_LINE
            val e = if (isEasting) fromE else fromE + (toE - fromE) * t
            val n = if (isEasting) minN + (maxN - minN) * t else northing
            val (lat, lon) = Utm.mgaToWgs84(zone, e, n)
            if (lon < clipWLon || lon > clipELon) {
                started = false
                continue
            }
            val (mx, my) = CoordinateConverter.wgs84ToWebMercator(lat, lon)
            val sx = camera.worldToScreenX(mx)
            val sy = camera.worldToScreenY(my)
            if (!started) {
                path.moveTo(sx, sy)
                started = true
            } else {
                path.lineTo(sx, sy)
            }
        }
    }

    private data class Label(val text: String, val x: Float, val y: Float)

    companion object {
        private const val REBUILD_FRACTION = 0.10
        private const val REBUILD_ZOOM_FRACTION = 0.10f
        private const val SAMPLES_PER_LINE = 4
        private const val MAX_LINES_PER_ZONE = 400
        private const val LABEL_MIN_KM_PIXELS = 60.0
    }
}
