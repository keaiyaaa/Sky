package com.example.msdksample.drone.command

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatedFlightCommandExecutorTest {
    @Test fun rejectsAirborneCommandsOnGround() {
        val executor = SimulatedFlightCommandExecutor()
        assertEquals("模拟拒绝：飞机未处于空中", executor.execute(BasicFlightAction.HOVER))
        assertEquals(SimulatedAircraftState.ON_GROUND, executor.state)
    }

    @Test fun followsBasicCommandTransitionsWithoutDevice() {
        val executor = SimulatedFlightCommandExecutor()
        assertEquals("模拟起飞完成", executor.execute(BasicFlightAction.TAKEOFF))
        assertEquals("模拟悬停", executor.execute(BasicFlightAction.HOVER))
        assertEquals("模拟返航至起点并悬停", executor.execute(BasicFlightAction.RETURN_HOME))
        assertEquals("模拟降落完成", executor.execute(BasicFlightAction.LAND))
        assertEquals(SimulatedAircraftState.ON_GROUND, executor.state)
    }
}
