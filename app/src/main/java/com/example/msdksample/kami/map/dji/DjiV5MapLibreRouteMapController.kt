@file:Suppress("DEPRECATION")

package com.example.msdksample.kami.map.dji

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.Polygon
import com.mapbox.mapboxsdk.annotations.PolygonOptions
import com.mapbox.mapboxsdk.annotations.Polyline
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.example.msdksample.kami.map.RouteMapCallbacks
import com.example.msdksample.kami.map.RouteMapController
import com.example.msdksample.kami.model.FlightZone
import com.example.msdksample.kami.model.Waypoint
import com.example.msdksample.kami.model.ZoneType
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class DjiV5MapLibreRouteMapController(
    private val context: Context,
    private val mapView: MapView
) : RouteMapController {

    private var callbacks: RouteMapCallbacks? = null
    private var mapboxMap: MapboxMap? = null
    private var styleLoaded = false
    private var pendingLocationEnable = false
    private val waypointMarkers = mutableMapOf<String, Marker>()
    private val markerWaypointIds = mutableMapOf<Long, String>()
    private val zonePolygons = mutableMapOf<Polygon, FlightZone>()
    private var flownPolyline: Polyline? = null
    private var unflownPolyline: Polyline? = null
    private var aircraftMarker: Marker? = null
    private var draggingMarker: Marker? = null
    private var draggingWaypointId: String? = null

    override fun setCallbacks(callbacks: RouteMapCallbacks) {
        this.callbacks = callbacks
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            mapView.onCreate(savedInstanceState)
            mapView.getMapAsync { readyMap ->
                setupReadyMap(readyMap)
            }
        } catch (error: Exception) {
            Log.e(TAG, "MapLibre init failed: ${error.message}", error)
            Toast.makeText(context, "DJI 地图初始化异常: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupReadyMap(readyMap: MapboxMap) {
        mapboxMap = readyMap
        readyMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE),
                DEFAULT_ZOOM.toDouble()
            )
        )
        readyMap.addOnMapClickListener { latLng ->
            callbacks?.onMapPointSelected(latLng.latitude, latLng.longitude)
            true
        }
        readyMap.setOnPolygonClickListener { polygon ->
            zonePolygons[polygon]?.let { zone ->
                callbacks?.onFlightZoneSelected(zone)
                true
            } ?: false
        }
        readyMap.setOnMarkerClickListener { marker ->
            val waypointId = markerWaypointIds[marker.id]
            if (waypointId != null) {
                marker.showInfoWindow(readyMap, mapView)
            }
            waypointId != null
        }
        mapView.setOnTouchListener { _, event ->
            handleWaypointDragTouch(event, readyMap)
        }

        readyMap.setStyle(buildMapStyle()) { style ->
            styleLoaded = true
            Log.i(TAG, "Map style loaded successfully")
            if (pendingLocationEnable) {
                Log.i(TAG, "Enabling location component (pending)")
                enableLocationComponent(readyMap, style)
            }
            callbacks?.onMapReady()
        }
    }

    override fun onResume() {
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
    }

    override fun onDestroy() {
        mapView.onDestroy()
    }

    override fun enableMyLocation() {
        pendingLocationEnable = true
        val map = mapboxMap ?: return
        val style = map.style ?: return
        enableLocationComponent(map, style)
    }

    override fun moveTo(latitude: Double, longitude: Double, zoom: Float) {
        mapboxMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(latitude, longitude),
                zoom.toDouble()
            )
        )
    }

    override fun renderFlightZones(zones: List<FlightZone>) {
        val map = mapboxMap ?: return
        zonePolygons.keys.forEach { map.removePolygon(it) }
        zonePolygons.clear()

        zones.forEach { zone ->
            val colors = zoneColors(zone.type)
            val polygon = map.addPolygon(
                PolygonOptions()
                    .addAll(buildCirclePolygon(zone))
                    .fillColor(colors.first)
                    .strokeColor(colors.second)
                    .alpha(0.7f)
            )
            zonePolygons[polygon] = zone
        }
    }

    override fun renderWaypoints(waypoints: List<Waypoint>) {
        val map = mapboxMap ?: return
        val activeIds = waypoints.map { it.id }.toSet()
        val removedIds = waypointMarkers.keys.filter { it !in activeIds }
        removedIds.forEach { id ->
            waypointMarkers.remove(id)?.let { marker ->
                markerWaypointIds.remove(marker.id)
                map.removeMarker(marker)
            }
        }

        waypoints.forEachIndexed { index, waypoint ->
            val position = LatLng(waypoint.latitude, waypoint.longitude)
            val marker = waypointMarkers[waypoint.id]
            if (marker == null) {
                val added = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title("航点 ${index + 1}")
                        .snippet(formatCoordinate(waypoint))
                )
                waypointMarkers[waypoint.id] = added
                markerWaypointIds[added.id] = waypoint.id
            } else {
                marker.position = position
                marker.title = "航点 ${index + 1}"
                marker.snippet = formatCoordinate(waypoint)
                map.updateMarker(marker)
            }
        }
    }

    override fun renderRoute(waypoints: List<Waypoint>, flownWaypointIndex: Int) {
        val map = mapboxMap ?: return
        if (waypoints.size < 2) {
            flownPolyline?.let { map.removePolyline(it) }
            flownPolyline = null
            unflownPolyline?.let { map.removePolyline(it) }
            unflownPolyline = null
            return
        }

        val allPoints = waypoints.map { LatLng(it.latitude, it.longitude) }
        val flownPoints = if (flownWaypointIndex >= 1) {
            allPoints.take(flownWaypointIndex + 1)
        } else {
            emptyList()
        }
        val unflownStart = flownWaypointIndex.coerceAtLeast(0)
        val unflownPoints = allPoints.drop(unflownStart)

        flownPolyline = renderPolyline(
            map = map,
            existing = flownPolyline,
            points = flownPoints,
            color = Color.parseColor("#EF4444")
        )
        unflownPolyline = renderPolyline(
            map = map,
            existing = unflownPolyline,
            points = unflownPoints,
            color = Color.parseColor("#00A86B")
        )
    }

    override fun showAircraftPosition(latitude: Double, longitude: Double) {
        val map = mapboxMap ?: return
        val position = LatLng(latitude, longitude)
        val marker = aircraftMarker
        if (marker == null) {
            aircraftMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("飞机位置")
            )
        } else {
            marker.position = position
            map.updateMarker(marker)
        }
    }

    override fun clear() {
        val map = mapboxMap ?: return
        waypointMarkers.values.forEach { map.removeMarker(it) }
        waypointMarkers.clear()
        markerWaypointIds.clear()
        flownPolyline?.let { map.removePolyline(it) }
        flownPolyline = null
        unflownPolyline?.let { map.removePolyline(it) }
        unflownPolyline = null
        aircraftMarker?.let { map.removeMarker(it) }
        aircraftMarker = null
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(map: MapboxMap, style: Style) {
        if (!hasLocationPermission()) {
            return
        }
        runCatching {
            val locationComponent = map.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style).build()
            )
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.NONE
            locationComponent.renderMode = RenderMode.NORMAL
        }.onFailure { error ->
            Log.e(TAG, "Enable MapLibre location failed: ${error.message}", error)
        }
    }

    private fun renderPolyline(
        map: MapboxMap,
        existing: Polyline?,
        points: List<LatLng>,
        color: Int
    ): Polyline? {
        if (points.size < 2) {
            existing?.let { map.removePolyline(it) }
            return null
        }
        return if (existing == null) {
            map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .width(6f)
                    .color(color)
            )
        } else {
            existing.points = points
            existing.color = color
            existing.width = 6f
            map.updatePolyline(existing)
            existing
        }
    }

    private fun handleWaypointDragTouch(event: MotionEvent, map: MapboxMap): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val marker = findTouchedWaypointMarker(event.x, event.y, map)
                if (marker == null) {
                    false
                } else {
                    draggingMarker = marker
                    draggingWaypointId = markerWaypointIds[marker.id]
                    true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                updateDraggingWaypoint(event, map)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val consumed = updateDraggingWaypoint(event, map)
                draggingMarker = null
                draggingWaypointId = null
                if (consumed) {
                    mapView.performClick()
                }
                consumed
            }

            else -> draggingMarker != null
        }
    }

    private fun updateDraggingWaypoint(event: MotionEvent, map: MapboxMap): Boolean {
        val marker = draggingMarker ?: return false
        val waypointId = draggingWaypointId ?: return false
        val position = map.projection.fromScreenLocation(PointF(event.x, event.y))
        marker.position = position
        map.updateMarker(marker)
        callbacks?.onWaypointDragged(waypointId, position.latitude, position.longitude)
        return true
    }

    private fun findTouchedWaypointMarker(
        x: Float,
        y: Float,
        map: MapboxMap
    ): Marker? {
        return waypointMarkers.values
            .map { marker ->
                val screenPoint = map.projection.toScreenLocation(marker.position)
                marker to hypot(
                    (screenPoint.x - x).toDouble(),
                    (screenPoint.y - y).toDouble()
                )
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= DRAG_HIT_RADIUS_PX }
            ?.first
    }

    private fun buildCirclePolygon(zone: FlightZone): List<LatLng> {
        val result = mutableListOf<LatLng>()
        val centerLat = Math.toRadians(zone.centerLatitude)
        val centerLon = Math.toRadians(zone.centerLongitude)
        val angularDistance = zone.radiusMeters / EARTH_RADIUS_METERS
        repeat(CIRCLE_SEGMENTS + 1) { index ->
            val bearing = 2.0 * Math.PI * index / CIRCLE_SEGMENTS
            val lat = asin(
                sin(centerLat) * cos(angularDistance) +
                    cos(centerLat) * sin(angularDistance) * cos(bearing)
            )
            val lon = centerLon + atan2(
                sin(bearing) * sin(angularDistance) * cos(centerLat),
                cos(angularDistance) - sin(centerLat) * sin(lat)
            )
            result.add(LatLng(Math.toDegrees(lat), Math.toDegrees(lon)))
        }
        return result
    }

    private fun zoneColors(type: ZoneType): Pair<Int, Int> {
        return when (type) {
            ZoneType.NO_FLY -> Color.argb(90, 239, 68, 68) to Color.argb(220, 239, 68, 68)
            ZoneType.RESTRICTED -> Color.argb(70, 217, 119, 6) to Color.argb(190, 217, 119, 6)
            ZoneType.SAFE -> Color.argb(55, 34, 197, 94) to Color.argb(160, 34, 197, 94)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun readMapStyleUrl(): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(MAP_STYLE_META_DATA).orEmpty()
        }.getOrDefault("").ifBlank { DEFAULT_STYLE_URL }
    }

    @Suppress("DEPRECATION")
    private fun readTianDiTuToken(): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(TIANDITU_TOKEN_META_DATA).orEmpty()
        }.getOrDefault("").trim()
    }

    private fun buildMapStyle(): Style.Builder {
        // 优先级: 高德地图 > 天地图 > 默认 MapLibre 演示瓦片
        val amapKey = readAmapKey()
        if (amapKey.isNotBlank()) {
            Log.i(TAG, "Using AMap raster tiles (key=${amapKey.take(8)}...)")
            return Style.Builder().fromJson(buildAmapStyleJson())
        }
        val tianDiTuToken = readTianDiTuToken()
        if (tianDiTuToken.isNotBlank()) {
            Log.i(TAG, "Using TianDiTu tiles")
            return Style.Builder().fromJson(buildTianDiTuStyleJson(tianDiTuToken))
        }
        Log.i(TAG, "Falling back to default demo tiles")
        return Style.Builder().fromUrl(readMapStyleUrl())
    }

    @Suppress("DEPRECATION")
    private fun readAmapKey(): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(AMAP_KEY_META_DATA).orEmpty()
        }.getOrDefault("").trim()
    }

    private fun buildAmapStyleJson(): String {
        return """
            {
              "version": 8,
              "name": "AMap Raster Style",
              "sources": {
                "amap-base": {
                  "type": "raster",
                  "tiles": [
                    "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                    "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                    "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                    "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"
                  ],
                  "tileSize": 256,
                  "attribution": "高德地图"
                }
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#f5f0eb"
                  }
                },
                {
                  "id": "amap-base",
                  "type": "raster",
                  "source": "amap-base",
                  "minzoom": 0,
                  "maxzoom": 18
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildTianDiTuStyleJson(token: String): String {
        val vectorTiles = buildTianDiTuTileUrls("vec_w", token)
        val labelTiles = buildTianDiTuTileUrls("cva_w", token)
        return """
            {
              "version": 8,
              "sources": {
                "tianditu-vector": {
                  "type": "raster",
                  "tiles": $vectorTiles,
                  "tileSize": 256
                },
                "tianditu-label": {
                  "type": "raster",
                  "tiles": $labelTiles,
                  "tileSize": 256
                }
              },
              "layers": [
                {
                  "id": "tianditu-vector",
                  "type": "raster",
                  "source": "tianditu-vector",
                  "minzoom": 0,
                  "maxzoom": 18
                },
                {
                  "id": "tianditu-label",
                  "type": "raster",
                  "source": "tianditu-label",
                  "minzoom": 0,
                  "maxzoom": 18
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildTianDiTuTileUrls(layer: String, token: String): String {
        val escapedToken = token.replace("\\", "\\\\").replace("\"", "\\\"")
        return (0..7).joinToString(prefix = "[", postfix = "]") { server ->
            "\"https://t$server.tianditu.gov.cn/DataServer?T=$layer&x={x}&y={y}&l={z}&tk=$escapedToken\""
        }
    }

    private fun formatCoordinate(waypoint: Waypoint): String {
        return String.format(Locale.US, "(%.6f, %.6f)", waypoint.latitude, waypoint.longitude)
    }

    companion object {
        private const val TAG = "DjiV5MapLibre"
        private const val DEFAULT_LATITUDE = 31.2047    // 上海张江
        private const val DEFAULT_LONGITUDE = 121.5809
        private const val DEFAULT_ZOOM = 15f
        private const val EARTH_RADIUS_METERS = 6378137.0
        private const val CIRCLE_SEGMENTS = 72
        private const val DRAG_HIT_RADIUS_PX = 44f
        private const val DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json"
        private const val MAP_STYLE_META_DATA = "com.example.msdksample.kami.maplibre.STYLE_URL"
        private const val TIANDITU_TOKEN_META_DATA = "com.example.msdksample.kami.tianditu.TOKEN"
        private const val AMAP_KEY_META_DATA = "com.example.msdksample.kami.amap.API_KEY"
    }
}
