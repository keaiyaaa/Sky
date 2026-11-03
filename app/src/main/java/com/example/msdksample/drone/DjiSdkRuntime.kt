package com.example.msdksample.drone

import android.os.Build

/** 统一 DJI 原生运行边界，离线功能与真实 SDK 加载分离。 */
object DjiSdkRuntime {
    /** 当前主机是否满足可测试的原生 ARM64 规则，不接受 x86 的 ARM 转译。 */
    val supported: Boolean get() = DjiRuntimeSupport.supportsNativeSdk(Build.SUPPORTED_ABIS.toList())
    const val UNAVAILABLE_MESSAGE = "当前运行环境不加载 DJI 原生 SDK，可使用本地演示；真机联调需 ARM64 Android 设备"
}
