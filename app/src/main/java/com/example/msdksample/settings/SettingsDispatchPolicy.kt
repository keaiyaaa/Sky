package com.example.msdksample.settings

import com.example.msdksample.drone.state.*

object SettingsDispatchPolicy {
    /** Physical parameter capabilities/readback are not verified. This phase permits local demo only. */
    fun rejection(state: DeviceStateSnapshot, expectedGeneration: Long): String? = when {
        state.generation != expectedGeneration -> "STALE_GENERATION：连接会话已变化"
        state.linkState != LinkState.CONNECTED -> "设备未连接，未同步"
        state.mode == DeviceMode.LOCAL_DEMO && state.readiness == Readiness.READY -> null
        state.mode == DeviceMode.DEVICE -> "真实参数能力和读回尚未验证，未下发"
        state.mode == DeviceMode.DJI_SIMULATOR -> "SDK 模拟器参数适配尚未验证，未下发"
        else -> "设备状态不可用，未同步"
    }
}
