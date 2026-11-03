package com.example.msdksample.kami.data

import android.content.Context
import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.RouteValidation
import com.example.msdksample.kami.model.Waypoint

class RouteRepository(context: Context) : java.io.Closeable {
    private val helper = RouteDatabaseHelper(context.applicationContext)
    private val routeDao: RouteDao = SqliteRouteDao(
        helper
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

    /**
     * 关闭本仓库持有的数据库连接，不影响使用独立连接的模拟执行器。
     * @return {Unit} 无返回值。
     */
    override fun close() = helper.close()
}
