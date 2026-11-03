package com.example.msdksample.drone

/** DJI 原生运行库的主机架构边界；本地航线功能不依赖原生 SDK。 */
object DjiRuntimeSupport {
    /**
     * 只接受原生 ARM64 主机，不把 x86 模拟器提供的 ARM 转译能力当作兼容真机。
     * @param {List<String>} supportedAbis Android 按优先级提供的主机 ABI 列表。
     * @return {Boolean} 是否允许加载 DJI 加固原生库。
     */
    fun supportsNativeSdk(supportedAbis: List<String>): Boolean = supportedAbis.firstOrNull() == "arm64-v8a"
}
