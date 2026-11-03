package com.example.msdksample.asuka

import com.example.msdksample.drone.DroneManager

/** Legacy inspection entry reads the same connection state as the home and settings pages. */
object DjiConnectionManager {
    interface ConnectionStatusListener {
        fun onConnected(productName: String)
        fun onDisconnected()
        fun onConnectionFailed(error: String)
        fun onSDKInitialized()
        fun onSDKInitFailed(error: String)
    }

    private var listener: ConnectionStatusListener? = null
    private val manager get() = DroneManager.getInstance()
    private val bridge = object : DroneManager.ConnectionStateListener {
        override fun onSdkStateChanged(state: DroneManager.SdkState, message: String?) {
            when (state) {
                DroneManager.SdkState.REGISTERED -> listener?.onSDKInitialized()
                DroneManager.SdkState.REGISTER_FAILED -> listener?.onSDKInitFailed(message ?: "SDK 注册失败")
                else -> Unit
            }
        }
        override fun onConnectionChanged(connected: Boolean, device: DroneManager.DroneDevice?) {
            if (connected) listener?.onConnected(getConnectedProductName()) else listener?.onDisconnected()
        }
        override fun onDeviceInfoUpdated(device: DroneManager.DroneDevice?) {
            if (manager.isConnected()) listener?.onConnected(getConnectedProductName())
        }
        override fun onConnectionStatusChanged(status: DroneManager.ConnectionStatus, message: String?) {
            if (status == DroneManager.ConnectionStatus.FAILED) listener?.onConnectionFailed(message ?: "连接失败")
        }
    }

    fun setConnectionStatusListener(value: ConnectionStatusListener?) {
        manager.removeStateListener(bridge)
        listener = value
        if (value != null) manager.addStateListener(bridge)
    }
    fun isConnected(): Boolean = manager.isConnected()
    fun getConnectedProductName(): String = manager.getConnectedDevice()?.productType ?: "未连接"
    fun isSDKInitialized(): Boolean = manager.isRegistered()
    fun initialize() { if (listener != null) manager.addStateListener(bridge) }
    fun getSDKVersion(): String = "5.18.0${if (manager.isSimulatorEnabled()) "（本地演示）" else ""}"
}
