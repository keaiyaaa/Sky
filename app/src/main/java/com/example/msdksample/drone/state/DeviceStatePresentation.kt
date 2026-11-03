package com.example.msdksample.drone.state

/** Connection UI only. READY never grants permission to execute a device action. */
data class DeviceStatePresentation(val title: String, val status: String, val details: String) {
    companion object {
        fun from(state: DeviceStateSnapshot): DeviceStatePresentation {
            val source = when (state.mode) {
                DeviceMode.OFFLINE -> "离线"
                DeviceMode.DEVICE -> "DJI 设备"
                DeviceMode.DJI_SIMULATOR -> "DJI SDK 模拟器"
                DeviceMode.LOCAL_DEMO -> "本地演示"
            }
            val link = when (state.linkState) {
                LinkState.DISCONNECTED -> "未连接"
                LinkState.CONNECTING -> "连接中"
                LinkState.CONNECTED -> "已连接"
                LinkState.DISCONNECTING -> "断开中"
                LinkState.ERROR -> "连接失败"
            }
            val registration = when (state.sdkRegistration) {
                SdkRegistrationState.NOT_INITIALIZED -> "未初始化"
                SdkRegistrationState.INITIALIZING -> "初始化中"
                SdkRegistrationState.REGISTERING -> "注册中"
                SdkRegistrationState.REGISTERED -> "已注册"
                SdkRegistrationState.REGISTER_FAILED -> "注册失败"
            }
            val readiness = when (state.readiness) {
                Readiness.UNAVAILABLE -> "不可用"
                Readiness.CONNECTING -> "等待连接"
                Readiness.READY -> "连接层就绪"
                Readiness.DEGRADED -> "待确认"
                Readiness.ERROR -> "异常"
            }
            val title = when (state.mode) {
                DeviceMode.LOCAL_DEMO -> "本地演示设备"
                DeviceMode.DJI_SIMULATOR -> "DJI SDK 模拟器"
                DeviceMode.DEVICE -> state.device?.productType ?: "型号未知"
                DeviceMode.OFFLINE -> "未连接设备"
            }
            val status = "$source · $link · $readiness"
            val details = buildString {
                appendLine(status)
                appendLine("SDK：$registration")
                appendLine("会话：${state.generation} / 版本：${state.version}")
                appendLine("型号：${state.device?.productType ?: "未知"}")
                appendLine("序列号：${state.device?.serialNumber ?: "未知"}")
                appendLine("固件：${state.device?.firmwareVersion ?: "未知"}")
                if (state.errorCode != null) appendLine("状态码：${state.errorCode}")
                if (state.userMessage != null) appendLine(state.userMessage)
                append(if (state.mode == DeviceMode.LOCAL_DEMO) "本地演示不会下发飞机指令" else "连接状态不代表动作能力已经验证")
            }
            return DeviceStatePresentation(title, status, details)
        }
    }
}
