package com.example.msdksample.drone.command

enum class BasicFlightAction { TAKEOFF, LAND, RETURN_HOME, HOVER }
enum class SimulatedAircraftState { ON_GROUND, AIRBORNE, RETURNING, HOVERING }

/** Process-local demonstration state. It never calls the DJI SDK. */
class SimulatedFlightCommandExecutor {
    var state: SimulatedAircraftState = SimulatedAircraftState.ON_GROUND
        private set

    fun execute(action: BasicFlightAction): String {
        return when (action) {
            BasicFlightAction.TAKEOFF -> {
                if (state != SimulatedAircraftState.ON_GROUND) return "模拟拒绝：飞机不在地面"
                state = SimulatedAircraftState.AIRBORNE
                "模拟起飞完成"
            }
            BasicFlightAction.LAND -> {
                if (state !in setOf(SimulatedAircraftState.AIRBORNE, SimulatedAircraftState.HOVERING)) {
                    return "模拟拒绝：飞机未处于空中"
                }
                state = SimulatedAircraftState.ON_GROUND
                "模拟降落完成"
            }
            BasicFlightAction.RETURN_HOME -> {
                if (state !in setOf(SimulatedAircraftState.AIRBORNE, SimulatedAircraftState.HOVERING)) {
                    return "模拟拒绝：飞机未处于空中"
                }
                state = SimulatedAircraftState.HOVERING
                "模拟返航至起点并悬停"
            }
            BasicFlightAction.HOVER -> {
                if (state !in setOf(SimulatedAircraftState.AIRBORNE, SimulatedAircraftState.HOVERING)) {
                    return "模拟拒绝：飞机未处于空中"
                }
                state = SimulatedAircraftState.HOVERING
                "模拟悬停"
            }
        }
    }
}
