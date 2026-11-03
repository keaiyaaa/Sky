package com.example.msdksample.drone

import android.os.Build

/** Avoid loading the protected ARM SDK through an x86 native bridge. Local features remain usable. */
object DjiSdkRuntime {
    val supported: Boolean get() = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
    const val UNAVAILABLE_MESSAGE = "当前运行环境不加载 DJI 原生 SDK，可使用本地演示；真机联调需 ARM64 Android 设备"
}
