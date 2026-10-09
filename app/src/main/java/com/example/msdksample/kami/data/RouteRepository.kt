package com.example.msdksample.kami.data

import android.content.Context
import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.RouteValidation
import com.example.msdksample.kami.model.Waypoint

class RouteRepository(context: Context) {
    private val routeDao: RouteDao = SqliteRouteDao(
        RouteDatabaseHelper(context.applicationContext)
    )

    fun getRouteSummaries(): List<RouteSummary> {
        return routeDao.getRouteSummaries()
    }

    fun getRoute(routeId: String): RouteMission? {
        return routeDao.getRoute(routeId)
    }

    fun saveRoute(route: RoutePlan, waypoints: List<Waypoint>) {
        RouteValidation.normalizeRoute(route)
        RouteValidation.requireValidWaypoints(waypoints)
        routeDao.saveRoute(route, waypoints)
    }

    fun deleteRoute(routeId: String) {
        routeDao.deleteRoute(routeId)
    }

    fun loadOrCreateInitialRoute(): RouteMission {
        val first = routeDao.getRouteSummaries().firstOrNull()
        if (first != null) {
            return routeDao.getRoute(first.route.id) ?: createRoute()
        }
        return createRoute()
    }

    fun createRoute(): RouteMission {
        val nextNumber = routeDao.getRouteCount() + 1
        val route = RoutePlan(name = "航线 $nextNumber")
        routeDao.saveRoute(route, emptyList())
        return RouteMission(route, emptyList())
    }
}
