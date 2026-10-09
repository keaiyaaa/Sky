package com.example.msdksample.kami.mission

import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RouteValidation
import com.example.msdksample.kami.model.Waypoint
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WpmlRouteExporter(
    private val outputDirectory: File
) {
    fun export(route: RoutePlan, waypoints: List<Waypoint>): File {
        require(waypoints.isNotEmpty()) { "航线至少需要 1 个航点" }
        RouteValidation.normalizeRoute(route)
        RouteValidation.requireValidWaypoints(waypoints)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val fileName = safeFileName("${route.name}_${System.currentTimeMillis()}.kmz")
        val outputFile = File(outputDirectory, fileName)

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.putNextEntry(ZipEntry("wpmz/template.kml"))
            zip.write(buildTemplateKml(route, waypoints).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("wpmz/waylines.wpml"))
            zip.write(buildWaylinesWpml(route, waypoints).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return outputFile
    }

    private fun buildTemplateKml(route: RoutePlan, waypoints: List<Waypoint>): String {
        val placemarks = waypoints.joinToString(separator = "\n") { waypoint ->
            """
            <Placemark>
                <Point>
                    <coordinates>${format(waypoint.longitude)},${format(waypoint.latitude)}</coordinates>
                </Point>
                <wpml:index>${waypoint.sequence}</wpml:index>
                <wpml:ellipsoidHeight>${format(waypoint.altitude)}</wpml:ellipsoidHeight>
                <wpml:height>${format(waypoint.altitude)}</wpml:height>
                <wpml:useGlobalHeight>1</wpml:useGlobalHeight>
                <wpml:useGlobalSpeed>1</wpml:useGlobalSpeed>
                <wpml:useGlobalHeadingParam>1</wpml:useGlobalHeadingParam>
                <wpml:useGlobalTurnParam>1</wpml:useGlobalTurnParam>
            </Placemark>
            """.trimIndent()
        }

        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:wpml="http://www.dji.com/wpmz/1.0.6">
            <Document>
                <wpml:author>TianShuFlyer</wpml:author>
                <wpml:createTime>${route.createdAt}</wpml:createTime>
                <wpml:updateTime>${route.updatedAt}</wpml:updateTime>
                <wpml:missionConfig>
                    <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>
                    <wpml:finishAction>goHome</wpml:finishAction>
                    <wpml:exitOnRCLost>executeLostAction</wpml:exitOnRCLost>
                    <wpml:executeRCLostAction>goBack</wpml:executeRCLostAction>
                    <wpml:takeOffSecurityHeight>20</wpml:takeOffSecurityHeight>
                    <wpml:globalTransitionalSpeed>8</wpml:globalTransitionalSpeed>
                </wpml:missionConfig>
                <Folder>
                    <wpml:templateId>0</wpml:templateId>
                    <wpml:templateType>waypoint</wpml:templateType>
                    <wpml:waylineCoordinateSysParam>
                        <wpml:coordinateMode>WGS84</wpml:coordinateMode>
                        <wpml:heightMode>relativeToStartPoint</wpml:heightMode>
                    </wpml:waylineCoordinateSysParam>
                    <wpml:autoFlightSpeed>8</wpml:autoFlightSpeed>
                    <wpml:globalWaypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:globalWaypointTurnMode>
                    <wpml:globalUseStraightLine>1</wpml:globalUseStraightLine>
                    <name>${escapeXml(route.name)}</name>
                    <description>${escapeXml(route.taskName)}</description>
                    $placemarks
                </Folder>
            </Document>
        </kml>
        """.trimIndent()
    }

    private fun buildWaylinesWpml(route: RoutePlan, waypoints: List<Waypoint>): String {
        val placemarks = waypoints.joinToString(separator = "\n") { waypoint ->
            """
            <Placemark>
                <Point>
                    <coordinates>${format(waypoint.longitude)},${format(waypoint.latitude)}</coordinates>
                </Point>
                <wpml:index>${waypoint.sequence}</wpml:index>
                <wpml:executeHeight>${format(waypoint.altitude)}</wpml:executeHeight>
                <wpml:waypointSpeed>8</wpml:waypointSpeed>
                <wpml:waypointHeadingParam>
                    <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>
                    <wpml:waypointHeadingAngle>0</wpml:waypointHeadingAngle>
                    <wpml:waypointPoiPoint>0.000000,0.000000,0.000000</wpml:waypointPoiPoint>
                    <wpml:waypointHeadingPathMode>clockwise</wpml:waypointHeadingPathMode>
                </wpml:waypointHeadingParam>
                <wpml:waypointTurnParam>
                    <wpml:waypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:waypointTurnMode>
                    <wpml:waypointTurnDampingDist>0</wpml:waypointTurnDampingDist>
                </wpml:waypointTurnParam>
                <wpml:useStraightLine>1</wpml:useStraightLine>
            </Placemark>
            """.trimIndent()
        }

        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:wpml="http://www.dji.com/wpmz/1.0.6">
            <Document>
                <wpml:missionConfig>
                    <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>
                    <wpml:finishAction>goHome</wpml:finishAction>
                    <wpml:exitOnRCLost>executeLostAction</wpml:exitOnRCLost>
                    <wpml:executeRCLostAction>goBack</wpml:executeRCLostAction>
                    <wpml:takeOffSecurityHeight>20</wpml:takeOffSecurityHeight>
                    <wpml:globalTransitionalSpeed>8</wpml:globalTransitionalSpeed>
                </wpml:missionConfig>
                <Folder>
                    <wpml:templateId>0</wpml:templateId>
                    <wpml:executeHeightMode>relativeToStartPoint</wpml:executeHeightMode>
                    <wpml:waylineId>0</wpml:waylineId>
                    <wpml:autoFlightSpeed>8</wpml:autoFlightSpeed>
                    <name>${escapeXml(route.name)}</name>
                    <description>${escapeXml(route.taskName)}</description>
                    $placemarks
                </Folder>
            </Document>
        </kml>
        """.trimIndent()
    }

    private fun safeFileName(value: String): String {
        return value.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
    }

    private fun format(value: Double): String {
        return String.format(Locale.US, "%.7f", value)
    }

    private fun format(value: Float): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
