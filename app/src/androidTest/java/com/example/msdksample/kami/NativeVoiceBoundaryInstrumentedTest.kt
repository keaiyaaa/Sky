package com.example.msdksample.kami

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.aerial.VoskHelper
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证不兼容的语音原生库不会在离线导航过程中启动后台加载线程。 */
@RunWith(AndroidJUnit4::class)
class NativeVoiceBoundaryInstrumentedTest {
    /**
     * 仅在需要架构保护的设备执行，直接验证错误回调且无模型下载或初始化成功。
     * @return {Unit} 架构保护的真实回调通过后返回。
     */
    @Test fun unsupportedHostRejectsVoiceBeforeAnyDownload() {
        assumeTrue(Build.SUPPORTED_ABIS.firstOrNull() != "arm64-v8a")
        val callback = BoundaryCallback()
        VoskHelper.init(InstrumentationRegistry.getInstrumentation().targetContext, callback)
        assertTrue(callback.error?.contains("文字指令") == true)
        assertFalse(callback.nativeWorkStarted)
        assertFalse(VoskHelper.isModelReady())
    }

    /** 记录原生边界回调，不替代实际 VoskHelper 实现。 */
    private class BoundaryCallback : VoskHelper.VoskInitCallback {
        var error: String? = null
        var nativeWorkStarted = false
        /**
         * 标记下载已启动。
         * @return {Unit} 无返回值。
         */
        override fun onDownloadStart() { nativeWorkStarted = true }
        /**
         * 标记存在下载进度。
         * @param {Int} percent 下载百分比。
         * @return {Unit} 无返回值。
         */
        override fun onDownloadProgress(percent: Int) { nativeWorkStarted = true }
        /**
         * 标记下载已完成。
         * @return {Unit} 无返回值。
         */
        override fun onDownloadComplete() { nativeWorkStarted = true }
        /**
         * 标记模型正在解压。
         * @return {Unit} 无返回值。
         */
        override fun onExtracting() { nativeWorkStarted = true }
        /**
         * 标记原生初始化已发生。
         * @return {Unit} 无返回值。
         */
        override fun onInitialized() { nativeWorkStarted = true }
        /**
         * 记录真实不可用原因。
         * @param {String} message VoskHelper 返回的提示。
         * @return {Unit} 无返回值。
         */
        override fun onError(message: String) { error = message }
    }
}
