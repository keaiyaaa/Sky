package com.example.msdksample.drone.command

enum class BasicFlightAction { TAKEOFF, LAND, RETURN_HOME, HOVER }
enum class SimulatedAircraftState { ON_GROUND, AIRBORNE, RETURNING, HOVERING }
data class SimulatedFlightCommandResult(val accepted: Boolean,val message: String)

/** Process-local demonstration state. It never calls the DJI SDK. */
class SimulatedFlightCommandExecutor {
    var state: SimulatedAircraftState = SimulatedAircraftState.ON_GROUND
        private set

    fun execute(action: BasicFlightAction): String {
        return executeResult(action).message
    }
    fun executeResult(action: BasicFlightAction): SimulatedFlightCommandResult {
        if(action==BasicFlightAction.TAKEOFF && state!=SimulatedAircraftState.ON_GROUND)
            return SimulatedFlightCommandResult(false,"模拟拒绝：飞机不在地面")
        if(action!=BasicFlightAction.TAKEOFF && state !in setOf(SimulatedAircraftState.AIRBORNE,SimulatedAircraftState.HOVERING))
            return SimulatedFlightCommandResult(false,"模拟拒绝：飞机未处于空中")
        state=when(action) {
            BasicFlightAction.TAKEOFF -> SimulatedAircraftState.AIRBORNE
            BasicFlightAction.LAND -> SimulatedAircraftState.ON_GROUND
            else -> SimulatedAircraftState.HOVERING
        }
        return SimulatedFlightCommandResult(true,when(action) {
            BasicFlightAction.TAKEOFF -> "模拟起飞完成"
            BasicFlightAction.LAND -> "模拟降落完成"
            BasicFlightAction.RETURN_HOME -> "模拟返航至起点并悬停"
            BasicFlightAction.HOVER -> "模拟悬停"
        })
    }
}
