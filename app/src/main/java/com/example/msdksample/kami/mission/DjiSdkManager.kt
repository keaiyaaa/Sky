package com.example.msdksample.kami.mission

import android.content.Context
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object DjiSdkManager {
    private const val SDK_MANAGER_CLASS = "dji.v5.manager.SDKManager"
    private const val SDK_CALLBACK_CLASS = "dji.v5.manager.interfaces.SDKManagerCallback"
    private const val INITIALIZE_COMPLETE = "INITIALIZE_COMPLETE"

    @Volatile
    private var sdkManagerInstance: Any? = null

    @Volatile
    private var started = false

    @Volatile
    var state: DjiSdkState = DjiSdkState.NotStarted
        private set

    fun initialize(context: Context) {
        if (started) {
            return
        }
        started = true
        state = DjiSdkState.Initializing("正在初始化 DJI V5 SDK")

        runCatching {
            val sdkManagerClass = Class.forName(SDK_MANAGER_CLASS)
            val callbackClass = Class.forName(SDK_CALLBACK_CLASS)
            val manager = sdkManagerClass.getMethod("getInstance").invoke(null)
                ?: error("SDKManager.getInstance 返回空")
            sdkManagerInstance = manager

            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
                SdkCallbackHandler(manager)
            )
            sdkManagerClass
                .getMethod("init", Context::class.java, callbackClass)
                .invoke(manager, context.applicationContext, callback)
        }.onFailure { error ->
            state = DjiSdkState.Unavailable("DJI V5 SDK 初始化入口不可用: ${error.readableMessage()}")
        }
    }

    fun getSdkManagerInstance(): Any? = sdkManagerInstance

    fun isRegistered(): Boolean {
        val manager = sdkManagerInstance ?: return false
        return runCatching {
            manager.javaClass.getMethod("isRegistered").invoke(manager) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun markShellUnavailable(error: Throwable) {
        if (state == DjiSdkState.NotStarted) {
            state = DjiSdkState.Unavailable("DJI 运行壳安装失败: ${error.readableMessage()}")
        }
    }

    private class SdkCallbackHandler(
        private val manager: Any
    ) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "onRegisterSuccess" -> {
                    state = DjiSdkState.Registered
                }

                "onRegisterFailure" -> {
                    state = DjiSdkState.Failed("DJI SDK 注册失败: ${args.firstErrorMessage()}")
                }

                "onProductConnect" -> {
                    state = DjiSdkState.ProductConnected(args?.firstOrNull()?.toString().orEmpty())
                }

                "onProductDisconnect" -> {
                    state = if (isRegistered()) {
                        DjiSdkState.Registered
                    } else {
                        DjiSdkState.Initializing("飞行器已断开，等待 SDK 注册完成")
                    }
                }

                "onInitProcess" -> {
                    val eventName = args?.firstOrNull()?.toString().orEmpty()
                    state = DjiSdkState.Initializing("DJI 初始化进度: $eventName")
                    if (eventName == INITIALIZE_COMPLETE) {
                        runCatching {
                            manager.javaClass.getMethod("registerApp").invoke(manager)
                        }.onFailure { error ->
                            state = DjiSdkState.Failed("调用 DJI registerApp 失败: ${error.readableMessage()}")
                        }
                    }
                }
            }
            return null
        }
    }
}

sealed class DjiSdkState {
    data object NotStarted : DjiSdkState()
    data class Initializing(val message: String) : DjiSdkState()
    data object Registered : DjiSdkState()
    data class ProductConnected(val productId: String) : DjiSdkState()
    data class Failed(val reason: String) : DjiSdkState()
    data class Unavailable(val reason: String) : DjiSdkState()

    fun userMessage(): String {
        return when (this) {
            NotStarted -> "DJI V5 SDK 尚未初始化"
            is Initializing -> message
            Registered -> "DJI V5 SDK 已注册，等待飞行器连接"
            is ProductConnected -> "DJI 飞行器已连接: $productId"
            is Failed -> reason
            is Unavailable -> reason
        }
    }
}

internal fun Throwable.readableMessage(): String {
    return cause?.message ?: message ?: javaClass.simpleName
}

internal fun Array<out Any?>?.firstErrorMessage(): String {
    val error = this?.firstOrNull() ?: return "未知错误"
    val description = runCatching {
        error.javaClass.getMethod("description").invoke(error)?.toString()
    }.getOrNull()
    val code = runCatching {
        error.javaClass.getMethod("errorCode").invoke(error)?.toString()
    }.getOrNull()
    return listOfNotNull(code, description)
        .ifEmpty { listOf(error.toString()) }
        .joinToString(" ")
}
