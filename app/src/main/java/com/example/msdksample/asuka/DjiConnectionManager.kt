package com.example.msdksample.asuka

object DjiConnectionManager {

    interface ConnectionStatusListener {
        fun onConnected(productName: String)
        fun onDisconnected()
        fun onConnectionFailed(error: String)
        fun onSDKInitialized()
        fun onSDKInitFailed(error: String)
    }

    private var connectionStatusListener: ConnectionStatusListener? = null
    private var isSdkInitialized = false

    fun setConnectionStatusListener(listener: ConnectionStatusListener?) {
        connectionStatusListener = listener
    }

    fun isConnected(): Boolean {
        return false
    }

    fun getConnectedProductName(): String {
        return "未连接"
    }

    fun isSDKInitialized(): Boolean {
        return isSdkInitialized
    }

    fun initialize() {
        isSdkInitialized = true
        connectionStatusListener?.onSDKInitialized()
    }

    fun getSDKVersion(): String {
        return "5.0.0 (Local Mode)"
    }
}
