package com.example.msdksample.kami.mission

import android.content.Context
import com.example.msdksample.drone.DroneManager

/** Compatibility facade. Application and route planning share a single SDK lifecycle. */
object DjiSdkManager {
    private var shellFailure: String? = null

    val state: DjiSdkState get() {
        val manager = DroneManager.getInstance()
        return when (manager.getSdkState()) {
            DroneManager.SdkState.NOT_INITIALIZED -> shellFailure?.let { DjiSdkState.Unavailable(it) } ?: DjiSdkState.NotStarted
            DroneManager.SdkState.INITIALIZING, DroneManager.SdkState.REGISTERING ->
                DjiSdkState.Initializing(manager.getSdkMessage() ?: "正在初始化 DJI SDK")
            DroneManager.SdkState.REGISTER_FAILED -> DjiSdkState.Failed(manager.getSdkMessage() ?: "SDK 注册失败")
            DroneManager.SdkState.REGISTERED -> if (manager.getRuntimeMode() == DroneManager.RuntimeMode.REAL) {
                DjiSdkState.ProductConnected(manager.getConnectedDevice()?.productId?.toString().orEmpty())
            } else DjiSdkState.Registered
        }
    }

    fun initialize(context: Context) { DroneManager.getInstance().initSDK(context) }
    fun isRegistered(): Boolean = DroneManager.getInstance().isRegistered()
    fun getSdkManagerInstance(): Any? = if (!isRegistered()) null else runCatching {
        Class.forName("dji.v5.manager.SDKManager").getMethod("getInstance").invoke(null)
    }.getOrNull()

    fun markShellUnavailable(error: Throwable) { shellFailure = "DJI 运行壳安装失败: ${error.readableMessage()}" }
}

sealed class DjiSdkState {
    data object NotStarted : DjiSdkState()
    data class Initializing(val message: String) : DjiSdkState()
    data object Registered : DjiSdkState()
    data class ProductConnected(val productId: String) : DjiSdkState()
    data class Failed(val reason: String) : DjiSdkState()
    data class Unavailable(val reason: String) : DjiSdkState()

    fun userMessage(): String = when (this) {
        NotStarted -> "DJI V5 SDK 尚未初始化"
        is Initializing -> message
        Registered -> "DJI V5 SDK 已注册，等待飞行器连接"
        is ProductConnected -> "DJI 飞行器已连接: $productId"
        is Failed -> reason
        is Unavailable -> reason
    }
}

internal fun Throwable.readableMessage(): String = cause?.message ?: message ?: javaClass.simpleName

internal fun Array<out Any?>?.firstErrorMessage(): String {
    val error = this?.firstOrNull() ?: return "未知错误"
    val description = runCatching { error.javaClass.getMethod("description").invoke(error)?.toString() }.getOrNull()
    val code = runCatching { error.javaClass.getMethod("errorCode").invoke(error)?.toString() }.getOrNull()
    return listOfNotNull(code, description).ifEmpty { listOf(error.toString()) }.joinToString(" ")
}
