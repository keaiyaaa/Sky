package com.example.msdksample.drone

import org.junit.Assert.*
import org.junit.Test

/** 主机架构和 ARM 转译混淆的回归测试。 */
class DjiRuntimeSupportTest {
    /**
     * 验证只有原生 ARM64 主机允许加载 DJI 加固库。
     * @return {Unit} 断言通过后返回。
     */
    @Test fun rejectsTranslatedArmOnX86() {
        assertFalse(DjiRuntimeSupport.supportsNativeSdk(listOf("x86_64", "arm64-v8a")))
        assertFalse(DjiRuntimeSupport.supportsNativeSdk(listOf("x86")))
        assertFalse(DjiRuntimeSupport.supportsNativeSdk(emptyList()))
        assertTrue(DjiRuntimeSupport.supportsNativeSdk(listOf("arm64-v8a", "armeabi-v7a")))
    }
}
